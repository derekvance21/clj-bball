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

(defn number-transact
  [parser]
  (:number parser))

(defn append-action
  [parser]
  (let [action (:action parser)]
    (if (empty? action)
      parser
      (-> parser
          (update-in [:possession :possession/action]
                     (fnil (fn [actions]
                             (conj actions
                                   (assoc action
                                          :action/order
                                          (count actions))))
                           []))
          (assoc :action {})))))

(append-action {:possession {:possession/order 3
                             :possession/team {:team/name "Seattle Storm"}
                             :possession/action [{:action/type :action.type/rebound
                                                  :player/number 23
                                                  :action/order 0}]}
                :action {:action/type :action.type/turnover
                         :player/number 30}})

(defn append-possession
  [parser]
  (-> parser
    (update-in [:game :game/possession] (fnil (fn [possessions]
                                                (conj possessions
                                                      (assoc (:possession parser)
                                                             :possession/order
                                                             (count possessions))))
                                              []))
    (assoc :possession {:possession/team (:team parser)})
    (assoc :action {})))

(append-possession {:teams {:V "Las Vegas Aces"
                            :S "Seattle Storm"}
                    :team {:team/name "Las Vegas Aces"}
                    :possession {:possession/team {:team/name "Seattle Storm"}
                                 :possession/action [{:action/type :action.type/turnover
                                                      :player/number 30}]}})

(defn apply-team
  [parser team-keyword]
  (let [team {:team/name (get-in parser [:teams team-keyword])}]
    (-> parser
        (update-in [:possession :possession/team] (fnil identity team))
        (assoc :team team)
        (assoc :number nil))))

(apply-team {:teams {:S "Seattle Storm"
                     :V "Las Vegas Aces"}}
            :S)

(defn apply-number
  [parser number]
  (-> parser
      (assoc :number number)))

(defn apply-action
  [parser action]
  ((ns-resolve *ns* action) parser))

(defn possession-change?
  [parser]
  (not= (get-in parser [:possession :possession/team])
        (:team parser)))

(defn check-possession-change
  [parser]
  (cond-> parser
    (possession-change? parser) append-possession))

(check-possession-change {:teams {:V "Las Vegas Aces"
                                  :S "Seattle Storm"}
                          :team {:team/name "Seattle Storm"}
                          :possession {:possession/team {:team/name "Las Vegas Aces"}}})

(defn turnover
  [parser]
  (-> parser
    append-action
    check-possession-change
    (assoc-in [:action :player/number] (:number parser))
    (assoc-in [:action :action/type] :action.type/turnover)))

(defn three
  [parser]
  (-> parser
      append-action
      check-possession-change
      (assoc-in [:action :player/number] (:number parser))
      (assoc-in [:action :action/type] :action.type/shot)
      (assoc-in [:action :shot/value] 3)))

(defn two
  [parser]
  (-> parser
      append-action
      check-possession-change
      (assoc-in [:action :player/number] (:number parser))
      (assoc-in [:action :action/type] :action.type/shot)
      (assoc-in [:action :shot/value] 2)))

(defn make
  [parser]
  (assoc-in parser [:action :shot/make?] true))

(defn miss
  [parser]
  (assoc-in parser [:action :shot/make?] false))

(defn reb
  [parser]
  (cond-> parser
      true (assoc-in [:action :shot/off-reb?] (= (:team parser)
                                            (get-in parser [:possession :possession/team])))
      (:number parser) (assoc-in [:action :shot/rebounder] (:number parser))))

(defn in
  [parser]
  (-> parser
      (assoc :players (:player parser))))

; TODO: end of period should end possession (use for end of game, too)
(defn period
  [parser]
  (-> parser
      append-action
      append-possession))

(defn game-edn-reducer
  [parser command]
  ((cond (team-selector? command) apply-team
         (number-selector? command) apply-number
         (players-selector? command) apply-number
         (action? command) apply-action)
   parser
   command))

(defn transform-game-edn
  [[teams commands]]
  (reduce game-edn-reducer {:teams teams} commands))

(transform-game-edn '[{:V "Vegas"
                       :S "Seattle"}
                      [:V 12 three miss 22 reb two make
                       :S 30 three miss reb 24 turnover
                       :V 41 two miss :V reb 22 two miss
                       :S 30 reb 10 three make
                       period]])
