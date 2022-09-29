(ns bball.core
  (:require [datahike.api :as d]
            [bball.parser :as p]))

(defn valid-shot-distance?
  [d]
  (and (>= d 0) (< d 100)))

(def schema [;; action
             {:db/ident :action/type
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "the enumerated type of an action"}
             {:db/ident :action/player
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "the player involved in this action"}
             ; use :player/number here for player performing action
             {:db/ident :action/order
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the order this action happened in its possession"}
             {:db/ident :action/ft-made
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number of made free throws in this action"}
             {:db/ident :action/ft-attempted
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number of attempted free throws in this action"}

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
             {:db/ident :possession/lineup
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc "the lineup in at the end of this possession"}
             {:db/ident :possession/points
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number of points scored in this possession"}
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

             ;; player
             {:db/ident :player/number
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the number jersey the player wears"}
             {:db/ident :player/team
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "the team the player plays for"}

             ;; team
             {:db/ident :team/name
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/unique :db.unique/identity
              :db/doc "the name of the team"}])

(def action-type-enums [{:db/ident :action.type/turnover}
                        {:db/ident :action.type/def-rebound} ; TODO: potentially make rebounds ref's of a shot attempt action
                        {:db/ident :action.type/off-rebound} ; TODO: potentially make rebounds ref's of a shot attempt action
                        {:db/ident :action.type/make-2}
                        {:db/ident :action.type/make-3}
                        {:db/ident :action.type/miss-2}
                        {:db/ident :action.type/miss-3}
                        {:db/ident :action.type/bonus}
                        {:db/ident :action.type/technical}
                        {:db/ident :action.type/shot
                         :db/doc "a shot attempted in this action"}])

(def cfg {:store {:backend :mem
                  :id "clj-basketball-db"}})

(d/create-database cfg)
(def conn (d/connect cfg))

(d/transact conn schema)

(d/transact conn [{:db/ident :player/team+number
                   :db/valueType :db.type/tuple
                   :db/tupleAttrs [:player/team :player/number]
                   :db/cardinality :db.cardinality/one
                   :db/unique :db.unique/identity}])

(d/transact conn action-type-enums)

(d/schema @conn)

; gets all action types
; note: predicates can't have nested transformations, so [(= (namespace ?ai) "action.type")] fails (without error)
(d/q '[:find (pull ?a [:db/id :db/ident])
       :where
       [?a :db/ident ?ai]
       [(namespace ?ai) ?ns]
       [(= ?ns "action.type")]]
     @conn)

(d/transact conn [{:db/id -1
                   :player/number 12
                   :player/team {:team/name "Anacortes Seahawks"}}
                  {:action/type :action.type/shot
                   :action/order 0
                   :action/player -1}])

(d/transact conn [{:db/id -1
                   :player/number 20
                   :player/team {:team/name "Anacortes Seahawks"}}
                  {:action/type :action.type/turnover
                   :action/order 0
                   :action/player -1}])

(d/q '[:find (pull ?a [* {:action/player [* {:player/team [*]}]}])
       :where
       [?a :action/type]]
     @conn)

(d/transact conn [{:possession/team {:team/name "Blaine Borderites"}
                   :possession/order 0
                   :possession/action [{:action/player {:team/name "Blaine Borderites"}
                                        :action/type :action.type/turnover}]}])

(d/transact conn [{:possession/team {:team/name "Sehome Mariners"}
                   :possession/order 0
                   :possession/action [{:action/player [:team/name "Sehome Mariners"]
                                        :action/type :action.type/turnover}]}])

(d/q '[:find (pull ?t [*])
       :where
       [?t :team/name]]
     @conn)

(def game [{:db/id -1
            :team/name "Blaine Borderites"}
           {:db/id -2
            :team/name "Bellingham Bayhawks"}
           {:game/minutes 32
            :game/possession [{:possession/team -1
                               :possession/order 0
                               :possession/points 2
                               :possession/action [{:action/order 1
                                                    :action/type :action.type/miss-3
                                                    :action/player {:player/number 10
                                                                    :player/team -1}}
                                                   {:action/order 2
                                                    :action/type :action.type/make-2
                                                    :action/player {:player/number 22
                                                                    :player/team -1}}]}
                              {:possession/team -2
                               :possession/order 1
                               :possession/points 0
                               :possession/action [{:action/order 1
                                                    :action/type :action.type/miss-2
                                                    :action/player {:player/number 33
                                                                    :player/team -2}}]}]}])

(d/transact! conn game)

(def edn-game '[{:V "Las Vegas Aces"
                 :S "Seattle Storm"}
                [:V 12 three miss 22 reb two make
                 :S 30 three miss reb 24 turnover
                 :V 41 two miss :V reb 22 two miss (ft 1 2)
                 :S 30 reb 10 three make
                 period]])
