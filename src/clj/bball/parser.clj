(ns bball.parser)

; what starts a new action?
; - turnover
; - shot
; - bonus
; - technical
; when a new action is started, check for possession change (where team selector doesn't match :possession/team)

(defn raise
  [parser msg]
  (throw (Exception. (str msg "\nparser: " parser))))

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
  [parser team]
  (-> parser
      (assoc :team team)
      (dissoc :number)))

(defn number
  [parser number]
  (assoc parser :number number))

(defn in
  [parser & numbers]
  (update-in parser [:players (:team parser)] (fnil #(into (if (= 5 (count numbers)) #{} %)
                                       numbers)
                                #{})))

(defn out
  [parser & numbers]
  (update-in parser [:players (:team parser)] (fnil #(->> % (remove (set numbers)) set) #{})))

(defn ft
  [parser & args]
  (let [number-form? (and (= 2 (count args))
                          (every? number? args))
        made (if number-form?
               (-> args first long)
               (->> args
                    (filter #(= % 'make))
                    count
                    long))
        attempted (if number-form?
                    (-> args second long)
                    (-> args count long))]
    (-> parser
        (assoc-in [:action :ft/made] made)
        (assoc-in [:action :ft/attempted] attempted))))

(defn possession-change?
  [parser]
  (not= (get-in parser [:possession :possession/team])
        (get-in parser [:teams (:team parser)])))

(defn check-possession-change
  [parser]
  (cond-> parser
    (possession-change? parser) (-> append-possession
                                    (assoc-in [:possession :possession/team] (get-in parser [:teams (:team parser)])))))

(defn next-action
  [parser action-type]
  (let [offense (get-in parser [:players (:team parser)])
        defense (get-in parser [:players (->> (:teams parser) keys (remove #{(:team parser)}) first)])]
    (-> parser
        append-action
        check-possession-change
        (assoc-in [:action :action/player] (:number parser))

        (assoc-in [:action :action/type] action-type)
        (cond->
         offense (assoc-in [:action :offense/players] (let [offense (get-in parser [:players (:team parser)])]
                                                        (when-not (= 5 (count offense))
                                                          (raise parser (str "number of offensive players is not five: " offense "\naction-type: " action-type)))
                                                        (-> offense sort vec)))
         defense (assoc-in [:action :defense/players] (let [defense (get-in parser [:players (->> (:teams parser) keys (remove #{(:team parser)}) first)])]
                                                        (when-not (= 5 (count defense))
                                                          (raise parser (str "number of defensive players is not five: " defense "\naction-type: " action-type)))
                                                        (-> defense sort vec)))))))

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

(defn distance
  [parser dist]
  (assoc-in parser [:action :shot/distance] dist))

(defn three
  ([parser]
   (-> parser
       shot
       (assoc-in [:action :shot/value] 3)))
  ([parser dist]
   (-> parser
       three
       (distance dist))))

(defn two
  ([parser]
   (-> parser
       shot
       (assoc-in [:action :shot/value] 2)))
  ([parser dist]
   (-> parser
       two
       (distance dist))))

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

(defn steal
  [parser]
  (assoc-in parser [:action :turnover/stealer] (:number parser)))

(defn block
  [parser]
  (assoc-in parser [:action :shot/blocker] (:number parser)))

(defn period
  [parser]
  (-> parser
      append-action
      append-possession))

(defn action
  [parser action]
  ((ns-resolve (find-ns 'bball.parser) action) parser))

(defn action-form
  [parser [fn-symbol & args]]
  (apply (if (= fn-symbol 'quote)
           distance
           (ns-resolve (find-ns 'bball.parser) fn-symbol))
         parser args))

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
