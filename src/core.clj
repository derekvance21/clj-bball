(ns core
  (:require [datalevin.core :as d]
            [instaparse.core :as insta]))

(defn valid-shot-distance?
  [d]
  (and (>= d 0) (< d 100)))

(defn player-is-unique?
  [db eid]
  (let [p (d/pull db [:player/team :player/number] eid)]
    false ;; TODO: ensure player is unique here with d/q
    ))

(def schema {:action/type {:db/valueType :db.type/ref
                           :db/cardinality :db.cardinality/one
                           :db/doc "the enumerated type of an action"}
             :action/player {:db/valueType :db.type/ref
                             :db/cardinality :db.cardinality/one
                             :db/doc "the player involved in this action"}
             :action/order {:db/valueType :db.type/long
                            :db/cardinality :db.cardinality/one
                            :db/doc "the order this action happened in its possession"}
             :action/free-throw {:db/valueType :db.type/boolean
                                 :db/cardinality :db.cardinality/many
                                 :schema/see-instead :action/ft-made
                                 :db/doc "DEPRECATED, SEE :action/ft-made and :action/ft-attempted | the free throw attempts of this action"}
             :action/ft-1 {:db/valueType :db.type/boolean
                           :db/cardinality :db.cardinality/one
                           :schema/see-instead :action/ft-made
                           :db/doc "DEPRECATED, SEE :action/ft-made and :action/ft-attempted | the result of the first free throw attempt"}
             :action/ft-2 {:db/valueType :db.type/boolean
                           :db/cardinality :db.cardinality/one
                           :schema/see-instead :action/ft-made
                           :db/doc "DEPRECATED, SEE :action/ft-made and :action/ft-attempted | the result of the second free throw attempt"}
             :action/ft-3 {:db/valueType :db.type/boolean
                           :db/cardinality :db.cardinality/one
                           :schema/see-instead :action/ft-made
                           :db/doc "DEPRECATED, SEE :action/ft-made and :action/ft-attempted | the result of the third free throw attempt"}
             :action/ft-made {:db/valueType :db.type/long
                              :db/cardinality :db.cardinality/one
                              :db/doc "the number of made free throws in this action"}
             :action/ft-attempted {:db/valueType :db.type/long
                                   :db/cardinality :db.cardinality/one
                                   :db/doc "the number of attempted free throws in this action"}
             :shot/distance {:db/valueType :db.type/long
                             :db/cardinality :db.cardinality/one
                             :db.attr/preds 'core/valid-shot-distance?
                             :db/doc "the distance from the hoop in feet the shot was attempted from"}
             :possession/action {:db/valueType :db.type/ref
                                 :db/cardinality :db.cardinality/many
                                 :db/isComponent true
                                 :db/doc "the actions of this possession"}
             :possession/lineup {:db/valueType :db.type/ref
                                 :db/cardinality :db.cardinality/many
                                 :db/doc "the lineup in at the end of this possession"}
             :possession/points {:db/valueType :db.type/long
                                 :db/cardinality :db.cardinality/one
                                 :db/doc "the number of points scored in this possession"
                                 ;; either needs a :db.entity/preds validator,
                                 ;; or just use a rule to get the points for a possession by summing the action scores
                                 }
             :possession/team {:db/valueType :db.type/ref
                               :db/cardinality :db.cardinality/one
                               :db/doc "the team with the ball on this possession"}
             :possession/order {:db/valueType :db.type/long
                                :db/cardinality :db.cardinality/one
                                :db/doc "the order this possession happened in its game"}
             :game/possession {:db/valueType :db.type/ref
                               :db/cardinality :db.cardinality/many
                               :db/isComponent true
                               :db/doc "the possessions of this game"}
             :game/minutes {:db/valueType :db.type/long
                            :db/cardinality :db.cardinality/one
                            :db/doc "the number of minutes this game lasted"}
             :player/number {:db/valueType :db.type/long
                             :db/cardinality :db.cardinality/one
                             :db/doc "the number jersey the player wears"}
             :player/team {:db/valueType :db.type/ref
                           :db/cardinality :db.cardinality/one
                           :db/doc "the team the player plays for"}
             :team/name {:db/valueType :db.type/string
                         :db/cardinality :db.cardinality/one
                         :db/unique :db.unique/identity}})

(def action-type-enums [{:db/ident :action.type/turnover}
                        {:db/ident :action.type/rebound}
                        {:db/ident :action.type/make-2
                         :action/score 2}
                        {:db/ident :action.type/make-3
                         :action/score 3}
                        {:db/ident :action.type/miss-2}
                        {:db/ident :action.type/miss-3}
                        {:db/ident :action.type/bonus}
                        {:db/ident :action.type/technical}])

; this is unsupported functionality in datalevin
(def guards [{:db/ident :player/guard
              :db.entity/attrs [:player/team :player/number]
              :db.entity/preds 'core/player-is-unique?}])

(def conn (d/get-conn "/tmp/datalevin/clj-basketball-db"))

(d/update-schema conn schema)

(d/transact! conn action-type-enums)
; (d/transact! conn guards) ;; :db/ensure and guards do not exist in datalevin! They do in datahike 

; gets all action types, and their score, defaulted to 0
(d/q '[:find (pull ?a [:db/id :db/ident [:action/score :default 0]])
       :where
       [?a :db/ident]]
     @conn)

(def game [{:db/id -1
            :team/name "Blaine Borderites"}
           {:db/id -2
            :team/name "Bellingham Bayhawks"}
           {:game/minutes 32
            :game/possession [{:possession/team -1
                               :possession/order 1
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
