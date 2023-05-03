;; TODO - rename to datascript?
(ns app.datascript
  (:require [bball.db]
            [bball.query :as query]
            [cljs.reader :as reader]
            [datascript.core :as d]
            [reagent.core :as reagent]))


(defn create-ratom-conn
  ([]
   (create-ratom-conn nil))
  ([schema]
   (reagent/atom (d/empty-db schema) :meta {:listeners (atom {})})))


(def empty-db (d/empty-db bball.db/ds-schema))


(defonce conn (create-ratom-conn bball.db/ds-schema))


(def db-local-storage-key "datascript-db")


(defn db->local-storage
  [db]
  (.setItem js/localStorage db-local-storage-key (pr-str db)))


(defn local-storage->db
  []
  (some-> (.getItem js/localStorage db-local-storage-key)
          reader/read-string))


(defn teams
  [db g]
  (:game/teams (d/pull db '[{:game/teams [*]}] g)))


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


(comment
  (d/q '[:find (pull ?a [*])
         :where
         [?g :game/possession ?p]
         [?p :possession/action ?a]
         [?a :offense/players ?o-players]
         [?a :defense/players ?d-players]
         [?p :possession/team 3]
         [(set ?o-players) ?o-players-set]
         [(contains? ?o-players-set 11)]]
       @conn))


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
        action          (assoc action
                               :action/order (if poss-change? 1 (inc (get last-action :action/order 0)))
                               :offense/players (get-in players [team-id :on-court])
                               :defense/players (:on-court (val (first (dissoc players team-id)))))]
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
(defn game->team-players
  [db g]
  (d/q '[:find ?t (distinct ?player)
         :in $ ?g
         :where
         [?g :game/teams ?t]
         [?g :game/possession ?p]
         [?p :possession/action ?a]
         [?a :offense/players ?offense]
         [?a :defense/players ?defense]
         (or (and [?p :possession/team ?t]
                  [(ground ?offense) [?player ...]])
             (and (not [?p :possession/team ?t])
                  [(ground ?defense) [?player ...]]))]
       db g))


(defn save-db
  ([]
   (save-db "game-db.edn"))
  ([filename]
   (let [db-string (pr-str @conn)
         url (.createObjectURL js/URL (js/File. [db-string] filename))]
     (doto (.createElement js/document "a")
       (.setAttribute "download" filename)
       (.setAttribute "href" url)
       (.click))
     (.revokeObjectURL js/URL url))))


(defn datom->tx
  [datom schema]
  (let [{:keys [e a v]} datom
        ref? (= :db.type/ref (get-in schema [a :db/valueType]))]
    [:db/add (- e) a (if ref? (- v) v)]))

(let [schema bball.db/ds-schema
      tx-datoms (->> (d/datoms @conn :eavt)
                     (map #(datom->tx % schema)))]
  (->> (-> (d/empty-db bball.db/ds-schema)
           (d/db-with [{:game/teams [{:team/name "Anacortes"} {:team/name "Blaine"}]
                        :game/possession [{:possession/period 1
                                           :possession/order 1
                                           :possession/team {:team/name "Blaine"}
                                           :possession/action [{:action/order 1
                                                                :action/type :action.type/shot
                                                                :action/player 12
                                                                :shot/value 3
                                                                :shot/make? true
                                                                :offense/players #{1 2 3 4 12}
                                                                :defense/players #{11 21 13 14 22}}]}]}])
           (d/db-with tx-datoms)
           (d/datoms :eavt))
       (group-by :tx)))
