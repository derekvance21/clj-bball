(ns bball.core
  (:require [datahike.api :as d]
            [bball.parser :refer [parse]]
            [bball.query :refer [rules]]))

(def schema [;; action
             {:db/ident :action/type
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "the enumerated type of an action"}
             {:db/ident :action/order
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the order this action happened in its possession"}
             {:db/ident :player/number
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number of the player"}
             {:db/ident :ft/made
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number of made free throws in this action"}
             {:db/ident :ft/attempted
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number of attempted free throws in this action"}

             ;; action/type's
             {:db/ident :action.type/turnover}
             {:db/ident :action.type/bonus}
             {:db/ident :action.type/technical}
             {:db/ident :action.type/shot}

             ;; shot
             {:db/ident :shot/distance
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the distance from the hoop in feet the shot was attempted from"}
             {:db/ident :shot/rebounder
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number player of the rebounder of the shot attempt"}
             {:db/ident :shot/value
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the value of the shot attempted, 2 or 3"}
             {:db/ident :shot/make?
              :db/valueType :db.type/boolean
              :db/cardinality :db.cardinality/one
              :db/doc "whether or not the shot was made"}
             {:db/ident :shot/off-reb?
              :db/valueType :db.type/boolean
              :db/cardinality :db.cardinality/one
              :db/doc "whether or not the shot was offensive rebounded"}
             {:db/ident :shot/blocker
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number player of the blocker of the shot attempt"}

             ;; turnover
             {:db/ident :turnover/stealer
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number player of the stealer of the turnover"}

             ;; possession
             {:db/ident :possession/action
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/isComponent true
              :db/doc "the actions of this possession"}
             {:db/ident :possession/team
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "the team with the ball on this possession"}
             {:db/ident :possession/order
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the order this possession happened in its game"}

             ;; game
             {:db/ident :game/possession
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/isComponent true
              :db/doc "the possessions of this game"}
             {:db/ident :game/minutes
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number of minutes this game lasted"}
             {:db/ident :game/teams
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "the teams playing in this game"}
             {:db/ident :game/datetime
              :db/valueType :db.type/instant
              :db/cardinality :db.cardinality/one
              :db/doc "the datetime this game started"}

             ;; team
             {:db/ident :team/name
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/unique :db.unique/identity
              :db/doc "the name of the team"}])

(def config {:store {:backend :mem :id "default"}
             :initial-tx schema})

(d/create-database config)

(def conn (d/connect config))

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
          rules)
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

(defn box-score
  [game-eid]
  (->> (d/q '[:find (pull ?t [:team/name]) ?number (sum ?pts)
              :in $ % ?g
              :with ?a
              :where
              (actions ?g ?t ?p ?a)
              [?a :player/number ?number]
              (pts ?a ?pts)]
            @conn
            rules
            game-eid)
       (map #(update % 0 :team/name))
       (sort-by last >)
       (sort-by first)
       (into [[:team :player :points]])))

(box-score wnba-game-eid)
;; => [[:team :player :points]
;;     ["Las Vegas Aces" 12 31]
;;     ["Las Vegas Aces" 22 23]
;;     ["Las Vegas Aces" 0 18]
;;     ["Las Vegas Aces" 10 15]
;;     ["Las Vegas Aces" 2 6]
;;     ["Las Vegas Aces" 41 4]
;;     ["Las Vegas Aces" 5 0]
;;     ["Seattle Storm" 30 42]
;;     ["Seattle Storm" 24 29]
;;     ["Seattle Storm" 10 8]
;;     ["Seattle Storm" 5 8]
;;     ["Seattle Storm" 7 3]
;;     ["Seattle Storm" 31 2]
;;     ["Seattle Storm" 13 0]
;;     ["Seattle Storm" 20 0]]

(box-score hs-game-eid)
;; => [[:team :player :points]
;;     ["Blaine Borderites" 10 15]
;;     ["Blaine Borderites" 1 15]
;;     ["Blaine Borderites" 12 12]
;;     ["Blaine Borderites" 35 11]
;;     ["Blaine Borderites" 3 7]
;;     ["Blaine Borderites" 21 5]
;;     ["Blaine Borderites" 4 0]
;;     ["Ferndale Golden Eagles" 13 18]
;;     ["Ferndale Golden Eagles" 23 14]
;;     ["Ferndale Golden Eagles" 21 12]
;;     ["Ferndale Golden Eagles" 25 9]
;;     ["Ferndale Golden Eagles" 24 7]
;;     ["Ferndale Golden Eagles" 3 3]
;;     ["Ferndale Golden Eagles" 0 0]]

(->> (d/q '[:find (pull ?t [:team/name]) (sum ?pts) (count-distinct ?p) (avg ?pts)
            :in $ % ?g
            :where
            (actions ?g ?t ?p)
            (poss-pts ?p ?pts)]
          @conn
          rules
          wnba-game-eid)
     (map (fn [[{team :team/name} points possessions ppp]] [team points possessions (-> ppp (* 100) double)]))
     (into [[:team :points :possessions :offensive-rating]]))
;; => [[:team :points :possessions :offensive-rating]
;;     ["Las Vegas Aces" 97 78 124.3589743589744]
;;     ["Seattle Storm" 92 78 117.9487179487179]]

(->> (d/q '[:find (pull ?t [:team/name]) (sum ?off-rebs) (count ?off-rebs) (avg ?off-rebs)
            :in $ % ?g
            :with ?a
            :where
            (actions ?g ?t ?p ?a)
            [?a :shot/rebounder]
            (off-rebs ?a ?off-rebs)]
          @conn
          rules
          wnba-game-eid)
     (map (fn [[{team :team/name} or r or-rate]] [team or r (-> or-rate (* 100) double)]))
     (into [[:team :offrebs :available :offreb%]]))
;; => [[:team :offrebs :rebounds :offreb%]
;;     ["Las Vegas Aces" 5 29 17.24137931034483]
;;     ["Seattle Storm" 11 40 27.5]]

(->> (d/q '[:find (pull ?t [:team/name]) ?player (count-distinct ?a)
            :in $ % ?g
            :where
            (actions ?g ?t ?p ?a)
            [?a :shot/rebounder ?player]
            [?a :shot/off-reb? true]]
          @conn
          rules
          wnba-game-eid)
     (map #(update % 0 :team/name))
     (sort-by last >)
     (sort-by first)
     (into [[:team :player :offrebs]]))
;; => [[:team :player :offrebs]
;;     ["Las Vegas Aces" 22 3]
;;     ["Las Vegas Aces" 41 1]
;;     ["Las Vegas Aces" 12 1]
;;     ["Seattle Storm" 30 3]
;;     ["Seattle Storm" 5 3]
;;     ["Seattle Storm" 24 2]
;;     ["Seattle Storm" 7 1]
;;     ["Seattle Storm" 13 1]
;;     ["Seattle Storm" 31 1]]

(->> (d/q '[:find (pull ?t [:team/name]) (sum ?3fgs) (count ?3fgs) (avg ?3fgs)
            :in $ % ?g
            :with ?a
            :where
            (actions ?g ?t ?p ?a)
            (fga? ?a)
            [?a :shot/value 3]
            (fgs ?a ?3fgs)]
          @conn
          rules
          wnba-game-eid)
     (map (fn [[{team :team/name} fg3s fg3as fg3%]] [team fg3s fg3as (-> fg3% (* 100) double)]))
     (into [[:team :3fg :3fga :3fg%]]))
;; => [[:team :3fg :3fga :3fg%]
;;     ["Las Vegas Aces" 10 24 41.66666666666667]
;;     ["Seattle Storm" 11 26 42.30769230769231]]

(->> (d/q '[:find (pull ?t [:team/name]) (sum ?turnovers) (count-distinct ?p) (avg ?turnovers)
            :in $ % ?g
            :with ?a
            :where
            (actions ?g ?t ?p ?a)
            (tos ?a ?turnovers)]
          @conn
          rules
          wnba-game-eid)
     (map (fn [[{team :team/name} tos nposs to-rate]] [team tos nposs (-> to-rate (* 100) double)]))
     (into [[:team :turnovers :possessions :to%]]))
;; => [[:team :turnovers :possessions :to%]
;;     ["Las Vegas Aces" 12 78 14.4578313253012]
;;     ["Seattle Storm" 9 78 9.89010989010989]]


(->> (d/q '[:find (pull ?t [:team/name]) (avg ?efgs)
            :in $ % ?g
            :with ?a
            :where
            (actions ?g ?t ?p ?a)
            (fga? ?a)
            (efgs ?a ?efgs)]
          @conn
          rules
          wnba-game-eid)
     (map (fn [[{team :team/name} efg-rate]] [team (-> efg-rate (* 100) double)]))
     (into [[:team :efg%]]))
;; => [[:team :efg%]
;;     ["Seattle Storm" 52.142857142857146]
;;     ["Las Vegas Aces" 65.07936507936508]]

(->> (d/q '[:find (pull ?t [:team/name]) ?fts ?fgas ?ft-fgas
            :in $ % ?g
            :where
            [?t :team/name]
            (game-team-fts ?g ?t ?fts)
            (game-team-fgas ?g ?t ?fgas)
            [(/ ?fts ?fgas) ?ft-fgas]]
          @conn
          rules
          wnba-game-eid)
     (map (fn [[{team :team/name} fts fgas ft-fgas]] [team fts fgas (-> ft-fgas (* 100) double)]))
     (into [[:team :fts :fgas :ft/fgas%]]))
;; => [[:team :fts :fgas :ft/fgas%]
;;     ["Las Vegas Aces" 15 63 23.80952380952381]
;;     ["Seattle Storm" 19 70 27.14285714285714]]

