(ns app.datascript
  (:require
   [bball.schema :as schema]
   [app.db :as db]
   [bball.query :as query]
   [cljs.reader :as reader]
   [datascript.core :as d]
   [datascript.parser :as dp]
   [datascript.query :as dq]
   [reagent.core :as reagent]
   [clojure.set]
   [clojure.pprint :as pprint]
   [bball.game-utils :as game-utils]))


(def schema (schema/datomic->datascript-schema schema/schema))


(defn empty-db [] (d/empty-db schema))


(defn create-ratom-conn
  ([]
   (create-ratom-conn nil))
  ([schema]
   (reagent/atom (d/empty-db schema) :meta {:listeners (atom {})})))


(defonce conn (create-ratom-conn schema))


(def game-local-storage-key "current-game")


(defn game->local-storage
  [db g]
  (.setItem js/localStorage game-local-storage-key
            (game-utils/datascript-game->tx-map db g)))


(defn local-storage->game-tx-map
  []
  (some-> (.getItem js/localStorage game-local-storage-key)
          reader/read-string))


(defn clear-ls-game
  []
  (.removeItem js/localStorage game-local-storage-key))


(defn teams
  [db g]
  ((juxt :game/home-team :game/away-team) (d/pull db '[{:game/home-team [*]
                                                        :game/away-team [*]}] g)))


(defn ppp
  [db g]
  (d/q '[:find ?t (sum ?pts) (count-distinct ?p)
         :in $ % ?g
         :with ?a
         :where
         (actions ?g ?t ?p ?a)
         (pts ?a ?pts)]
       db query/rules g))


(defn score
  [db g]
  (d/q '[:find ?t (sum ?pts)
         :in $ % ?g
         :with ?a
         :where
         (actions ?g ?t ?p ?a)
         (pts ?a ?pts)]
       db query/rules g))


(defn possessions
  [db g]
  (d/q '[:find [(pull ?p [* {:possession/team [*]}]) ...]
         :in $ ?g
         :where
         [?g :game/possession ?p]]
       db g))


(defn possessions?
  [db g]
  (not (empty? (d/q '[:find ?g
                      :in $ ?g
                      :where
                      [?g :game/possession]]
                    db g))))


(defn last-possession
  [db g]
  (apply max-key :possession/order (possessions db g)))


(defn last-action
  [possession]
  (apply max-key :action/order (:possession/action possession)))


(defn last-action-db
  [db g]
  (last-action (last-possession db g)))


(defn efg
  [db g]
  (d/q '[:find ?t (avg ?efgs)
         :in $ % ?g
         :with ?a
         :where
         (actions ?g ?t ?p ?a)
         (fga? ?a)
         (efgs ?a ?efgs)]
       db query/rules g))


(defn off-reb-rate
  [db g]
  (d/q '[:find ?t (avg ?off-rebs)
         :in $ % ?g
         :with ?a
         :where
         (actions ?g ?t ?p ?a)
         (rebound? ?a)
         (off-rebs ?a ?off-rebs)]
       db query/rules g))


(defn turnover-rate
  [db g]
  (->> (d/q '[:find ?t (sum ?tos) (count-distinct ?p)
              :in $ % ?g
              :with ?a
              :where
              (actions ?g ?t ?p ?a)
              (tos ?a ?tos)]
            db query/rules g)
       (map (fn [[t tos nposs]]
              [t (/ tos nposs)]))))


(defn pps
  [db g]
  (d/q '[:find ?t (avg ?pts)
         :in $ % ?g
         :with ?a
         :where
         (actions ?g ?t ?p ?a)
         [?a :action/type :action.type/shot]
         (pts ?a ?pts)]
       db query/rules g))


(defn ft-rate
  [db g]
  (->> (d/q '[:find ?t (sum ?fts) (sum ?fgas)
              :in $ % ?g
              :with ?a
              :where
              (actions ?g ?t ?p ?a)
              (fts ?a ?fts)
              (fgas ?a ?fgas)]
            db query/rules g)
       (map (fn [[t fts fgas]]
              [t (/ fts fgas)]))))


(defn fts-per-shot
  [db g]
  (d/q '[:find ?t (avg ?fts)
         :in $ % ?g
         :with ?a
         :where
         (actions ?g ?t ?p ?a)
         [?a :action/type :action.type/shot]
         (fts ?a ?fts)
         (fgas ?a ?fgas)]
       db query/rules g))


(defn action-change-possession?
  [{:action/keys [type] off-reb? :rebound/off?}]
  (and (not off-reb?) (not= type :action.type/technical)))


(defn other-team
  [teams t]
  (->> teams
       (remove #(= t (:db/id %)))
       first))


(defn other-team-db
  [db g t]
  (other-team (teams db g) t))


(defn team-possession
  [db g]
  (let [{:possession/keys [team] :as last-possession} (last-possession db g)]
    (when-some [last-action (last-action last-possession)]
      (if (action-change-possession? last-action)
        (other-team-db db g (:db/id team))
        team))))


(defn append-action-tx-data
  [db g action players init]
  (let [last-possession (last-possession db g)
        last-action     (last-action last-possession)
        period          (if (some? init)
                          (get init :init/period 1)
                          (:possession/period last-possession))
        poss-change?    (if (some? init)
                          true
                          (action-change-possession? last-action))
        team-id         (if (some? init)
                          (get-in init [:init/team :db/id])
                          (let [last-team-id (get-in last-possession [:possession/team :db/id])]
                            (if poss-change?
                              (:db/id (other-team-db db g last-team-id))
                              last-team-id)))
        action          (cond-> action
                          true (assoc
                                :action/order (if poss-change? 1 (inc (get last-action :action/order 0)))
                                :offense/players (get-in players [team-id :on-court])
                                :defense/players (:on-court (val (first (dissoc players team-id)))))
                          (db/ft? action) (assoc
                                           :ft/offense (get-in players [team-id :on-court-ft])
                                           :ft/defense (:on-court-ft (val (first (dissoc players team-id))))))]
    [(if poss-change?
       {:db/id g
        :game/possession [{:possession/order (inc (get last-possession :possession/order 0))
                           :possession/team team-id
                           :possession/period period
                           :possession/action [action]}]}
       {:db/id (:db/id last-possession)
        :possession/action [action]})]))


(defn undo-last-action-tx-data
  [db g]
  (let [last-possession (last-possession db g)
        actions (:possession/action last-possession)
        one-action? (= 1 (count actions))
        entity (if one-action?
                 last-possession
                 (last-action last-possession))]
    (when-some [entid (:db/id entity)]
      [[:db/retractEntity entid]])))


#_{:clj-kondo/ignore [:datalog-syntax]}
(defn game->team-players-map
  [db g]
  (let [player-query '[:find [?t (distinct ?player)]
                       :in $ ?g ?t
                       :where
                       [?g :game/possession ?p]
                       [?p :possession/action ?a]
                       [?a :offense/players ?offense]
                       [?a :defense/players ?defense]
                       (or (and [?p :possession/team ?t]
                                [(ground ?offense) [?player ...]])
                           (and (not [?p :possession/team ?t])
                                [(ground ?defense) [?player ...]]))]
        team-query '[:find ?t .
                     :in $ ?g ?team-type
                     :where
                     [?g ?team-type ?t]]
        [home-t home-players] (d/q player-query db g (d/q team-query db g :game/home-team))
        [away-t away-players] (d/q player-query db g (d/q team-query db g :game/away-team))]
    {home-t home-players
     away-t away-players}))


