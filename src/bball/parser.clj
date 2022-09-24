(ns bball.parser
  (:require [instaparse.core :as insta]))

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
      (update p :possession (fnil (fn [possession] (conj possession (:action p))) []))
      (assoc p :action {}))))

(append-action {:action {:action/type :action.type/miss-2
                         :action/player [:player/team+number :A 12]}
                :possession [{:action/type :action.type/rebound
                              :action/player [:player/team+number :A 30]}]})

(defn append-possession
  [parser]
  (as-> parser p
    (append-action p)
    (update p :possessions (fnil (fn [possessions] (conj possessions (:possession p))) []))
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

(append-possession {:team :A
                    :number 12
                    :action {:action/type :action.type/make-2}})

(defn apply-number
  [parser number]
  (-> parser
      append-action
      (assoc :number number)))

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

