(ns bball.db)

(def schema [;; action
             {:db/ident :action/type
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "the enumerated type of an action"}
             {:db/ident :action/order
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the order this action happened in its possession"}
             {:db/ident :action/player
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number of the player performing the action"}
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

             {:db/ident :offense/players
              :db/valueType :db.type/tuple
              :db/tupleTypes [:db.type/long :db.type/long :db.type/long :db.type/long :db.type/long]
              :db/cardinality :db.cardinality/one
              :db/doc "the numbers of the offensive players on the floor"}
             {:db/ident :defense/players
              :db/valueType :db.type/tuple
              :db/tupleTypes [:db.type/long :db.type/long :db.type/long :db.type/long :db.type/long]
              :db/cardinality :db.cardinality/one
              :db/doc "the numbers of the defensive players on the floor"}

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

(def dev-config {:server-type :dev-local
                 :storage-dir :mem
                 :system "ci"})

(def test-config {:store {:backend :mem :id "testdb"}
                  :initial-tx schema})