(defn get-players-map
  [db g]
  (let [last-poss (last-possession db g)
        offense-team-id (get-in last-poss [:possession/team :db/id])
        defense-team-id (:db/id (other-team-db db g offense-team-id))
        last-action (last-action last-poss)
        offense (set (:offense/players last-action))
        defense (set (:defense/players last-action))
        all-players (game->team-players-map db g)
        on-court-players {offense-team-id offense
                          defense-team-id defense}]
    (merge-with
     (fn [player-set on-court-player-set]
       {:on-court on-court-player-set
        :on-bench (apply disj player-set on-court-player-set)})
     all-players on-court-players)))


(comment
  #_{:clj-kondo/ignore [:datalog-syntax]}
  (d/q '[:find [(avg ?pts) (count-distinct ?a)]
         :keys pps shots
         :in $ % ?t
         :where
         (actions ?g ?t ?p ?a)
         [?a :shot/value]
         (pts ?a ?pts)]
       @conn
       query/rules
       [:team/name "Blaine"])

  ;
  )


(comment
  (->> (d/q '[:find ?feet (avg ?pts) (count ?a)
              :keys feet pps shots
              :in $ % ?t
              :where
              (actions ?g ?t ?p ?a)
              [?a :shot/distance ?inches]
              [(quot ?inches 12) ?feet]
              (pts ?a ?pts)]
            @conn
            query/rules
            [:team/name "Blaine"])
       (filter #(>= (:shots %) 10))
       (sort-by :pps >))
  ;; => ({:feet 1, :pps 1.603305785123967, :shots 121}
  ;;     {:feet 8, :pps 1.3571428571428572, :shots 14}
  ;;     {:feet 25, :pps 1.3181818181818181, :shots 22}
  ;;     {:feet 2, :pps 1.2289156626506024, :shots 166}
  ;;     {:feet 21, :pps 1.199004975124378, :shots 201}
  ;;     {:feet 5, :pps 1.1219512195121952, :shots 82}
  ;;     {:feet 20, :pps 1.08, :shots 50}
  ;;     {:feet 10, :pps 1, :shots 10}
  ;;     {:feet 24, :pps 1, :shots 18}
  ;;     {:feet 4, :pps 0.9888888888888889, :shots 90}
  ;;     {:feet 22, :pps 0.9767441860465116, :shots 86}
  ;;     {:feet 3, :pps 0.9514563106796117, :shots 103}
  ;;     {:feet 26, :pps 0.8823529411764706, :shots 17}
  ;;     {:feet 23, :pps 0.8181818181818182, :shots 33}
  ;;     {:feet 6, :pps 0.6739130434782609, :shots 46}
  ;;     {:feet 7, :pps 0.65625, :shots 32})

  ;; => ({:feet 0, :pps 1.5555555555555556, :shots 9}
  ;;     {:feet 1, :pps 1.603305785123967, :shots 121}
  ;;     {:feet 2, :pps 1.2289156626506024, :shots 166}
  ;;     {:feet 3, :pps 0.9514563106796117, :shots 103}
  ;;     {:feet 4, :pps 0.9888888888888889, :shots 90}
  ;;     {:feet 5, :pps 1.1219512195121952, :shots 82}
  ;;     {:feet 6, :pps 0.6739130434782609, :shots 46}
  ;;     {:feet 7, :pps 0.65625, :shots 32}
  ;;     {:feet 8, :pps 1.3571428571428572, :shots 14}
  ;;     {:feet 9, :pps 0.8571428571428571, :shots 7}
  ;;     {:feet 10, :pps 1, :shots 10}
  ;;     {:feet 11, :pps 0.4, :shots 5}
  ;;     {:feet 12, :pps 1.5, :shots 4}
  ;;     {:feet 13, :pps 0.6666666666666666, :shots 6}
  ;;     {:feet 14, :pps 0.5714285714285714, :shots 7}
  ;;     {:feet 15, :pps 0.25, :shots 8}
  ;;     {:feet 16, :pps 0.8, :shots 5}
  ;;     {:feet 17, :pps 0, :shots 8}
  ;;     {:feet 18, :pps 1.2, :shots 5}
  ;;     {:feet 19, :pps 0.6666666666666666, :shots 3}
  ;;     {:feet 20, :pps 1.08, :shots 50}
  ;;     {:feet 21, :pps 1.199004975124378, :shots 201}
  ;;     {:feet 22, :pps 0.9767441860465116, :shots 86}
  ;;     {:feet 23, :pps 0.8181818181818182, :shots 33}
  ;;     {:feet 24, :pps 1, :shots 18}
  ;;     {:feet 25, :pps 1.3181818181818181, :shots 22}
  ;;     {:feet 26, :pps 0.8823529411764706, :shots 17}
  ;;     {:feet 27, :pps 0.5, :shots 6}
  ;;     {:feet 28, :pps 0, :shots 2})


  (let [total-result #_{:clj-kondo/ignore [:datalog-syntax]}
        (d/q '[:find [(avg ?pts) (count-distinct ?a)]
               :keys pps shots
               :in $ % ?t
               :where
               (actions ?g ?t ?p ?a)
               [?a :shot/value]
               (pts ?a ?pts)]
             @conn
             query/rules
             [:team/name "Blaine"])]
    (->> (d/q '[:find ?numbers ?sector (avg ?pts) (count ?a)
                :keys player sector pps shots
                :in $ % ?t [?numbers ...]
                :where
                (actions ?g ?t ?p ?a)
                [?a :action/player ?player]
                [(contains? ?numbers ?player)]
                [?a :shot/distance ?inches]
                [?a :shot/value ?value]
                (sector ?value ?inches ?sector)
                (pts ?a ?pts)]
              @conn
              query/rules
              [:team/name "Blaine"]
              [#{1} #{2 5} #{3} #{4} #{10} #{20} #{30} #{35} #{42}])
         (filter #(>= (:shots %) 10))
         ((fn [results] (conj results total-result)))
         (sort-by :pps >)))
  ;; => ({:player #{3}, :sector "0-4", :pps 1.4666666666666666, :shots 75}
  ;;     {:player #{1}, :sector "0-4", :pps 1.4352941176470588, :shots 85}
  ;;     {:player #{35}, :sector "0-4", :pps 1.3191489361702127, :shots 47}
  ;;     {:player #{1}, :sector "3P", :pps 1.26, :shots 100}
  ;;     {:player #{2 5}, :sector "0-4", :pps 1.2571428571428571, :shots 35}
  ;;     {:player #{4}, :sector "0-4", :pps 1.2222222222222223, :shots 27}
  ;;     {:player #{42}, :sector "0-4", :pps 1.1717171717171717, :shots 99}
  ;;     {:player #{35}, :sector "3P", :pps 1.1636363636363636, :shots 110}
  ;;     {:player #{3}, :sector "4-10", :pps 1.1031746031746033, :shots 126}
  ;;     {:pps 1.0969125214408233, :shots 1166}
  ;;     {:player #{42}, :sector "3P", :pps 1.0188679245283019, :shots 53}
  ;;     {:player #{4}, :sector "3P", :pps 1, :shots 49}
  ;;     {:player #{2 5}, :sector "3P", :pps 0.95, :shots 60}
  ;;     {:player #{3}, :sector "3P", :pps 0.9444444444444444, :shots 54}
  ;;     {:player #{35}, :sector "4-10", :pps 0.9166666666666666, :shots 36}
  ;;     {:player #{1}, :sector "4-10", :pps 0.8979591836734694, :shots 49}
  ;;     {:player #{4}, :sector "4-10", :pps 0.85, :shots 20}
  ;;     {:player #{42}, :sector "4-10", :pps 0.7692307692307693, :shots 13}
  ;;     {:player #{20}, :sector "0-4", :pps 0.7307692307692307, :shots 26}
  ;;     {:player #{1}, :sector "10-3P", :pps 0.5833333333333334, :shots 24}
  ;;     {:player #{35}, :sector "10-3P", :pps 0.5833333333333334, :shots 24}
  ;;     {:player #{2 5}, :sector "4-10", :pps 0.5, :shots 14})

  ;; (all conference and non-dome games)
  ;; => ({:player #{3}, :sector "0-4", :pps 1.4666666666666666, :shots 75}
  ;;     {:player #{1}, :sector "0-4", :pps 1.4352941176470588, :shots 85}
  ;;     {:player #{35}, :sector "3P-24", :pps 1.3529411764705883, :shots 51}
  ;;     {:player #{35}, :sector "0-4", :pps 1.3191489361702127, :shots 47}
  ;;     {:player #{1}, :sector "3P-24", :pps 1.263157894736842, :shots 95}
  ;;     {:player #{2 5}, :sector "0-4", :pps 1.2571428571428571, :shots 35}
  ;;     {:player #{4}, :sector "0-4", :pps 1.2222222222222223, :shots 27}
  ;;     {:player #{42}, :sector "0-4", :pps 1.1717171717171717, :shots 99}
  ;;     {:player #{3}, :sector "4-10", :pps 1.1031746031746033, :shots 126}
  ;;     {:pps 1.0969125214408233, :shots 1166}
  ;;     {:player #{42}, :sector "3P-24", :pps 1.0188679245283019, :shots 53}
  ;;     {:player #{4}, :sector "3P-24", :pps 1, :shots 49}
  ;;     {:player #{35}, :sector "24+", :pps 1, :shots 59}
  ;;     {:player #{3}, :sector "3P-24", :pps 0.9622641509433962, :shots 53}
  ;;     {:player #{2 5}, :sector "3P-24", :pps 0.95, :shots 60}
  ;;     {:player #{35}, :sector "4-10", :pps 0.9166666666666666, :shots 36}
  ;;     {:player #{1}, :sector "4-10", :pps 0.8979591836734694, :shots 49}
  ;;     {:player #{4}, :sector "4-10", :pps 0.85, :shots 20}
  ;;     {:player #{42}, :sector "4-10", :pps 0.7692307692307693, :shots 13}
  ;;     {:player #{20}, :sector "0-4", :pps 0.7307692307692307, :shots 26}
  ;;     {:player #{1}, :sector "10-3P", :pps 0.5833333333333334, :shots 24}
  ;;     {:player #{35}, :sector "10-3P", :pps 0.5833333333333334, :shots 24}
  ;;     {:player #{2 5}, :sector "4-10", :pps 0.5, :shots 14})

  ;; (all conference and non-dome games)
  ;; => ({:player #{1}, :sector "0-3", :pps 1.5614035087719298, :shots 57}
  ;;     {:player #{3}, :sector "0-3", :pps 1.5098039215686274, :shots 51}
  ;;     {:player #{4}, :sector "0-3", :pps 1.5, :shots 18}
  ;;     {:player #{35}, :sector "0-3", :pps 1.4411764705882353, :shots 34}
  ;;     {:player #{2 5}, :sector "0-3", :pps 1.3793103448275863, :shots 29}
  ;;     {:player #{35}, :sector "3P-24", :pps 1.3529411764705883, :shots 51}
  ;;     {:player #{42}, :sector "0-3", :pps 1.2790697674418605, :shots 86}
  ;;     {:player #{1}, :sector "3P-24", :pps 1.263157894736842, :shots 95}
  ;;     {:player #{3}, :sector "3-10", :pps 1.1466666666666667, :shots 150}
  ;;     {:pps 1.0969125214408233, :shots 1166}
  ;;     {:player #{42}, :sector "3P-24", :pps 1.0188679245283019, :shots 53}
  ;;     {:player #{4}, :sector "3P-24", :pps 1, :shots 49}
  ;;     {:player #{1}, :sector "3-10", :pps 1, :shots 77}
  ;;     {:player #{35}, :sector "24+", :pps 1, :shots 59}
  ;;     {:player #{3}, :sector "3P-24", :pps 0.9622641509433962, :shots 53}
  ;;     {:player #{2 5}, :sector "3P-24", :pps 0.95, :shots 60}
  ;;     {:player #{20}, :sector "0-3", :pps 0.9473684210526315, :shots 19}
  ;;     {:player #{35}, :sector "3-10", :pps 0.9387755102040817, :shots 49}
  ;;     {:player #{4}, :sector "3-10", :pps 0.7931034482758621, :shots 29}
  ;;     {:player #{42}, :sector "3-10", :pps 0.6153846153846154, :shots 26}
  ;;     {:player #{1}, :sector "10-3P", :pps 0.5833333333333334, :shots 24}
  ;;     {:player #{35}, :sector "10-3P", :pps 0.5833333333333334, :shots 24}
  ;;     {:player #{2 5}, :sector "3-10", :pps 0.55, :shots 20}
  ;;     {:player #{20}, :sector "3-10", :pps 0.3125, :shots 16})

  (let [total-result #_{:clj-kondo/ignore [:datalog-syntax]}
        (d/q '[:find [(avg ?pts) (count-distinct ?a)]
               :keys pps shots
               :in $ % ?blaine
               :where
               (actions ?g ?t ?p ?a)
               (not [?p :possession/team ?blaine])
               [?a :shot/value]
               (pts ?a ?pts)]
             @conn
             query/rules
             [:team/name "Blaine"])]
    (->> (d/q '[:find ?sector (avg ?pts) (count ?a)
                :keys sector pps shots
                :in $ % ?blaine
                :where
                (actions ?g ?t ?p ?a)
                (not [?p :possession/team ?blaine])
                [?a :shot/distance ?inches]
                [?a :shot/value ?value]
                (sector ?value ?inches ?sector)
                (pts ?a ?pts)]
              @conn
              query/rules
              [:team/name "Blaine"])
         ((fn [results] (conj results total-result)))
         (sort-by :pps >)))
  ;; => ({:sector "0-4", :pps 1.2692307692307692, :shots 442}
  ;;     {:pps 0.9983108108108109, :shots 1184}
  ;;     {:sector "3P", :pps 0.9175531914893617, :shots 376}
  ;;     {:sector "4-10", :pps 0.7954545454545454, :shots 264}
  ;;     {:sector "10-3P", :pps 0.6470588235294118, :shots 102})

  ;; all shots by non-Blaine players
  ;; => ({:sector "0-4", :pps 1.2692307692307692, :shots 442}
  ;;     {:sector "24+", :pps 1.065217391304348, :shots 92}
  ;;     {:pps 0.9983108108108109, :shots 1184}
  ;;     {:sector "3P-24", :pps 0.8697183098591549, :shots 284}
  ;;     {:sector "4-10", :pps 0.7954545454545454, :shots 264}
  ;;     {:sector "10-3P", :pps 0.6470588235294118, :shots 102})

  ;; all shots by non-Blaine players
  ;; => ({:sector "0-3", :pps 1.3168044077134986, :shots 363}
  ;;     {:sector "24+", :pps 1.065217391304348, :shots 92}
  ;;     {:pps 0.9983108108108109, :shots 1184}
  ;;     {:sector "3P-24", :pps 0.8697183098591549, :shots 284}
  ;;     {:sector "3-10", :pps 0.8542274052478134, :shots 343}
  ;;     {:sector "10-3P", :pps 0.6470588235294118, :shots 102}) 
  )

(comment

  (def offrtg-results
    (->> (d/q '[:find ?player (sum ?pts) (count-distinct ?p)
                :keys player total-pts possessions
                :in $ % ?t [?player ...]
                :with ?a
                :where
                (actions ?g ?t ?p ?a)
                [?p :possession/team ?t]
                [?a :offense/players ?offense]
                [(set ?offense) ?offense-set]
                [(contains? ?offense-set ?player)]
                (pts ?a ?pts)]
              @conn
              query/rules
              [:team/name "Blaine"]
              (apply concat [#{1} #{2 5} #{3} #{4} #{10} #{20} #{30} #{35} #{42}]))
         ((fn [results]
            (let [player-2 (first (filter #(= 2 (:player %)) results))
                  player-5 (first (filter #(= 5 (:player %)) results))]
              (conj results (-> (merge-with + player-2 player-5)
                                (assoc :player #{2 5}))))))
         (remove (fn [{:keys [player]}]
                   (or (= player 2) (= player 5))))
         (map (fn [{:keys [total-pts possessions] :as result}]
                (assoc result :offrtg (-> total-pts
                                          (/ possessions)
                                          (* 100)))))
         (sort-by :offrtg >)))
;; => ({:player 3, :total-pts 907, :possessions 822, :offrtg 110.34063260340632}
;;     {:player 42, :total-pts 715, :possessions 650, :offrtg 110.00000000000001}
;;     {:player 4, :total-pts 644, :possessions 586, :offrtg 109.8976109215017}
;;     {:player 1, :total-pts 959, :possessions 874, :offrtg 109.7254004576659}
;;     {:player 35, :total-pts 866, :possessions 794, :offrtg 109.06801007556675}
;;     {:player #{2 5}, :total-pts 643, :possessions 603, :offrtg 106.6334991708126}
;;     {:player 20, :total-pts 226, :possessions 218, :offrtg 103.6697247706422}
;;     {:player 10, :total-pts 73, :possessions 82, :offrtg 89.02439024390245}
;;     {:player 30, :total-pts 41, :possessions 60, :offrtg 68.33333333333333})


  (def defrtg-results
    (->> (d/q '[:find ?player (sum ?pts) (count-distinct ?p)
                :keys player total-pts possessions
                :in $ % ?t [?player ...]
                :with ?a
                :where
                (or [?g :game/home-team ?t]
                    [?g :game/away-team ?t])
                (actions ?g ?ot ?p ?a)
                (not [?p :possession/team ?t])
                [?a :defense/players ?defense]
                [(set ?defense) ?defense-set]
                [(contains? ?defense-set ?player)]
                (pts ?a ?pts)]
              @conn
              query/rules
              [:team/name "Blaine"]
              (apply concat [#{1} #{2 5} #{3} #{4} #{10} #{20} #{30} #{35} #{42}]))
         ((fn [results]
            (let [player-2 (first (filter #(= 2 (:player %)) results))
                  player-5 (first (filter #(= 5 (:player %)) results))]
              (conj results (-> (merge-with + player-2 player-5)
                                (assoc :player #{2 5}))))))
         (remove (fn [{:keys [player]}]
                   (or (= player 2) (= player 5))))
         (map (fn [{:keys [total-pts possessions] :as result}]
                (assoc result :defrtg (-> total-pts
                                          (/ possessions)
                                          (* 100)))))
         (sort-by :defrtg)))
;; => ({:player 30, :total-pts 57, :possessions 60, :defrtg 95}
;;     {:player 4, :total-pts 562, :possessions 588, :defrtg 95.578231292517}
;;     {:player #{2 5}, :total-pts 584, :possessions 611, :defrtg 95.5810147299509}
;;     {:player 3, :total-pts 810, :possessions 816, :defrtg 99.26470588235294}
;;     {:player 42, :total-pts 647, :possessions 650, :defrtg 99.53846153846155}
;;     {:player 35, :total-pts 783, :possessions 786, :defrtg 99.61832061068702}
;;     {:player 1, :total-pts 867, :possessions 867, :defrtg 100}
;;     {:player 10, :total-pts 89, :possessions 89, :defrtg 100}
;;     {:player 20, :total-pts 242, :possessions 231, :defrtg 104.76190476190477})


  (->> (for [offrtg-result offrtg-results
             defrtg-result defrtg-results
             :when (= (:player offrtg-result) (:player defrtg-result))]
         {:player (:player offrtg-result)
          :possessions (apply + (map :possessions [offrtg-result defrtg-result]))
          :netrtg (- (:offrtg offrtg-result) (:defrtg defrtg-result))})
       (sort-by :netrtg >))
;; => ({:player 4, :possessions 1174, :netrtg 14.319379628984692} ;; doesn't include suspended games, obvi
;;     {:player 3, :possessions 1638, :netrtg 11.075926721053378}
;;     {:player #{2 5}, :possessions 1214, :netrtg 11.052484440861704}
;;     {:player 42, :possessions 1300, :netrtg 10.461538461538467}
;;     {:player 1, :possessions 1741, :netrtg 9.725400457665899}
;;     {:player 35, :possessions 1580, :netrtg 9.449689464879725}
;;     {:player 20, :possessions 449, :netrtg -1.0921799912625687}
;;     {:player 10, :possessions 171, :netrtg -10.975609756097555}
;;     {:player 30, :possessions 120, :netrtg -26.66666666666667})
  )

(comment
  (defn off-reb-rate
    [db g]
    (d/q '[:find ?t (avg ?off-rebs)
           :in $ %
           :with ?a
           :where
           (actions ?g ?t ?p ?a)
           (rebound? ?a)
           (off-rebs ?a ?off-rebs)]
         db query/rules g))

  (->> (d/q '[:find
              (pull ?t [:team/name])
              (avg ?off-rebs)
              :keys team off-reb-rate
              :in $ %
              :with ?a
              :where
              (actions ?g ?t ?p ?a)
              (rebound? ?a)
              (off-rebs ?a ?off-rebs)]
            @conn query/rules)
       (map (fn [result] (-> result
                             (update :team :team/name)
                             (assoc :offreb% (* 100 (:off-reb-rate result)))
                             (dissoc :off-reb-rate))))
       (sort-by :offreb% >))
  ;; => ({:team "Sedro-Woolley", :offreb% 50}
  ;;     {:team "Lynden", :offreb% 45.83333333333333}
  ;;     {:team "Sehome", :offreb% 43.333333333333336}
  ;;     {:team "Burlington-Edison", :offreb% 42.857142857142854}
  ;;     {:team "Northwest", :offreb% 38.46153846153847}
  ;;     {:team "Ferndale", :offreb% 38.23529411764706}
  ;;     {:team "Anacortes", :offreb% 37.5}
  ;;     {:team "Nooksack Valley", :offreb% 36.84210526315789}
  ;;     {:team "Blaine", :offreb% 33.0188679245283}
  ;;     {:team "Mount Vernon", :offreb% 32.432432432432435}
  ;;     {:team "Meridian", :offreb% 28.57142857142857}
  ;;     {:team "Mount Baker", :offreb% 28.57142857142857}
  ;;     {:team "Bellingham", :offreb% 26.190476190476193}
  ;;     {:team "Lynden Christian", :offreb% 25.423728813559322}
  ;;     {:team "Zillah", :offreb% 25}
  ;;     {:team "Squalicum", :offreb% 24}
  ;;     {:team "Oak Harbor", :offreb% 18.91891891891892}
  ;;     {:team "Lakewood", :offreb% 3.3333333333333335}) 

  ;; Blaine was 10-10 on winning offensive rebounding
  (->> (d/q '[:find
              ?g
              (pull ?t [:team/name])
              (avg ?off-rebs)
              :keys g team off-reb-rate
              :in $ %
              :with ?a
              :where
              (actions ?g ?t ?p ?a)
              (rebound? ?a)
              (off-rebs ?a ?off-rebs)]
            @conn query/rules)
       (map (fn [result] (-> result
                             (update :team :team/name)
                             (assoc :offreb% (* 100 (:off-reb-rate result)))
                             (dissoc :off-reb-rate))))
       (group-by :g)
       vals
       (map (fn [teams] (->> teams
                             (map #(dissoc % :g))
                             (sort-by :offreb% >))))
       (sort-by (fn [[winner loser]] (= "Blaine" (:team winner)))))
  ;; => (({:team "Sehome", :offreb% 43.333333333333336} {:team "Blaine", :offreb% 12.5})
  ;;     ({:team "Northwest", :offreb% 38.46153846153847} {:team "Blaine", :offreb% 23.076923076923077})
  ;;     ({:team "Lynden Christian", :offreb% 29.03225806451613} {:team "Blaine", :offreb% 27.906976744186046})
  ;;     ({:team "Sedro-Woolley", :offreb% 50} {:team "Blaine", :offreb% 35.13513513513514})
  ;;     ({:team "Lynden", :offreb% 45.83333333333333} {:team "Blaine", :offreb% 25.806451612903224})
  ;;     ({:team "Anacortes", :offreb% 37.5} {:team "Blaine", :offreb% 17.24137931034483})
  ;;     ({:team "Burlington-Edison", :offreb% 42.857142857142854} {:team "Blaine", :offreb% 36.666666666666664})
  ;;     ({:team "Nooksack Valley", :offreb% 38.88888888888889} {:team "Blaine", :offreb% 23.52941176470588})
  ;;     ({:team "Zillah", :offreb% 25} {:team "Blaine", :offreb% 22.22222222222222})
  ;;     ({:team "Mount Vernon", :offreb% 32.432432432432435} {:team "Blaine", :offreb% 21.21212121212121})
  ;;     ({:team "Blaine", :offreb% 32.35294117647059} {:team "Oak Harbor", :offreb% 18.91891891891892})
  ;;     ({:team "Blaine", :offreb% 36} {:team "Lynden Christian", :offreb% 21.428571428571427})
  ;;     ({:team "Blaine", :offreb% 42.10526315789473} {:team "Bellingham", :offreb% 26.190476190476193})
  ;;     ({:team "Blaine", :offreb% 34.78260869565217} {:team "Meridian", :offreb% 25})
  ;;     ({:team "Blaine", :offreb% 40} {:team "Meridian", :offreb% 32.142857142857146})
  ;;     ({:team "Blaine", :offreb% 36.11111111111111} {:team "Nooksack Valley", :offreb% 35})
  ;;     ({:team "Blaine", :offreb% 48.275862068965516} {:team "Ferndale", :offreb% 38.23529411764706})
  ;;     ({:team "Blaine", :offreb% 60} {:team "Mount Baker", :offreb% 28.57142857142857})
  ;;     ({:team "Blaine", :offreb% 44.11764705882353} {:team "Lakewood", :offreb% 3.3333333333333335})
  ;;     ({:team "Blaine", :offreb% 36} {:team "Squalicum", :offreb% 24}))

  ;
  )

(comment
  (->> (d/q '[:find ?offense-set (sum ?pts) (count-distinct ?p)
              :keys offense total-pts possessions
              :in $ % ?t
              :with ?a
              :where
              (actions ?g ?t ?p ?a)
              [?a :offense/players ?offense]
              [(set ?offense) ?offense-set]
              (pts ?a ?pts)]
            @conn
            query/rules
            [:team/name "Blaine"])
       (map (fn [result]
              (update result :offense
                      (fn [players]
                        (->> players
                             (map (fn [player]
                                    (if (contains? #{2 5} player)
                                      #{2 5}
                                      player)))
                             set)))))
       (group-by :offense)
       ((fn [results]
          (update-vals results
                       (fn [results]
                         (apply merge-with + (map #(dissoc % :offense) results))))))
       (map (fn [[offense result]]
              (assoc result :offense offense)))
       (map (fn [{:keys [total-pts possessions] :as result}]
              (assoc result :offrtg (-> total-pts
                                        (/ possessions)
                                        (* 100)))))
       (filter (fn [{:keys [possessions]}]
                 (> possessions 20)))
       ((juxt #(sort-by :offrtg > %) #(sort-by :possessions > %))))
  ;; => [({:total-pts 244, :possessions 190, :offense #{1 4 3 35 #{2 5}}, :offrtg 128.42105263157896}
  ;;      {:total-pts 33, :possessions 26, :offense #{20 1 3 35 42}, :offrtg 126.92307692307692}
  ;;      {:total-pts 24, :possessions 21, :offense #{4 3 35 #{2 5} 42}, :offrtg 114.28571428571428}
  ;;      {:total-pts 60, :possessions 56, :offense #{20 1 3 35 #{2 5}}, :offrtg 107.14285714285714}
  ;;      {:total-pts 430, :possessions 402, :offense #{1 4 3 35 42}, :offrtg 106.96517412935323}
  ;;      {:total-pts 104, :possessions 103, :offense #{1 4 3 #{2 5} 42}, :offrtg 100.97087378640776}
  ;;      {:total-pts 188, :possessions 189, :offense #{1 3 35 #{2 5} 42}, :offrtg 99.47089947089947}
  ;;      {:total-pts 34, :possessions 35, :offense #{1 4 35 #{2 5} 42}, :offrtg 97.14285714285714}
  ;;      {:total-pts 22, :possessions 29, :offense #{20 1 4 3 #{2 5}}, :offrtg 75.86206896551724}
  ;;      {:total-pts 10, :possessions 22, :offense #{20 1 4 3 35}, :offrtg 45.45454545454545})

  ;;     ({:total-pts 430, :possessions 402, :offense #{1 4 3 35 42}, :offrtg 106.96517412935323}
  ;;      {:total-pts 244, :possessions 190, :offense #{1 4 3 35 #{2 5}}, :offrtg 128.42105263157896}
  ;;      {:total-pts 188, :possessions 189, :offense #{1 3 35 #{2 5} 42}, :offrtg 99.47089947089947}
  ;;      {:total-pts 104, :possessions 103, :offense #{1 4 3 #{2 5} 42}, :offrtg 100.97087378640776}
  ;;      {:total-pts 60, :possessions 56, :offense #{20 1 3 35 #{2 5}}, :offrtg 107.14285714285714}
  ;;      {:total-pts 34, :possessions 35, :offense #{1 4 35 #{2 5} 42}, :offrtg 97.14285714285714}
  ;;      {:total-pts 22, :possessions 29, :offense #{20 1 4 3 #{2 5}}, :offrtg 75.86206896551724}
  ;;      {:total-pts 33, :possessions 26, :offense #{20 1 3 35 42}, :offrtg 126.92307692307692}
  ;;      {:total-pts 10, :possessions 22, :offense #{20 1 4 3 35}, :offrtg 45.45454545454545}
  ;;      {:total-pts 24, :possessions 21, :offense #{4 3 35 #{2 5} 42}, :offrtg 114.28571428571428})]


  (->> (d/q '[:find ?defense-set (sum ?pts) (count-distinct ?p)
              :keys defense total-pts possessions
              :in $ % ?t
              :with ?a
              :where
              (actions ?g ?ot ?p ?a)
              (or [?g :game/home-team ?t]
                  [?g :game/away-team ?t])
              (not [?p :possession/team ?t])
              [?a :defense/players ?defense]
              [(set ?defense) ?defense-set]
              (pts ?a ?pts)]
            @conn
            query/rules
            [:team/name "Blaine"])
       (map (fn [result]
              (update result :defense
                      (fn [players]
                        (->> players
                             (map (fn [player]
                                    (if (contains? #{2 5} player)
                                      #{2 5}
                                      player)))
                             set)))))
       (group-by :defense)
       ((fn [results]
          (update-vals results
                       (fn [results]
                         (apply merge-with + (map #(dissoc % :defense) results))))))
       (map (fn [[defense result]]
              (assoc result :defense defense)))
       (map (fn [{:keys [total-pts possessions] :as result}]
              (assoc result :defrtg (-> total-pts
                                        (/ possessions)
                                        (* 100)))))
       (filter (fn [{:keys [possessions]}]
                 (> possessions 20)))
       ((juxt #(sort-by :defrtg < %) #(sort-by :possessions > %))))
  ;; => [({:total-pts 16, :possessions 23, :defense #{4 3 35 #{2 5} 42}, :defrtg 69.56521739130434}
  ;;      {:total-pts 20, :possessions 25, :defense #{20 1 4 3 35}, :defrtg 80}
  ;;      {:total-pts 26, :possessions 31, :defense #{20 1 4 3 #{2 5}}, :defrtg 83.87096774193549}
  ;;      {:total-pts 98, :possessions 112, :defense #{1 4 3 #{2 5} 42}, :defrtg 87.5}
  ;;      {:total-pts 36, :possessions 41, :defense #{1 4 35 #{2 5} 42}, :defrtg 87.8048780487805}
  ;;      {:total-pts 49, :possessions 55, :defense #{20 1 3 35 #{2 5}}, :defrtg 89.0909090909091}
  ;;      {:total-pts 172, :possessions 186, :defense #{1 4 3 35 #{2 5}}, :defrtg 92.47311827956989}
  ;;      {:total-pts 176, :possessions 184, :defense #{1 3 35 #{2 5} 42}, :defrtg 95.65217391304348}
  ;;      {:total-pts 380, :possessions 390, :defense #{1 4 3 35 42}, :defrtg 97.43589743589743}
  ;;      {:total-pts 31, :possessions 27, :defense #{20 1 3 35 42}, :defrtg 114.81481481481481})

  ;;     ({:total-pts 380, :possessions 390, :defense #{1 4 3 35 42}, :defrtg 97.43589743589743}
  ;;      {:total-pts 172, :possessions 186, :defense #{1 4 3 35 #{2 5}}, :defrtg 92.47311827956989}
  ;;      {:total-pts 176, :possessions 184, :defense #{1 3 35 #{2 5} 42}, :defrtg 95.65217391304348}
  ;;      {:total-pts 98, :possessions 112, :defense #{1 4 3 #{2 5} 42}, :defrtg 87.5}
  ;;      {:total-pts 49, :possessions 55, :defense #{20 1 3 35 #{2 5}}, :defrtg 89.0909090909091}
  ;;      {:total-pts 36, :possessions 41, :defense #{1 4 35 #{2 5} 42}, :defrtg 87.8048780487805}
  ;;      {:total-pts 26, :possessions 31, :defense #{20 1 4 3 #{2 5}}, :defrtg 83.87096774193549}
  ;;      {:total-pts 31, :possessions 27, :defense #{20 1 3 35 42}, :defrtg 114.81481481481481}
  ;;      {:total-pts 20, :possessions 25, :defense #{20 1 4 3 35}, :defrtg 80}
  ;;      {:total-pts 16, :possessions 23, :defense #{4 3 35 #{2 5} 42}, :defrtg 69.56521739130434})]
  )


(defn save-file
  ([file]
   (save-file "download.txt" file))
  ([filename file]
   (let [url (.createObjectURL js/URL (js/File. [file] filename))]
     (doto (.createElement js/document "a")
       (.setAttribute "download" filename)
       (.setAttribute "href" url)
       (.click))
     (.revokeObjectURL js/URL url))))

(comment
  (let [filename "2022-12-06-Blaine-Bellingham.edn"
        game-id (get @re-frame.db/app-db :game-id)
        game-map (game-utils/datascript-game->tx-map @conn game-id)]
    (save-file filename
               (with-out-str (pprint/pprint game-map))))

  (clear-ls-game)
  ;
  )

(comment

  (->> (d/q '[:find ?e ?v
              :in $
              :where
              [?e :possession/action ?v]
              [?x :nonsense ?y]] @conn)
       ((juxt count identity))
       first)
  ;; => 0

  (->> (d/q '[:find ?a ?pts
              :in $ % #_[?g ...] #_[?t ...] [?player ...] #_?offense-subset subset?
              :where
              [?a :action/player ?player]
              [?a :offense/players ?offense-tuple]
              [(set ?offense-tuple) ?offense]
              [(subset? ?offense-subset ?offense)] ;; here, ?offense-subset is not bound

              (actions ?g ?t ?p ?a)
              (pts ?a ?pts)] @conn query/rules
            #{1 3} clojure.set/subset?)
       ((juxt count identity))
       first)


  (dp/parse-rule '[(pts ?a ?pts)
                   [?a :ft/made ?ft-pts]
                   [(+ ?ft-pts 2) ?pts]])
  ;; {:name #datascript.parser.PlainSymbol{:symbol pts}
  ;;  :vars #datascript.parser.RuleVars{:required nil
  ;;                                    :free [#datascript.parser.Variable{:symbol ?a}
  ;;                                           #datascript.parser.Variable{:symbol ?pts}]}
  ;;  :clauses [#datascript.parser.Pattern{:source #datascript.parser.DefaultSrc{}
  ;;                                       :pattern [#datascript.parser.Variable{:symbol ?a}
  ;;                                                 #datascript.parser.Constant{:value :ft/made}
  ;;                                                 #datascript.parser.Variable{:symbol ?ft-pts}]}
  ;;            #datascript.parser.Function{:fn #datascript.parser.PlainSymbol{:symbol +}
  ;;                                        :args [#datascript.parser.Variable{:symbol ?ft-pts}
  ;;                                               #datascript.parser.Constant{:value 2}]
  ;;                                        :binding #datascript.parser.BindScalar{:variable #datascript.parser.Variable{:symbol ?pts}}}]}



  (dp/parse-where '[[?a :action/player ?player]
                    [?a :offense/players ?offense-tuple]
                    [(set ?offense-tuple) ?offense]
                    [(subset? ?offense-subset ?offense)] ;; here, ?offense-subset is not bound

                    (actions ?g ?t ?p ?a)
                    (pts ?a ?pts)])
  ;; [#datascript.parser.Pattern{:source #datascript.parser.DefaultSrc{},
  ;;                             :pattern [#datascript.parser.Variable{:symbol ?a}
  ;;                                       #datascript.parser.Constant{:value :action/player}
  ;;                                       #datascript.parser.Variable{:symbol ?player}]}
  ;;  #datascript.parser.Pattern{:source #datascript.parser.DefaultSrc{},
  ;;                             :pattern [#datascript.parser.Variable{:symbol ?a}
  ;;                                       #datascript.parser.Constant{:value :offense/players}
  ;;                                       #datascript.parser.Variable{:symbol ?offense-tuple}]}
  ;;  #datascript.parser.Function{:fn #datascript.parser.PlainSymbol{:symbol set},
  ;;                              :args [#datascript.parser.Variable{:symbol ?offense-tuple}],
  ;;                              :binding #datascript.parser.BindScalar{:variable #datascript.parser.Variable{:symbol ?offense}}}
  ;;  #datascript.parser.Predicate{:fn #datascript.parser.PlainSymbol{:symbol subset?},
  ;;                               :args [#datascript.parser.Variable{:symbol ?offense-subset}
  ;;                                      #datascript.parser.Variable{:symbol ?offense}]}
  ;;  #datascript.parser.RuleExpr{:source #datascript.parser.DefaultSrc{},
  ;;                              :name #datascript.parser.PlainSymbol{:symbol actions},
  ;;                              :args [#datascript.parser.Variable{:symbol ?g}
  ;;                                     #datascript.parser.Variable{:symbol ?t}
  ;;                                     #datascript.parser.Variable{:symbol ?p}
  ;;                                     #datascript.parser.Variable{:symbol ?a}]}
  ;;  #datascript.parser.RuleExpr{:source #datascript.parser.DefaultSrc{},
  ;;                              :name #datascript.parser.PlainSymbol{:symbol pts},
  ;;                              :args [#datascript.parser.Variable{:symbol ?a}
  ;;                                     #datascript.parser.Variable{:symbol ?pts}]}]


  (dp/parse-query
   '[:find ?a ?pts
     :in $ % #_[?g ...] #_[?t ...] [?player ...] #_?offense-subset subset?
     :where
     [?a :action/player ?player]
     [?a :offense/players ?offense-tuple]
     [(set ?offense-tuple) ?offense]
     [(subset? ?offense-subset ?offense)] ;; here, ?offense-subset is not bound

     (actions ?g ?t ?p ?a)
     (pts ?a ?pts)])
  ;; #datascript.parser.Query
  ;;  {:qfind #datascript.parser.FindRel{:elements [#datascript.parser.Variable{:symbol ?a}
  ;;                                                #datascript.parser.Variable{:symbol ?pts}]}
  ;;   :qwith nil
  ;;   :qreturn-map nil
  ;;   :qin [#datascript.parser.BindScalar{:variable #datascript.parser.SrcVar{:symbol $}}
  ;;         #datascript.parser.BindScalar{:variable #datascript.parser.RulesVar{}}
  ;;         #datascript.parser.BindColl{:binding #datascript.parser.BindScalar{:variable #datascript.parser.Variable{:symbol ?player}}}
  ;;         #datascript.parser.BindScalar{:variable #datascript.parser.Variable{:symbol subset?}}]
  ;;   :qwhere [#datascript.parser.Pattern{:source #datascript.parser.DefaultSrc{}
  ;;                                       :pattern [#datascript.parser.Variable{:symbol ?a}
  ;;                                                 #datascript.parser.Constant{:value :action/player}
  ;;                                                 #datascript.parser.Variable{:symbol ?player}]}
  ;;            #datascript.parser.Pattern{:source #datascript.parser.DefaultSrc{}
  ;;                                       :pattern [#datascript.parser.Variable{:symbol ?a}
  ;;                                                 #datascript.parser.Constant{:value :offense/players}
  ;;                                                 #datascript.parser.Variable{:symbol ?offense-tuple}]}
  ;;            #datascript.parser.Function{:fn #datascript.parser.PlainSymbol{:symbol set}
  ;;                                        :args [#datascript.parser.Variable{:symbol ?offense-tuple}]
  ;;                                        :binding #datascript.parser.BindScalar{:variable #datascript.parser.Variable{:symbol ?offense}}}
  ;;            #datascript.parser.Predicate{:fn #datascript.parser.PlainSymbol{:symbol subset?}
  ;;                                         :args [#datascript.parser.Variable{:symbol ?offense-subset}
  ;;                                                #datascript.parser.Variable{:symbol ?offense}]}
  ;;            #datascript.parser.RuleExpr{:source #datascript.parser.DefaultSrc{}
  ;;                                        :name #datascript.parser.PlainSymbol{:symbol actions}
  ;;                                        :args [#datascript.parser.Variable{:symbol ?g}
  ;;                                               #datascript.parser.Variable{:symbol ?t}
  ;;                                               #datascript.parser.Variable{:symbol ?p}
  ;;                                               #datascript.parser.Variable{:symbol ?a}]}
  ;;            #datascript.parser.RuleExpr{:source #datascript.parser.DefaultSrc{}
  ;;                                        :name #datascript.parser.PlainSymbol{:symbol pts}
  ;;                                        :args [#datascript.parser.Variable{:symbol ?a}
  ;;                                               #datascript.parser.Variable{:symbol ?pts}]}]}
  )