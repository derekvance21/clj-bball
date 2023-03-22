(ns app.db
  (:require [bball.db :as db]
            [bball.query :as q]
            [datascript.core :as d]
            [re-frame.core :as re-frame]))

(def conn (d/create-conn db/ds-schema))



(def rules q/rules)

(def q-score
  '[:find ?team (sum ?pts)
    :in $ % ?g
    :with ?a
    :where
    (actions ?g ?t ?p ?a)
    (pts ?a ?pts)
    [?t :team/name ?team]])

(defn score
  [db g]
  (d/q q-score db rules g))

(re-frame/reg-cofx
 ::datascript-conn
 (fn [cofx _]
   (assoc cofx :conn conn)))

(defn possessions
  [db g]
  (d/q '[:find [(pull ?p [{:possession/team [*]} *]) ...]
         :in $ % ?g
         :where
         [?g :game/possession ?p]]
       db q/rules g))