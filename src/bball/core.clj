(ns bball.core
  (:require [datahike.api :as d]
            [bball.parser :refer [parse]]
            [bball.query :as q]
            [bball.db :refer [dev-config]]))

(d/create-database dev-config)
(def conn (d/connect dev-config))

(def wnba-game (-> "games/2022-09-06-Vegas-Seattle.edn" slurp read-string parse))
(def hs-game (-> "games/2022-02-05-Blaine-Ferndale.edn" slurp read-string parse))

(d/transact conn [wnba-game hs-game])

(->> (d/q '[:find (pull ?g [:db/id :game/datetime :game/minutes]) (pull ?t [:team/name]) (sum ?pts)
            :in $ %
            :with ?a
            :where
            (actions ?g ?t ?p ?a)
            (pts ?a ?pts)]
          @conn
          q/rules)
     (map (fn [[{g :db/id dt :game/datetime min :game/minutes} {team :team/name} pts]] [g dt min team pts]))
     (sort-by last >)
     (sort-by first)
     (into [[:game :datetime :minutes :team :points]]))
;; => [[:game :datetime :minutes :team :points]
;;     [25 #inst "2022-09-07T01:00:00.000-00:00" 40 "Las Vegas Aces" 97]
;;     [25 #inst "2022-09-07T01:00:00.000-00:00" 40 "Seattle Storm" 92]
;;     [358 #inst "2022-02-06T02:15:00.000-00:00" 32 "Blaine Borderites" 65]
;;     [358 #inst "2022-02-06T02:15:00.000-00:00" 32 "Ferndale Golden Eagles" 63]]

(def wnba-game-eid 25)
(def hs-game-eid 358)

(q/box-score @conn wnba-game-eid)
(q/box-score @conn hs-game-eid)