;; => {:teams {:V "Vegas", :S "Seattle"},
;;     :possession #:possession{:team #:team{:name "Seattle"}},
;;     :team #:team{:name "Seattle"},
;;     :number 10,
;;     :action {},
;;     :game
;;     #:game{:possession
;;            [#:possession{:team #:team{:name "Vegas"},
;;                          :action
;;                          [{:player/number 12,
;;                            :action/type :action.type/shot,
;;                            :shot/value 3,
;;                            :shot/make? false,
;;                            :shot/off-reb? true,
;;                            :shot/rebounder 22,
;;                            :action/order 0}
;;                           {:player/number 22,
;;                            :action/type :action.type/shot,
;;                            :shot/value 2,
;;                            :shot/make? true,
;;                            :action/order 1}],
;;                          :order 0}
;;             #:possession{:team #:team{:name "Seattle"},
;;                          :action
;;                          [{:player/number 30,
;;                            :action/type :action.type/shot,
;;                            :shot/value 3,
;;                            :shot/make? false,
;;                            :shot/off-reb? true,
;;                            :shot/rebounder 30,
;;                            :action/order 0}
;;                           {:player/number 24, :action/type :action.type/turnover, :action/order 1}],
;;                          :order 1}
;;             #:possession{:team #:team{:name "Vegas"},
;;                          :action
;;                          [{:player/number 41,
;;                            :action/type :action.type/shot,
;;                            :shot/value 2,
;;                            :shot/make? false,
;;                            :shot/off-reb? true,
;;                            :action/order 0}
;;                           {:player/number 22,
;;                            :action/type :action.type/shot,
;;                            :shot/value 2,
;;                            :shot/make? false,
;;                            :shot/off-reb? false,
;;                            :shot/rebounder 30,
;;                            :action/order 1}],
;;                          :order 2}
;;             #:possession{:team #:team{:name "Seattle"},
;;                          :action
;;                          [{:player/number 10,
;;                            :action/type :action.type/shot,
;;                            :shot/value 3,
;;                            :shot/make? true,
;;                            :action/order 0}],
;;                          :order 3}]}}

;; => {:teams {:V "Vegas", :S "Seattle"},
;;     :possession #:possession{:team #:team{:name "Seattle"}},
;;     :team #:team{:name "Seattle"},
;;     :number 10,
;;     :action {},
;;     :game
;;     #:game{:possession
;;            [#:possession{:team #:team{:name "Vegas"},
;;                          :action
;;                          [{:player/number 12,
;;                            :action/type :action.type/shot,
;;                            :shot/value 3,
;;                            :shot/make? false,
;;                            :shot/off-reb? true,
;;                            :shot/rebounder 22,
;;                            :action/order 0}
;;                           {:player/number 22,
;;                            :action/type :action.type/shot,
;;                            :shot/value 2,
;;                            :shot/make? true,
;;                            :action/order 1}],
;;                          :order 0}
;;             #:possession{:team #:team{:name "Seattle"},
;;                          :action
;;                          [{:player/number 30,
;;                            :action/type :action.type/shot,
;;                            :shot/value 3,
;;                            :shot/make? false,
;;                            :shot/off-reb? true,
;;                            :shot/rebounder 30,
;;                            :action/order 0}
;;                           {:player/number 24, :action/type :action.type/turnover, :action/order 1}],
;;                          :order 1}
;;             #:possession{:team #:team{:name "Vegas"},
;;                          :action
;;                          [{:player/number 41,
;;                            :action/type :action.type/shot,
;;                            :shot/value 2,
;;                            :shot/make? false,
;;                            :shot/off-reb? true,
;;                            :action/order 0}
;;                           {:player/number 22,
;;                            :action/type :action.type/shot,
;;                            :shot/value 2,
;;                            :shot/make? false,
;;                            :shot/off-reb? false,
;;                            :action/order 1}],
;;                          :order 2}
;;             #:possession{:team #:team{:name "Seattle"},
;;                          :action
;;                          [{:player/number 10,
;;                            :action/type :action.type/shot,
;;                            :shot/value 3,
;;                            :shot/make? true,
;;                            :action/order 0}],
;;                          :order 3}]}}


(defn debug-transform-game-edn
  [[teams commands]]
  (reductions game-edn-reducer {:teams teams} commands))

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

