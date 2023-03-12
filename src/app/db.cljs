(ns app.db
  (:require [bball.db :as db]
            [bball.query :as q]
            [datascript.core :as d]
            [re-frame.core :as re-frame]))


(def default-db
  {})

(def conn (d/create-conn db/ds-schema))

(def rules q/rules)

(def q-score
  '[:find ?team (sum ?pts)
    :in $ %
    :with ?a
    :where
    (actions ?g ?t ?p ?a)
    (pts ?a ?pts)
    [?t :team/name ?team]])

(re-frame/reg-cofx
 ::datascript-conn
 (fn [cofx _]
   (assoc cofx :conn conn)))
