(ns bball.core
  (:require [bball.parser :refer [parse]]
            [bball.query :as q]
            [bball.db :as db]
            [datomic.client.api :as d]))

(def client (d/client db/dev-config))

(def db {:db-name "clj-bball-db-dev"})

(comment
  (d/delete-database client db)
  )

(d/create-database client db)

(def conn (d/connect client db))

(d/transact conn {:tx-data db/schema})

(def wnba-game (-> "games/2022-09-06-Vegas-Seattle.edn" slurp read-string parse))
(def wnba-game-2 (-> "games/2022-09-04-Vegas-Seattle.edn" slurp read-string parse))
(def hs-game (-> "games/2022-02-05-Blaine-Ferndale.edn" slurp read-string parse))

(d/transact conn {:tx-data [wnba-game hs-game wnba-game-2]})

(->> (d/q '[:find (pull ?g [:db/id :game/datetime :game/minutes]) (pull ?t [:team/name]) (sum ?pts)
            :in $ %
            :with ?a
            :where
            (actions ?g ?t ?p ?a)
            (pts ?a ?pts)]
          (d/db conn)
          q/rules)
     (map (fn [[{g :db/id dt :game/datetime min :game/minutes} {team :team/name} pts]] [g dt min team pts]))
     (sort-by last >)
     (sort-by first)
     (into [[:game :datetime :minutes :team :points]]))
;; => [[:game :datetime :minutes :team :points]
;;     [79164837199967 #inst "2022-09-07T01:00:00.000-00:00" 40 "Las Vegas Aces" 97]
;;     [79164837199967 #inst "2022-09-07T01:00:00.000-00:00" 40 "Seattle Storm" 92]
;;     [79164837200300 #inst "2022-02-06T02:15:00.000-00:00" 32 "Blaine Borderites" 65]
;;     [79164837200300 #inst "2022-02-06T02:15:00.000-00:00" 32 "Ferndale Golden Eagles" 63]
;;     [79164837200577 #inst "2022-09-05T02:00:00.000-00:00" 45 "Las Vegas Aces" 110]
;;     [79164837200577 #inst "2022-09-05T02:00:00.000-00:00" 45 "Seattle Storm" 98]]


(def wnba-game-eid 79164837199967)
(def hs-game-eid 79164837200300)
(def wnba-game-2-eid 96757023244993)

(d/q '[:find (pull ?t [:team/name]) ?type ?lineup (sum ?pts) (count-distinct ?p)
       :in $ % ?g ?players [?type ...]
       :with ?a
       :where
       (actions ?g ?t ?p ?a)
       (pts ?a ?pts)
       (lineup? ?a ?type ?players)
       (lineup ?a ?type ?lineup)
       ]
     (d/db conn)
     (conj q/rules
           '[(lineup? ?a ?type ?players)
             [?a ?type ?ps]
             [(set ?ps) ?lineup]
             [(clojure.set/subset? ?players ?lineup)]]
           '[(lineup ?a ?type ?lineup)
             [?a ?type ?ps]
             [(set ?ps) ?lineup]]
           '[(floor? ?a ?players)
             (lineup? ?a :offense/players ?players)]
           '[(floor? ?a ?players)
             (lineup? ?a :defense/players ?players)])
     wnba-game-2-eid
    ;;  #{} ; all unique lineups
     #{10 24 30} ; all unique lineups with 10, 24, and 30 in them
     [:offense/players :defense/players]
     )

