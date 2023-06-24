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


   ;; action/type's
   {:db/ident :action.type/turnover}

   {:db/ident :action.type/bonus}

   {:db/ident :action.type/technical}

   {:db/ident :action.type/shot}


   ;; free throw
   {:db/ident :ft/made
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "the number of made free throws in this action"}

   {:db/ident :ft/attempted
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "the number of attempted free throws in this action"}

   {:db/ident :ft/results
    :db/valueType :db.type/tuple
    :db/tupleType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "the miss/make results of the free throw action"}


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

   {:db/ident :shot/foul?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "whether or not there was a shooting foul on the shot attempt"}

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


   ;; offense/defense
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

   ;; DEPRECATED - see :game/home-team and :game/away-team
   {:db/ident :game/teams
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "the teams playing in this game"}

   ;; :game/teams makes it annoying to query for the teams in the game
   ;; with this, one query result row can have the game and both teams
   {:db/ident :game/home-team
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "the home team in the game. This does not mean the team was playing at their home gym, however."}

   {:db/ident :game/away-team
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "the home team in the game. This does not mean the team was playing at their home gym, however."}

   {:db/ident :game/neutral-site?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "whether or not the home team was playing in their own gym"}

   {:db/ident :game/location
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "the name of the place where the game took place. ex: Yakima Valley Sundome (Yakima, WA)"}

   {:db/ident :game/datetime
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "the datetime this game started"}

   {:db/ident :game/home-team+away-team+datetime
    :db/valueType :db.type/tuple
    :db/tupleAttrs [:game/home-team :game/away-team :game/datetime]
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "unique game is a tuple of the teams playing (both refs) and a time"}


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
                                     :db.type/ref (not= "type" (name ident)) ;; to not include valueType for :action/type
                                     :db.type/tuple (contains? sch :db/tupleAttrs) ;; in case I use composite tuples later, which datascript supports
                                     false)
                               :db/valueType)])]))
       (into {})))
