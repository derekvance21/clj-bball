(ns app.datascript
  (:require
   [bball.schema :as schema]
   [app.db :as db]
   [bball.query :as query]
   [cljs.reader :as reader]
   [datascript.core :as d]
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


(def shots-query-by-player
  '[:find (pull ?a [:shot/distance :shot/angle :shot/make?]) ?pts
    :in $ % ?t ?numbers
    :where
    [?p :possession/team ?t]
    [?p :possession/action ?a]
    [?a :action/type :action.type/shot]
    [?a :action/player ?player]
    [(contains? ?numbers ?player)]
    (pts ?a ?pts)])


(def ft-pct-query
  '[:find [(sum ?made) (sum ?attempted)]
    :in $ %
    :with ?a
    :where
    (actions ?g ?t ?p ?a)
    [?a :ft/attempted ?attempted]
    [?a :ft/made ?made]])


;; ft-percent in all logged conference games:
(->> (d/q ft-pct-query @conn query/rules)
     (apply /)
     (* 100))
;; => 64.90384615384616


(def off-reb-rate-by-player
  '[:find ?player-numbers (avg ?off-rebs)
    :keys player off-reb-rate
    :in $ % ?t [?player-numbers ...]
    :with ?a
    :where
    (actions ?g ?t ?p ?a)
    (rebound? ?a)
    [?a :offense/players ?offense]
    [(ground ?offense) [?player ...]]
    [(contains? ?player-numbers ?player)]
    (off-rebs-player ?a ?player ?off-rebs)])

