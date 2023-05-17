(ns app.datascript
  (:require
   [bball.db :as db]
   [bball.query :as query]
   [cljs.reader :as reader]
   [datascript.core :as d]
   [reagent.core :as reagent]))


(defn create-ratom-conn
  ([]
   (create-ratom-conn nil))
  ([schema]
   (reagent/atom (d/empty-db schema) :meta {:listeners (atom {})})))


(def schema
  (->> db/schema
       (remove #(= "action.type" (namespace (:db/ident %)))) ;; removes enums
       (map
        (fn [{:db/keys [ident valueType] :as sch}]
          [ident
           (select-keys sch [:db/cardinality :db/unique :db/index :db/tupleAttrs :db/isComponent :db/doc
                             (when (case valueType
                                     :db.type/ref (not= "type" (name ident))
                                     :db.type/tuple (contains? sch :db/tupleAttrs) ;; TODO - probably no need to do this - just remove non-refs
                                     false)
                               :db/valueType)])]))
       (into {})))


(def empty-db (d/empty-db schema))


(defonce conn (create-ratom-conn schema))


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


(comment
  (->> (d/q '[:find (pull ?t [:team/name]) ?player (sum ?pts) (count ?a) (avg ?pts)
              :in $ % ?g
      ;;  :with ?a
              :where
              (actions ?g ?t ?p ?a)
              [?a :action/player ?player]
              [?a :action/type :action.type/shot]
              (pts ?a ?pts)]
            @conn query/rules 1)
       (map #(update % 0 :team/name))
       (sort-by #(nth % 2) >))
;;      team   p  pts s  pps
;; => (["Away" 24 16 13 1.2307692307692308]
;;     ["Home" 13 10 20 0.5]
;;     ["Home" 21 9 9 1]
;;     ["Away" 32 8 9 0.8888888888888888]
;;     ["Away" 10 7 9 0.7777777777777778]
;;     ["Away" 25 6 10 0.6]
;;     ["Home" 2 5 6 0.8333333333333334]
;;     ["Home" 22 4 2 2]
;;     ["Home" 12 3 8 0.375]
;;     ["Home" 1 3 2 1.5]
;;     ["Home" 20 2 2 1]
;;     ["Away" 15 2 4 0.5]
;;     ["Home" 4 0 1 0]
;;     ["Away" 35 0 2 0])
  )

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

