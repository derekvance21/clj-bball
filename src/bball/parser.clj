(ns bball.parser)

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
(defn action-call? [command] (list? command))

(defn append-action
  [parser]
  (if (empty? (:action parser))
    parser
    (-> parser
        (update-in [:possession :possession/action]
                   (fnil (fn [actions]
                           (conj actions
                                 (assoc (:action parser)
                                        :action/order
                                        (-> actions count long))))
                         []))
        (assoc :action {}))))

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
                                                             (-> possessions count long))))
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
  (assoc parser :number number))

(defn apply-action
  [parser action]
  ((ns-resolve (find-ns 'bball.parser) action) parser))

(defn apply-action-call
  [parser [fn-symbol & args]]
    (apply (ns-resolve (find-ns 'bball.parser) fn-symbol)
           parser
           args))

(defn ft
  [parser made attempted]
  (-> parser
      (assoc-in [:action :action/ft-made] made)
      (assoc-in [:action :action/ft-attempted] attempted)))

(apply-action-call {:team :S} '(ft 1 2))

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

(defn next-action
  [parser]
  (-> parser
      append-action
      check-possession-change
      (assoc-in [:action :player/number] (:number parser))))

(defn turnover
  [parser]
  (-> parser
      next-action
      (assoc-in [:action :action/type] :action.type/turnover)))

(defn shot
  [parser]
  (-> parser
      next-action
      (assoc-in [:action :action/type] :action.type/shot)))

(defn three
  [parser]
  (-> parser
      shot
      (assoc-in [:action :shot/value] 3)))

(defn two
  [parser]
  (-> parser
      shot
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
    (:number parser) (assoc-in [:action :shot/rebounder] (:number parser))
    :always (assoc-in [:action :shot/off-reb?] (= (:team parser)
                                                  (get-in parser [:possession :possession/team])))))

(defn in
  [parser]
  (assoc parser :players (:player parser)))

(defn period
  [parser]
  (-> parser
      append-action
      append-possession))

(defn game-edn-reducer
  [parser command]
  (let [reducer (cond (team-selector? command) apply-team
                      (number-selector? command) apply-number
                      (players-selector? command) apply-number
                      (action? command) apply-action
                      (action-call? command) apply-action-call)]
    (reducer parser command)))

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

(transform-game-edn '[{:V "Vegas"
                       :S "Seattle"}
                      [:V 10 two miss (ft 1 2)
                       period]])
