(ns app.datascript
  (:require
   [bball.schema :as schema]
   [app.db :as db]
   [bball.query :as query]
   [cljs.reader :as reader]
   [datascript.core :as d]
   [reagent.core :as reagent]
   [clojure.pprint :as pprint]))


(def schema (schema/datomic->datascript-schema schema/schema))


(defn empty-db [] (d/empty-db schema))


(defn create-ratom-conn
  ([]
   (create-ratom-conn nil))
  ([schema]
   (reagent/atom (d/empty-db schema) :meta {:listeners (atom {})})))


(defonce conn (create-ratom-conn schema))


(defn datascript-game->tx-map
  [db g]
  (let [schema-keys (->> schema/schema
                         (remove :db/tupleAttrs)
                         (map :db/ident))
        pattern
        '[* {:game/home-team [:team/name]
             :game/away-team [:team/name]
             :game/possession [* {:possession/team [:team/name]}]}]
        pull-result->tx-map
        (fn pull-result->tx-map
          [pull-result]
          (cond
            (set? pull-result) (vec pull-result)
            (map? pull-result) (reduce-kv
                                (fn [m k v]
                                  (let [ft-results? (= :ft/results k)]
                                    (if (and ft-results? (empty? v))
                                      m ;; don't include empty :ft/results
                                      (assoc m k
                                             (if ft-results?
                                               (->> (concat v (repeat nil))
                                                    (take (max 2 (count (filter boolean? v))))
                                                    vec) ;; make sure :ft/results is minimum length 2, for tuple compliance
                                               (pull-result->tx-map v) ;; otherwise, recursively apply
                                               )))))
                                {}
                                (select-keys pull-result schema-keys))
            (vector? pull-result) (vec (map pull-result->tx-map pull-result))
            :else pull-result))]
    (pull-result->tx-map (d/pull db pattern g))))


(def game-local-storage-key "current-game")


(defn game->local-storage
  [db g]
  (.setItem js/localStorage game-local-storage-key
            (datascript-game->tx-map db g)))


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
  (let [filename "2023-07-06-Home-Away.edn"
        game-id (get @re-frame.db/app-db :game-id)
        game-map (datascript-game->tx-map @conn game-id)]
    (save-file filename
               (with-out-str (pprint/pprint game-map))))
  ;
  )
