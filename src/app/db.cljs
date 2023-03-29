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

(defn score
  ([g]
   (score g @conn))
  ([g db]
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
   (possessions g @conn))
  ([g db]
   (d/q '[:find [(pull ?p [* {:possession/team [:db/id :team/name]}]) ...]
          :in $ ?g
          :where
          [?g :game/possession ?p]]
        db g)))

(defn last-possession
  ([g]
   (last-possession g @conn))
  ([g db]
   (apply max-key :possession/order (possessions g db))))
