(ns app.db
  (:require [bball.db :as db]
            [bball.query :as query]
            [datascript.core :as d]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))

(defn create-ratom-conn
  ([]
   (create-ratom-conn nil))
  ([schema]
   (reagent/atom (d/empty-db schema) :meta {:listeners (atom {})})))

(def conn (create-ratom-conn db/ds-schema))

(defn ppp
  ([g]
   (ppp @conn g))
  ([db g]
   (d/q '[:find ?t (sum ?pts) (count-distinct ?p)
          :in $ % ?g
          :with ?a
          :where
          (actions ?g ?t ?p ?a)
          (pts ?a ?pts)]
        db query/rules g)
   ))

(defn score
  ([g]
   (score @conn g))
  ([db g]
   (d/q '[:find ?t (sum ?pts)
          :in $ % ?g
          :with ?a
          :where
          (actions ?g ?t ?p ?a)
          (pts ?a ?pts)]
        db query/rules g)))

(re-frame/reg-cofx
 ::datascript-conn
 (fn [cofx _]
   (assoc cofx :conn conn)))

(defn possessions
  ([g]
   (possessions @conn g))
  ([db g]
   (d/q '[:find [(pull ?p [* {:possession/team [:db/id :team/name]}]) ...]
          :in $ ?g
          :where
          [?g :game/possession ?p]]
        db g)))

(defn possessions?
  [db g]
  (->> (d/q '[:find ?g
              :in $ ?g
              :where
              [?g :game/possession]]
            db g)
       empty?))

(defn last-possession
  ([g]
   (last-possession @conn g))
  ([db g]
   (apply max-key :possession/order (possessions db g))))

(defn box-score
  ([g]
   (box-score @conn g))
  ([db g]
   (d/q '[:find ?t ?number (sum ?pts)
          :in $ % ?g
          :with ?a
          :where
          (actions ?g ?t ?p ?a)
          [?a :action/player ?number]
          (pts ?a ?pts)]
        db query/rules g)))

(defn efg
  ([g]
   (efg @conn g))
  ([db g]
   (d/q '[:find ?t (avg ?efgs)
          :in $ % ?g
          :with ?a
          :where
          (actions ?g ?t ?p ?a)
          (fga? ?a)
          (efgs ?a ?efgs)]
        db query/rules g)))

(defn off-reb-rate
  ([g]
   (off-reb-rate @conn g))
  ([db g]
   (d/q '[:find ?t (avg ?off-rebs)
          :in $ % ?g
          :with ?a
          :where
          (actions ?g ?t ?p ?a)
          [?a :shot/rebounder]
          (off-rebs ?a ?off-rebs)]
        db query/rules g)))

(defn turnover-rate
  ([g]
   (turnover-rate @conn g))
  ([db g]
   (->> (d/q '[:find ?t (sum ?tos) (count-distinct ?p)
               :in $ % ?g
               :with ?a
               :where
               (actions ?g ?t ?p ?a)
               (tos ?a ?tos)]
             db query/rules g)
        (map (fn [[t tos nposs]]
               [t (/ tos nposs)])))))

(defn pps
  ([g]
   (pps @conn g))
  ([db g]
   (d/q '[:find ?t (avg ?pts)
          :in $ % ?g
          :with ?a
          :where
          (actions ?g ?t ?p ?a)
          [?a :action/type :action.type/shot]
          (pts ?a ?pts)]
        db query/rules g)))
