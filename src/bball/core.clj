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
              :db/valueType :db.type/ref
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

(parse '[{:V {:team/name "Las Vegas Aces"}
          :S {:team/name "Seattle Storm"}}
         [:V 12 three miss 22 reb two make
          :S 30 three miss reb 24 turnover
          :V 41 two miss :V reb 22 two miss (ft 1 2)
          :S 30 reb 10 three make
          period]])

(d/transact conn [(parse '[{:V {:team/name "Las Vegas Aces"}
                            :S {:team/name "Seattle Storm"}}
                           [:V 12 three miss 22 reb two make
                            :S 30 three miss reb 24 turnover
                            :V 41 two miss :V reb 22 two miss (ft 1 2)
                            :S 30 reb 10 three make
                            period]])])

(d/q '[:find (sum ?points) .
       :in $ % ?team
       :with ?a
       :where
       [?p :possession/team ?team]
       [?p :possession/action ?a]
       (ft-points ?a ?ft-points)
       (fg-points ?a ?fg-points)
       [(+ ?ft-points ?fg-points) ?points]]
     @conn
     '[[(ft-points ?a ?points)
        [?a :action/ft-made ?points]]
       [(ft-points ?a ?points)
        [(ground 0) ?points]]
       [(fg-points ?a ?points)
        [?a :shot/make? true]
        [?a :shot/value ?points]]
       [(fg-points ?a ?points)
        [(ground 0) ?points]]]
     [:team/name "Seattle Storm"])
