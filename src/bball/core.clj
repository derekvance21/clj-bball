(ns bball.core
  (:require [datahike.api :as d]
            [bball.parser :refer [parse]]))

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

(def game (-> "games/9-6-2022-Vegas-Seattle.edn" slurp read-string parse))

(d/transact conn [game])

(def points-rules '[[(ft-points ?a ?points)
                     [?a :ft/made ?points]]
                    [(ft-points ?a ?points)
                     [(missing? $ ?a :ft/made)]
                     [(ground 0) ?points]]

                    [(fg-points ?a ?points)
                     [?a :shot/make? true]
                     [?a :shot/value ?points]]
                    [(fg-points ?a ?points)
                     [?a :shot/make? false]
                     [(ground 0) ?points]]
                    [(fg-points ?a ?points)
                     [(missing? $ ?a :shot/make?)]
                     [(ground 0) ?points]]

                    [(points ?a ?points)
                     (ft-points ?a ?ft-points)
                     (fg-points ?a ?fg-points)
                     [(+ ?ft-points ?fg-points) ?points]]])

(d/q '[:find ?team (sum ?points)
       :in $ % [?team ...]
       :with ?a
       :where
       [?p :possession/team ?team]
       [?p :possession/action ?a]
       (points ?a ?points)]
     @conn
     points-rules
     [[:team/name "Las Vegas Aces"] [:team/name "Seattle Storm"]])
;; => [[[:team/name "Las Vegas Aces"] 97] [[:team/name "Seattle Storm"] 92]]

(d/q '[:find ?team ?number (sum ?points)
       :in $ % [?team ...]
       :with ?a
       :where
       [?p :possession/team ?team]
       [?p :possession/action ?a]
       [?a :player/number ?number]
       (points ?a ?points)]
     @conn
     points-rules
     [[:team/name "Las Vegas Aces"] [:team/name "Seattle Storm"]])
;; => [[[:team/name "Seattle Storm"] 5 8]
;;     [[:team/name "Seattle Storm"] 20 0]
;;     [[:team/name "Seattle Storm"] 10 8]
;;     [[:team/name "Seattle Storm"] 31 2]
;;     [[:team/name "Las Vegas Aces"] 22 23]
;;     [[:team/name "Las Vegas Aces"] 10 15]
;;     [[:team/name "Las Vegas Aces"] 41 4]
;;     [[:team/name "Seattle Storm"] 24 29]
;;     [[:team/name "Seattle Storm"] 7 3]
;;     [[:team/name "Seattle Storm"] 30 42]
;;     [[:team/name "Seattle Storm"] 13 0]
;;     [[:team/name "Las Vegas Aces"] 2 6]
;;     [[:team/name "Las Vegas Aces"] 5 0]
;;     [[:team/name "Las Vegas Aces"] 12 31]
;;     [[:team/name "Las Vegas Aces"] 0 18]]