(def game-transact-obj (:game (p/transform-game-edn edn-game)))

(d/transact conn [game-transact-obj])

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

; gets all players, and their team
(def player-query '[:find (pull ?p [:db/id :player/number]) (pull ?t [:db/id :team/name])
                    :where
                    [?p :player/team ?t]])
(d/q player-query @conn)

; get all teams
(d/q '[:find (pull ?t [*])
       :where
       [?t :team/name]]
     @conn)

(def three-point-attempt-rules '[[(three-point-attempt ?a)
                                  [?a :action/type :action.type/make-3]]
                                 [(three-point-attempt ?a)
                                  [?a :action/type :action.type/miss-3]]])

; gets all actions that were a three point attempt, and their possession id
(def three-point-action-query '[:find ?p (pull ?a [:db/id :action/order :action/type :action/player :action/free-throw])
                                :in $ %
                                :where
                                [?p :possession/action ?a]
                                (three-point-attempt ?a)])
(d/q three-point-action-query @conn three-point-attempt-rules)

; get all possessions
(d/q '[:find (pull ?p [:db/id {:possession/team [:team/name]}])
       :where
       [?p :possession/team]]
     @conn)

; gets all action ids for each possession, as collections for each possession
(d/q '[:find (pull ?p [:db/id
                       {:possession/team [:db/id :team/name]}
                       {:possession/action [* {:action/type [:db/ident]}]}])
       :where
       [?p :possession/order]]
     @conn)

; add an "and-one" to the specified action
(d/transact! conn [{:db/id 16
                    :action/ft-1 true}])

(d/transact! conn [{:possession/order 3
                    :possession/points 2
                    :possession/team [:team/name "Blaine Borderites"]
                    :possession/action [{:action/order 1
                                         :action/player {:player/number 12
                                                         :player/team [:team/name "Blaine Borderites"]}
                                         :action/type :action.type/miss-3
                                         :action/ft-attempted 3
                                         :action/ft-made 2}]}])

(d/transact! conn [{:possession/order 4
                    :possession/points 2
                    :possession/team [:team/name "Blaine Borderites"]
                    :possession/action [{:action/order 1
                                         :action/player {:player/number 12
                                                         :player/team [:team/name "Blaine Borderites"]}
                                         :action/type :action.type/make-2
                                         :action/ft-attempted 1
                                         :action/ft-made 1}]}])

(defn team-name->eid
  [team]
  (d/q '[:find ?e .
         :in $ ?team
         :where
         [?e :team/name ?team]] @conn team))

(defn ft-points
  [ft]
  (if ft 1 0))

(defn action-type->points
  [type]
  (case type
    :action.type/make-2 2
    :action.type/make-3 3
    0))

(defn action->points
  [type ft-1 ft-2 ft-3]
  (+ (action-type->points type)
     (ft-points ft-1)
     (ft-points ft-2)
     (ft-points ft-3)))

(d/q '[:find ?points
       :in $ ?t
       :where
       [?p :possession/team ?t]
       [?p :possession/action ?a]
       [?a :action/type ?at]
       [?at :db/ident ?type]
       [(get-else $ ?a :action/ft-1 false) ?ft-1]
       [(get-else $ ?a :action/ft-2 false) ?ft-2]
       [(get-else $ ?a :action/ft-3 false) ?ft-3]
       [(core/action->points ?type ?ft-1 ?ft-2 ?ft-3) ?points]]
     @conn (team-name->eid "Blaine Borderites"))

; get the number of points from FGs, FTs, and total possessions
(d/q '[:find [(sum ?points) (sum ?ft-points) (count-distinct ?p)]
       :in $ ?t
       :where
       [?p :possession/team ?t]
       [?p :possession/action ?a]
       [?a :action/type ?at]
       [(get-else $ ?at :action/score 0) ?points]
       [(get-else $ ?a :action/ft-made 0) ?ft-points]]
     @conn (team-name->eid "Bellingham Bayhawks"))

(defn team-offensive-rating
  [team]
  (let [[pts ftpts npos] (d/q '[:find [(sum ?points) (sum ?ft-points) (count-distinct ?p)]
                                :in $ ?t
                                :where
                                [?p :possession/team ?t]
                                [?p :possession/action ?a]
                                [?a :action/type ?at]
                                [(get-else $ ?at :action/score 0) ?points]
                                [(get-else $ ?a :action/ft-made 0) ?ft-points]]
                              @conn (team-name->eid team))]
    (/ (+ pts ftpts) npos)))

(team-offensive-rating "Blaine Borderites")

; functions for resetting and closing database
(defn clear [] (d/clear conn))
(defn close [] (d/close conn))