(->> (d/q off-reb-rate-by-player @conn query/rules
          [:team/name "Blaine"]
          [#{1} #{2 5} #{3} #{4} #{10} #{20} #{30} #{35} #{42}])
     (map #(update % :off-reb-rate (partial * 100)))
     (sort-by :off-reb-rate >))
;; => ({:player #{30}, :off-reb-rate 14.634146341463413}
;;     {:player #{42}, :off-reb-rate 11.987381703470032}
;;     {:player #{20}, :off-reb-rate 11.475409836065573}
;;     {:player #{3}, :off-reb-rate 5.88235294117647}
;;     {:player #{35}, :off-reb-rate 5}
;;     {:player #{2 5}, :off-reb-rate 4.067796610169491}
;;     {:player #{1}, :off-reb-rate 4.038004750593824}
;;     {:player #{4}, :off-reb-rate 3.728813559322034}
;;     {:player #{10}, :off-reb-rate 0})


(->> (d/q '[:find ?numbers ?sector (avg ?pts) (count ?a)
            :keys player sector pps shots
            :in $ % [[?team ?numbers] ...]
            :where
            (actions ?g ?t ?p ?a)
            [?a :action/player ?player]
            [(contains? ?numbers ?player)]
            [?a :shot/distance ?inches]
            [?a :shot/value ?value]
            (sector ?value ?inches ?sector)
            (pts ?a ?pts)
            [?t :team/name ?team]]
          @conn
          query/rules
          (map vector (repeat "Blaine") [#{1} #{2 5} #{3} #{4} #{10} #{20} #{30} #{35} #{42}]))
     (filter #(>= (:shots %) 10))
     (sort-by :pps >))
;; => ({:player #{1}, :sector "0-3", :pps 1.5853658536585367, :shots 41}
;;     {:player #{4}, :sector "0-3", :pps 1.5384615384615385, :shots 13}
;;     {:player #{3}, :sector "0-3", :pps 1.4864864864864864, :shots 37}
;;     {:player #{42}, :sector "0-3", :pps 1.3968253968253967, :shots 63}
;;     {:player #{35}, :sector "0-3", :pps 1.3666666666666667, :shots 30}
;;     {:player #{35}, :sector "3P-24", :pps 1.3421052631578947, :shots 38}
;;     {:player #{1}, :sector "3P-24", :pps 1.3285714285714285, :shots 70}
;;     {:player #{2 5}, :sector "0-3", :pps 1.28, :shots 25}
;;     {:player #{4}, :sector "3P-24", :pps 1.1818181818181819, :shots 33}
;;     {:player #{3}, :sector "3-10", :pps 1.1764705882352942, :shots 119}
;;     {:player #{35}, :sector "24+", :pps 1.0666666666666667, :shots 45}
;;     {:player #{1}, :sector "3-10", :pps 1.0634920634920635, :shots 63}
;;     {:player #{20}, :sector "0-3", :pps 1.0588235294117647, :shots 17}
;;     {:player #{42}, :sector "3P-24", :pps 1, :shots 36}
;;     {:player #{2 5}, :sector "3P-24", :pps 0.9545454545454546, :shots 44}
;;     {:player #{35}, :sector "3-10", :pps 0.875, :shots 40}
;;     {:player #{4}, :sector "3-10", :pps 0.7727272727272727, :shots 22}
;;     {:player #{3}, :sector "3P-24", :pps 0.7692307692307693, :shots 39}
;;     {:player #{42}, :sector "3-10", :pps 0.7272727272727273, :shots 22}
;;     {:player #{1}, :sector "10-3P", :pps 0.6666666666666666, :shots 15}
;;     {:player #{35}, :sector "10-3P", :pps 0.6666666666666666, :shots 15}
;;     {:player #{2 5}, :sector "3-10", :pps 0.5555555555555556, :shots 18}
;;     {:player #{20}, :sector "3-10", :pps 0.38461538461538464, :shots 13})


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


(->> (d/q '[:find ?player (count-distinct ?a) (avg ?3fgs)
            :keys player fg3as fg3-rate
            :in $ % ?t [?player ...]
            :where
            (actions ?g ?t ?p ?a)
            [?a :action/player ?pl]
            [(contains? ?player ?pl)]
            [?a :shot/value 3]
            (fga? ?a)
            (fgs ?a ?3fgs)]
          @conn query/rules
          [:team/name "Blaine"]
          [#{1} #{2 5} #{3} #{4} #{10} #{20} #{30} #{35} #{42}])
     (map (fn [result]
            (-> result
                (assoc :3pt% (* 100 (:fg3-rate result)))
                (dissoc :fg3-rate))))
     (sort-by :3pt% >))
;; => ({:player #{1}, :fg3as 74, :3pt% 44.5945945945946}
;;     {:player #{35}, :fg3as 83, :3pt% 39.75903614457831}
;;     {:player #{4}, :fg3as 33, :3pt% 39.39393939393939}
;;     {:player #{42}, :fg3as 36, :3pt% 33.33333333333333}
;;     {:player #{10}, :fg3as 3, :3pt% 33.33333333333333}
;;     {:player #{2 5}, :fg3as 44, :3pt% 31.818181818181817}
;;     {:player #{3}, :fg3as 40, :3pt% 25}
;;     {:player #{30}, :fg3as 2, :3pt% 0})


(->> (d/q '[:find ?player (count-distinct ?a) (avg ?tos)
            :keys player actions to-rate
            :in $ % ?t [?player ...]
            :where
            (actions ?g ?t ?p ?a)
            [?a :action/player ?pl]
            [(contains? ?player ?pl)]
            (tos ?a ?tos)]
          @conn query/rules
          [:team/name "Blaine"]
          [#{1} #{2 5} #{3} #{4} #{10} #{20} #{30} #{35} #{42}])
     (sort-by :to-rate >))
;; => ({:player #{10}, :actions 9, :to-rate 0.4444444444444444}
;;     {:player #{30}, :actions 10, :to-rate 0.3}
;;     {:player #{4}, :actions 94, :to-rate 0.26595744680851063}
;;     {:player #{2 5}, :actions 120, :to-rate 0.23333333333333334}
;;     {:player #{1}, :actions 243, :to-rate 0.17695473251028807}
;;     {:player #{35}, :actions 205, :to-rate 0.16097560975609757}
;;     {:player #{3}, :actions 237, :to-rate 0.15611814345991562}
;;     {:player #{42}, :actions 143, :to-rate 0.13986013986013987}
;;     {:player #{20}, :actions 36, :to-rate 0.1111111111111111})


(->> (d/q '[:find ?player (sum ?ftm) (sum ?fta)
            :keys player ftm fta
            :in $ % ?t [?player ...]
            :with ?a
            :where
            (actions ?g ?t ?p ?a)
            [?a :action/player ?pl]
            [(contains? ?player ?pl)]
            [?a :ft/made ?ftm]
            [?a :ft/attempted ?fta]]
          @conn query/rules
          [:team/name "Blaine"]
          [#{1} #{2 5} #{3} #{4} #{10} #{20} #{30} #{35} #{42}])
     (map (fn [{:keys [ftm fta] :as result}]
            (assoc result :ft% (* 100 (/ ftm fta)))))
     (sort-by :ft% >))
;; => ({:player #{1}, :ftm 35, :fta 46, :ft% 76.08695652173914}
;;     {:player #{35}, :ftm 31, :fta 42, :ft% 73.80952380952381}
;;     {:player #{20}, :ftm 5, :fta 8, :ft% 62.5}
;;     {:player #{42}, :ftm 8, :fta 13, :ft% 61.53846153846154}
;;     {:player #{3}, :ftm 27, :fta 44, :ft% 61.36363636363637}
;;     {:player #{2 5}, :ftm 6, :fta 12, :ft% 50}
;;     {:player #{10}, :ftm 1, :fta 2, :ft% 50}
;;     {:player #{4}, :ftm 1, :fta 4, :ft% 25}
;;     {:player #{30}, :ftm 0, :fta 1, :ft% 0})


(->> (d/q '[:find (pull ?t [:team/name]) (count-distinct ?g) (sum ?pts) (count-distinct ?p)
            :keys team games total-pts possessions
            :in $ %
            :with ?a
            :where
            (actions ?g ?t ?p ?a)
            (pts ?a ?pts)]
          @conn
          query/rules)
     (map (fn [{:keys [total-pts possessions] :as result}]
            (assoc result :offrtg (-> total-pts
                                      (/ possessions)
                                      (* 100)))))
     (map #(assoc % :team (get-in % [:team :team/name])))
     (sort-by :offrtg >))
;; => ({:team "Lynden", :games 1, :total-pts 75, :possessions 52, :offrtg 144.23076923076923}
;;     {:team "Anacortes", :games 1, :total-pts 84, :possessions 66, :offrtg 127.27272727272727}
;;     {:team "Lynden Christian", :games 1, :total-pts 70, :possessions 59, :offrtg 118.64406779661016}
;;     {:team "Mount Vernon", :games 1, :total-pts 82, :possessions 71, :offrtg 115.49295774647888}
;;     {:team "Sehome", :games 1, :total-pts 65, :possessions 57, :offrtg 114.03508771929825}
;;     {:team "Burlington-Edison", :games 1, :total-pts 72, :possessions 65, :offrtg 110.76923076923077}
;;     {:team "Blaine", :games 15, :total-pts 1021, :possessions 944, :offrtg 108.15677966101696}
;;     {:team "Squalicum", :games 1, :total-pts 62, :possessions 58, :offrtg 106.89655172413792}
;;     {:team "Lakewood", :games 1, :total-pts 63, :possessions 66, :offrtg 95.45454545454545}
;;     {:team "Ferndale", :games 1, :total-pts 52, :possessions 57, :offrtg 91.22807017543859}
;;     {:team "Nooksack Valley", :games 1, :total-pts 60, :possessions 66, :offrtg 90.9090909090909}
;;     {:team "Meridian", :games 1, :total-pts 55, :possessions 63, :offrtg 87.3015873015873}
;;     {:team "Oak Harbor", :games 1, :total-pts 56, :possessions 65, :offrtg 86.15384615384616}
;;     {:team "Mount Baker", :games 1, :total-pts 49, :possessions 65, :offrtg 75.38461538461539}
;;     {:team "Sedro-Woolley", :games 1, :total-pts 47, :possessions 64, :offrtg 73.4375}
;;     {:team "Bellingham", :games 1, :total-pts 46, :possessions 72, :offrtg 63.888888888888886})


(->> (d/q '[:find (pull ?t [:team/name]) (count-distinct ?g) (sum ?pts) (count-distinct ?p)
            :keys team games total-pts possessions
            :in $ %
            :with ?a
            :where
            (actions ?g ?ot ?p ?a)
            (or [?g :game/home-team ?t]
                [?g :game/away-team ?t])
            (not [?p :possession/team ?t])
            (pts ?a ?pts)]
          @conn
          query/rules)
     (map (fn [{:keys [total-pts possessions] :as result}]
            (assoc result :defrtg (-> total-pts
                                      (/ possessions)
                                      (* 100)))))
     (map #(assoc % :team (get-in % [:team :team/name])))
     (sort-by :defrtg <))
;; => ({:team "Sehome", :games 1, :total-pts 49, :possessions 58, :defrtg 84.48275862068965}
;;     {:team "Bellingham", :games 1, :total-pts 60, :possessions 71, :defrtg 84.50704225352112}
;;     {:team "Lynden", :games 1, :total-pts 47, :possessions 52, :defrtg 90.38461538461539}
;;     {:team "Lynden Christian", :games 1, :total-pts 56, :possessions 59, :defrtg 94.91525423728814}
;;     {:team "Oak Harbor", :games 1, :total-pts 64, :possessions 65, :defrtg 98.46153846153847}
;;     {:team "Blaine", :games 15, :total-pts 938, :possessions 946, :defrtg 99.15433403805497}
;;     {:team "Sedro-Woolley", :games 1, :total-pts 65, :possessions 64, :defrtg 101.5625}
;;     {:team "Anacortes", :games 1, :total-pts 70, :possessions 67, :defrtg 104.4776119402985}
;;     {:team "Mount Vernon", :games 1, :total-pts 79, :possessions 73, :defrtg 108.21917808219179}
;;     {:team "Nooksack Valley", :games 1, :total-pts 74, :possessions 67, :defrtg 110.44776119402985}
;;     {:team "Lakewood", :games 1, :total-pts 76, :possessions 65, :defrtg 116.92307692307693}
;;     {:team "Burlington-Edison", :games 1, :total-pts 74, :possessions 63, :defrtg 117.46031746031747}
;;     {:team "Squalicum", :games 1, :total-pts 69, :possessions 57, :defrtg 121.05263157894737}
;;     {:team "Ferndale", :games 1, :total-pts 70, :possessions 57, :defrtg 122.80701754385966}
;;     {:team "Meridian", :games 1, :total-pts 78, :possessions 62, :defrtg 125.80645161290323}
;;     {:team "Mount Baker", :games 1, :total-pts 90, :possessions 64, :defrtg 140.625})


(->> (d/q '[:find (pull ?t [:team/name]) (sum ?3fgs) (count-distinct ?a) (avg ?3fgs)
            :keys team fg3s fg3as fg3-rate
            :in $ %
            :where
            (actions ?g ?t ?p ?a)
            (fga? ?a)
            [?a :shot/value 3]
            (fgs ?a ?3fgs)]
          @conn
          query/rules)
     (map (fn [{:keys [fg3-rate] :as result}]
            (-> result
                (assoc :3pt% (* 100 fg3-rate))
                (dissoc :fg3-rate))))
     (map #(assoc % :team (get-in % [:team :team/name])))
     (sort-by :3pt% >))
;; => ({:team "Lynden", :fg3s 10, :fg3as 20, :3pt% 50}
;;     {:team "Ferndale", :fg3s 6, :fg3as 14, :3pt% 42.857142857142854}
;;     {:team "Lynden Christian", :fg3s 8, :fg3as 19, :3pt% 42.10526315789473}
;;     {:team "Blaine", :fg3s 117, :fg3as 318, :3pt% 36.79245283018868}
;;     {:team "Oak Harbor", :fg3s 6, :fg3as 17, :3pt% 35.294117647058826}
;;     {:team "Squalicum", :fg3s 6, :fg3as 18, :3pt% 33.33333333333333}
;;     {:team "Lakewood", :fg3s 8, :fg3as 26, :3pt% 30.76923076923077}
;;     {:team "Anacortes", :fg3s 5, :fg3as 17, :3pt% 29.411764705882355}
;;     {:team "Mount Vernon", :fg3s 5, :fg3as 20, :3pt% 25}
;;     {:team "Meridian", :fg3s 5, :fg3as 20, :3pt% 25}
;;     {:team "Nooksack Valley", :fg3s 5, :fg3as 20, :3pt% 25}
;;     {:team "Mount Baker", :fg3s 4, :fg3as 17, :3pt% 23.52941176470588}
;;     {:team "Burlington-Edison", :fg3s 4, :fg3as 17, :3pt% 23.52941176470588}
;;     {:team "Sehome", :fg3s 5, :fg3as 22, :3pt% 22.727272727272727}
;;     {:team "Sedro-Woolley", :fg3s 3, :fg3as 20, :3pt% 15}
;;     {:team "Bellingham", :fg3s 2, :fg3as 15, :3pt% 13.333333333333334})



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

