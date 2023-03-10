(ns app.db
  (:require
   [datascript.core :as d]
   [bball.db :as db]
   [re-frame.core :as re-frame]))

(def init-game
  '[{:teams {:V {:team/name "Las Vegas Aces"}
             :S {:team/name "Seattle Storm"}}}
    [:V 10 two make
     :S 30 two miss
     :V 41 reb 10 two make
     :V 22 turnover
     :S 5 two miss reb two make
     :V 22 two miss
     :S 30 reb 31 two miss :S reb 31 two miss
     :V 10 reb three miss 12 reb three miss 22 reb two miss
     period]])

(def default-db
  {:game-input (str init-game)})

(def conn (d/create-conn))

(def empty-db (d/empty-db db/ds-schema))

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
