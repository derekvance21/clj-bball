(ns bball.parser)

; what starts a new action?
; - turnover
; - shot
; - bonus
; - technical
; when a new action is started, check for possession change (where team selector doesn't match :possession/team)

(defn append-action
  [parser]
  (cond-> parser
    (:action parser)
    (-> (update-in [:possession :possession/action]
                   (fnil #(conj % (assoc (:action parser) :action/order (-> % count long)))
                         []))
        (dissoc :action))))

(defn append-possession
  [parser]
  (cond-> parser
    (:possession parser)
    (-> (update-in [:game :game/possession]
                   (fnil #(conj % (assoc (:possession parser) :possession/order (-> % count long)))
                         []))
        (dissoc :possession))))

(defn team
  [parser team-keyword]
  (-> parser
      (assoc :team (get-in parser [:teams team-keyword]))
      (dissoc :number)))

(defn number
  [parser number]
  (assoc parser :number number))

(defn action
  [parser action]
  ((ns-resolve (find-ns 'bball.parser) action) parser))

(defn action-form
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
    (possession-change? parser) (-> append-possession
                                    (assoc-in [:possession :possession/team] (:team parser)))))

(defn next-action
  [parser action-type]
  (-> parser
      append-action
      check-possession-change
      (assoc-in [:action :player/number] (:number parser))
      (assoc-in [:action :action/type] action-type)))

(defn turnover
  [parser]
  (next-action parser :action.type/turnover))

(defn bonus
  [parser]
  (next-action parser :action.type/bonus))

(defn technical
  [parser]
  (next-action parser :action.type/technical))

(defn shot
  [parser]
  (next-action parser :action.type/shot))

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
    :always (assoc-in [:action :shot/off-reb?] (not (possession-change? parser)))))

(defn period
  [parser]
  (-> parser
      append-action
      append-possession))

(defn reducer
  [parser command]
  (cond-> parser
    (keyword? command) (team command)
    (number? command) (number command)
    (symbol? command) (action command)
    (list? command) (action-form command)))

(defn parse
  [[init-parser commands]]
  (as-> (reduce reducer init-parser commands) parser
    (assoc-in parser [:game :game/teams] (-> parser :teams vals vec))
    (get parser :game)))
