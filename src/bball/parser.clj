(ns bball.parser
  (:require [instaparse.core :as insta]))

; what commands can start a new possession?
; - shot, where team selector doesn't match current :possession/team
;   - if so:
;   - end the current action,
;   - end the current possession,
;   - set :possession/team to current team selector
;   - always:
;   - set :action/type to shot
;   - set :player/number to player selector
; - turnover, where team selector doesn't match current :possession/team
;   - if so: same as above
;   - always:
;   - set :action/type to turnover
;   - set :player/number to player selector

; what starts a new action?
; - turnover
; - shot
; - bonus
; - technical
; - rebound (modifies current action, then starts new one)

; when above has a team selector that doesn't match current :possession/team, a new possession is started

; list form: (ft 1 2) ; made 1 free throw of 2 attempts

(defn team-selector? [command] (keyword? command))
(defn number-selector? [command] (number? command))
(defn action? [command] (symbol? command))
(defn players-selector? [command] (vector? command))

(defn parser-team
  [parser]
  (:team parser))

(defn team-transact
  [parser]
  (let [team (parser-team parser)]
    [:team/name (get-in parser [:teams team] team)]))

(defn number-transact
  [parser]
  (:number parser))

(defn append-action
  [parser]
  (if (empty? (:action parser))
    parser
    (as-> parser p
      (assoc-in p [:action :action/player] [:player/team+number (team-transact p) (number-transact p)])
      (assoc-in p [:action :action/order] (count (:possession p)))
      (update p :possession (fnil (fn [possession] (conj possession (:action p))) []))
      (assoc p :action {}))))

(append-action {:action {:action/type :action.type/miss-2}
                :team :A
                :number 21
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

(resolve 'turnover)

(defn change-action-type
  [parser type]
  (assoc-in parser [:action :action/type] type))

(defn turnover
  [parser]
  (-> parser
      append-action
      (change-action-type :action.type/turnover)))

(defn miss-3
  [parser]
  (-> parser
      append-action
      (change-action-type :action.type/miss-3)))

(defn make-3
  [parser]
  (-> parser
      append-action
      (change-action-type :action.type/make-3)))

(defn miss-2
  [parser]
  (-> parser
      append-action
      (change-action-type :action.type/miss-2)))

(defn make-2
  [parser]
  (-> parser
      append-action
      (change-action-type :action.type/make-2)))

(defn reb
  [parser]
  (-> parser
      append-action
      (change-action-type :action.type/rebound)))

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
  [[teams & commands]]
  (as-> (reduce game-edn-reducer {:teams teams} commands) parser
    (append-possession parser)))

(defn debug-transform-game-edn
  [[teams commands]]
  (reductions game-edn-reducer {:teams teams} (conj commands 'append-possession)))

(def game-edn '[{:V "Las Vegas Aces"
                 :C "Chicago Sky"}
                [:V 12 miss-3 21 reb make-2
                 :C 10 miss-2 33 reb turnover
                 :V [41 20] in 12 make-2]])

(debug-transform-game-edn game-edn)

(:possessions (transform-game-edn game-edn))

;; ======
;; INSTAPARSER
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

