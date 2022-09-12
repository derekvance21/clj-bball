(ns src.core
  (:require [datalevin.core :as d]))

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
                                 :db/doc "the free throw attempts of this action"}
             :shot/distance {:db/valueType :db.type/long
                             :db/cardinality :db.cardinality/one
                             :db.attr/preds 'src.core/valid-shot-distance?
                             :db/doc "the distance from the hoop in feet the shot was attempted from"}
             :possession/action {:db/valueType :db.type/ref
                                 :db/cardinality :db.cardinality/many
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
              :db.entity/preds 'src.core/player-is-unique?}])

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

; gets all action ids for each possession, as collections for each possession
(def possession-query '[:find (pull ?p [:db/id :possession/action])
                        :where
                        [?p :possession/order]])
(d/q possession-query @conn)



; functions for resetting and closing database
(defn clear [] (d/clear conn))
(defn close [] (d/close conn))
