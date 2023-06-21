(ns bball.schema)


(def schema
  [;; action
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
   {:db/ident :shot/angle
    :db/valueType :db.type/float
    :db/cardinality :db.cardinality/one
    :db/doc "the angle in turns from the center of the court the shot was attempted from"}
   {:db/ident :shot/value
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "the value of the shot attempted, 2 or 3"}
   {:db/ident :shot/make?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "whether or not the shot was made"}
   {:db/ident :block/player
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "the number player of the blocker of the shot attempt"}

   ;; rebound
   {:db/ident :rebound/player
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "the number player of the rebounder"}
   {:db/ident :rebound/off?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "whether or not the rebound was an offensive rebound"}
   {:db/ident :rebound/team?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "whether or not the rebound was a team offensive rebound"}

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
   {:db/ident :steal/player
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "the number player of the stealer of the turnover"}
   {:db/ident :charge/player
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "the number player of the charge taker for the turnover"}

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
   {:db/ident :possession/period
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "the period of the game this possession happened in"}

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
   ;; TODO - why is this named plurally? Also, might want to refactor into :game/team1 :game/team2. Or even a tuple
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


(defn datomic->datascript-schema
  [schema]
  (->> schema
       (remove #(= "action.type" (namespace (:db/ident %)))) ;; removes enums
       (map
        (fn [{:db/keys [ident valueType] :as sch}]
          [ident
           (select-keys sch [:db/cardinality :db/unique :db/index :db/tupleAttrs :db/isComponent :db/doc
                             (when (case valueType
                                     :db.type/ref (not= "type" (name ident))
                                     :db.type/tuple (contains? sch :db/tupleAttrs) ;; TODO - probably no need to do this - just remove non-refs
                                     false)
                               :db/valueType)])]))
       (into {})))