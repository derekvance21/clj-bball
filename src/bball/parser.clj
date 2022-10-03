(ns bball.parser)

; what starts a new action?
; - turnover
; - shot
; - bonus
; - technical

; when a new action is started, check for possession change (where team selector doesn't match :possession/team)

; todo: don't have the parser have its own :action and :parser,
; just update the last element as you go. Then, when adding an
; action or possession, you can set the :action/order at the start
; complication? - there's an empty? check on :action, which wouldn't
; jive with the :action/order setting at the beginning

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

(defn apply-team
  [parser team-keyword]
  (let [team (get-in parser [:teams team-keyword])]
    (-> parser
        (update-in [:possession :possession/team] (fnil identity team))
        (assoc :team team)
        (assoc :number nil))))

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
      (assoc-in [:action :ft/made] made)
      (assoc-in [:action :ft/attempted] attempted)))

(defn possession-change?
  [parser]
  (not= (get-in parser [:possession :possession/team])
        (:team parser)))

(defn check-possession-change
  [parser]
  (cond-> parser
    (possession-change? parser) append-possession))

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

(defn period
  [parser]
  (-> parser
      append-action
      append-possession))

(defn reducer
  [parser command]
  (cond (keyword? command) (apply-team parser command)
        (number? command) (apply-number parser command)
        (symbol? command) (apply-action parser command)
        (list? command) (apply-action-call parser command)))

(defn parse
  [[init-parser commands]]
  (-> init-parser
      ((partial reduce reducer) commands)
      :game))
