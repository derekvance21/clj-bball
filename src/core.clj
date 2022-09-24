(ns core
  (:require [datahike.api :as d]
            [instaparse.core :as insta]))

; what ends an action?
; - number
; - reb
; - shot attempt (rim, mid, three, ...)
; - technical
; - turnover

; what ends a possession (and thus also an action)?
; - *new* team
; - period

(defn team-selector? [command] (keyword? command))
(defn number-selector? [command] (number? command))
(defn action? [command] (symbol? command))
(defn players-selector? [command] (vector? command))

(defn append-action
  [parser]
  (if (empty? (:action parser))
    parser
    (as-> parser p
      (assoc-in p [:action :action/player] [:player/team+number (:team p) (:number p)])
      (assoc-in p [:action :action/order] (count (:possession p)))
      (update p :possession (fn [possession] (conj possession (:action p))))
      (assoc p :action {}))))

(append-action {:number 12
                :team :A
                :action {:action/type :action.type/make-3}
                :possession [{:action/type :action.type/rebound
                              :action/player [:player/team+number :A 10]
                              :action/order 0}]
                })

(defn append-possession
  [parser]
  (as-> parser p
      (append-action p)
      (update p :possessions (fn [possessions] (conj possessions (:possession p))))
      (assoc p :possession [])))

(defn apply-team
  [parser team]
  (let [curr-team (:team parser)
        possession-change? (and (not (nil? curr-team))
                                (not= curr-team team))]
    ; TODO: could refactor this into as->, with as-> parser p (if possession-change? p) (-> ...)
    (cond-> parser
      possession-change? append-possession
      true (assoc :team team)
      true (assoc :number nil))))

(apply-team {:team :V
             :number 12
             :possession []
             :possessions []
             :action {:action/type :action.type/make-3}}
            :C)

(apply-team {} :V)


(defn apply-number
  [parser number]
  (-> parser
      append-action
      (assoc :number number)))

(apply-number {:number 10
               :team :C
               :action {:action/type :action.type/miss-3}
               :possession []}
              3)

(defn apply-action
  [parser action]
  ((resolve action) parser))

(defn turnover
  [parser]
  (-> parser
      append-action
      (assoc-in [:action :action/type] :action.type/turnover)))

(defn miss-3
  [parser]
  (-> parser
      append-action
      (assoc-in [:action :action/type] :action.type/miss-3)))

(defn make-3
  [parser]
  (-> parser
      append-action
      (assoc-in [:action :action/type] :action.type/make-3)))

(defn miss-2
  [parser]
  (-> parser
      append-action
      (assoc-in [:action :action/type] :action.type/miss-2)))

(defn make-2
  [parser]
  (-> parser
      append-action
      (assoc-in [:action :action/type] :action.type/make-2)))

(defn reb
  [parser]
  (-> parser
      append-action
      (assoc-in [:action :action/type] :action.type/rebound)))

(defn in
  [parser]
  (-> parser
      (assoc :players (:player parser))))


(defn game-edn-reducer
  [parser command]
  ((cond (team-selector? command) apply-team
         (number-selector? command) apply-number
         (players-selector? command) apply-number
         (action? command) apply-action)
   parser
   command))


(defn transform-game-edn
  [game]
  (as-> (reduce game-edn-reducer {:possession []
                                  :possessions []} game) parser
    (append-possession parser)))

(def game-edn '[:V 12 miss-3 21 reb make-2
                :C 10 miss-2 33 reb turnover
                :V [41 20] in 12 make-2])

(apply-team
 {:team :V
  :number :12
  :action {:action/type :action.type/make-3}
  :possession []
  :possessions []}
 :C)

