(ns app.db
  (:require [bball.db :as db]
            [bball.query :as query]
            [datascript.core :as d]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [datascript.transit :as dt]))


;; re-export to keep datascript.core out of app.events
(def transact! d/transact!)


(defn create-ratom-conn
  ([]
   (create-ratom-conn nil))
  ([schema]
   (reagent/atom (d/empty-db schema) :meta {:listeners (atom {})})))


(defonce conn (create-ratom-conn db/ds-schema))


(re-frame/reg-cofx
 ::datascript-conn
 (fn [cofx _]
   (assoc cofx :conn conn)))


(defn teams
  [db g]
  (:game/teams (d/pull db [{:game/teams '[*]}] g)))


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
  (->> (d/q '[:find ?g
              :in $ ?g
              :where
              [?g :game/possession]]
            db g)
       empty?))


(defn last-possession
  [db g]
  (apply max-key :possession/order (possessions db g)))


(defn last-action
  [possession]
  (apply max-key :action/order (:possession/action possession)))


(defn last-action-db
  [db g]
  (last-action (last-possession db g)))


(defn box-score
  [db g]
  (d/q '[:find ?t ?number (sum ?pts)
         :in $ % ?g
         :with ?a
         :where
         (actions ?g ?t ?p ?a)
         [?a :action/player ?number]
         (pts ?a ?pts)]
       db query/rules g))


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
         [?a :shot/rebounder]
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


;; TODO - use return maps! then you can destructure maps instead of vectors after?
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
  [{:keys [shot/off-reb? action/type]}]
  (not (or off-reb? (= type :action.type/technical))))


(defn other-team
  [teams t]
  (->> teams
       (remove #(= t (:db/id %)))
       first))


(defn other-team-db
  [db g t]
  (other-team (:game/teams (d/pull db '[{:game/teams [*]}] g)) t))


(defn team-possession
  [db g]
  (let [last-possession (last-possession db g)
        last-possession-team (:possession/team last-possession)]
    (if-some [last-action (last-action last-possession)]
      (if (action-change-possession? last-action)
        (other-team-db db g (:db/id last-possession-team))
        last-possession-team)
      nil)))


;; TODO - see if you can do :_game/possession and :_possession/action easily (in this case, for db.cardinality/many)
(defn append-action-tx-map
  [db g action players init]
  (let [last-possession (last-possession db g)
        last-action     (last-action last-possession)
        period          (if-some [{init-period :init/period} init]
                          init-period
                          (:possession/period last-possession))
        poss-change?    (if (some? init)
                          true
                          (action-change-possession? last-action))
        team-id         (if-some [{init-team :init/team} init]
                          (:db/id init-team)
                          (let [last-team-id (get-in last-possession [:possession/team :db/id])]
                            (if poss-change?
                              (:db/id (other-team-db db g last-team-id))
                              last-team-id)))
        action          (assoc action
                               :action/order (if poss-change? 1 (inc (get last-action :action/order 0)))
                               :offense/players (get players team-id)
                               :defense/players (val (first (dissoc players team-id))))]
    (if poss-change?
      {:db/id g
       :game/possession [{:possession/order (inc (get last-possession :possession/order 0))
                          :possession/team team-id
                          :possession/period period
                          :possession/action [action]}]}
      {:db/id (:db/id last-possession)
       :possession/action [action]})))


(defn save-db
  ([]
   (save-db "game-db.edn"))
  ([filename]
   (let [db-string (dt/write-transit-str @conn)
         url (.createObjectURL js/URL (js/File. [db-string] filename))]
     (doto (.createElement js/document "a")
       (.setAttribute "download" filename)
       (.setAttribute "href" url)
       (.click))
     (.revokeObjectURL js/URL url))))