(:possessions (transform-game-edn game-edn))
;; => [[#:action{:type :action.type/miss-3, :player [:player/team+number :V 12], :order 0}
;;      #:action{:type :action.type/rebound, :player [:player/team+number :V 21], :order 1}
;;      #:action{:type :action.type/make-2, :player [:player/team+number :V 21], :order 2}]
;;     [#:action{:type :action.type/miss-2, :player [:player/team+number :C 10], :order 0}
;;      #:action{:type :action.type/rebound, :player [:player/team+number :C 33], :order 1}
;;      #:action{:type :action.type/turnover, :player [:player/team+number :C 33], :order 2}]
;;     [#:action{:type :action.type/make-2, :player [:player/team+number :V 12], :order 0}]]




;; ======
;; PARSER
;; ======

; comments
; haskell: {- ... -}
; ocaml:   (* ... *)
; c:       /* ... */
; mine?:   {* ... *}
; curly:   {  ...  }
; bracket: [  ...  ]

; should have a way of providing *checks* on evaluation
; like at a period, can write <A 12 S 13> to indicate the score,
; and the evaluator will check this and either error or provide warning if that's not the case

(def parser
  (insta/parser "Game            ::= <Whitespace*> (Token <Whitespace+>)* Token?
                 <Token>         ::= Team | Number | Numbers | Action | <Comment> | Verify
                 Comment         ::= <'['> #'[^\\]]*' <']'>
                 Numbers         ::= <'('> <Whitespace*> (Number <Whitespace>)* Number? <')'>
                 Verify          ::= <'<'> Score <'>'>
                 Score           ::= <Whitespace*> Team <Whitespace+> Number <Whitespace+> Team <Whitespace+> Number <Whitespace*>
                 Number          ::= #'[0-9]+'
                 Team            ::= #'[A-Z]+'
                 Action          ::= #'[a-z]+'
                 Whitespace      ::= #'[\\s,]+'"))

(def game-string "V 12 three miss, 21 reb, rim make
                 C 10 turnover
                 V (41 20 ) in
                 [long stoppage here (this was a comment) ]
                 12 mid make
                 <V 4 C 0>")


(def parsed-game (insta/parse parser game-string :start :Game))

(insta/transform {:Number #(vector :Number (read-string %))} parsed-game)
;; => [:Game
;;     [:Team "V"]
;;     [:Number 12]
;;     [:Action "three"]
;;     [:Action "miss"]
;;     [:Number 21]
;;     [:Action "reb"]
;;     [:Action "rim"]
;;     [:Action "make"]
;;     [:Team "C"]
;;     [:Number 10]
;;     [:Action "turnover"]
;;     [:Team "V"]
;;     [:Numbers [:Number 41] [:Number 20]]
;;     [:Action "in"]
;;     [:Number 12]
;;     [:Action "mid"]
;;     [:Action "make"]
;;     [:Verify [:Score [:Team "V"] [:Number 4] [:Team "C"] [:Number 0]]]]


(defn valid-shot-distance?
  [d]
  (and (>= d 0) (< d 100)))

(def schema [
             ;;action
             {:db/ident :action/type
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "the enumerated type of an action"}
             {:db/ident :action/player
              :db/valueType :db.type/ref
              :db/cardinality :db.cardinality/one
              :db/doc "the player involved in this action"}
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
             {:db/ident :shot/distance
              :db/valueType :db.type/long
              :db/cardinality :db.cardinality/one
              :db/doc "the distance from the hoop in feet the shot was attempted from"}
             
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
                        {:db/ident :action.type/make-2
                         :action/score 2}
                        {:db/ident :action.type/make-3
                         :action/score 3}
                        {:db/ident :action.type/miss-2}
                        {:db/ident :action.type/miss-3}
                        {:db/ident :action.type/bonus}
                        {:db/ident :action.type/technical}])

(def cfg {:store {:backend :file
                  :path "/tmp/datahike-clj-basketball-db"}})

(d/create-database cfg)
(def conn (d/connect cfg))

(d/transact conn schema)

(d/transact conn [{:db/ident :player/team+number
                   :db/valueType :db.type/tuple
                   :db/tupleAttrs [:player/team :player/number]
                   :db/cardinality :db.cardinality/one
                   :db/unique :db.unique/identity}])

(d/transact conn [{:db/ident :action/score
                   :db/valueType :db.type/long
                   :db/cardinality :db.cardinality/one
                   :db/doc "the number of points scored for this action type"}])

(d/transact conn action-type-enums)

(d/schema @conn)

; gets all action types, and their score, defaulted to 0
; note: predicates can't have nested transformations, so [(= (namespace ?ai) "action.type")] fails (without error)
(d/q '[:find (pull ?a [:db/id :db/ident [:action/score :default 0]])
       :where
       [?a :db/ident ?ai]
       [(namespace ?ai) ?ns]
       [(= ?ns "action.type")]]
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
