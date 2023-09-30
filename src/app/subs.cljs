(ns app.subs
  (:require
   [app.datascript :as ds]
   [app.db :as db]
   [bball.query :as query]
   [clojure.string :as string]
   [clojure.set]
   [datascript.core :as d]
   [re-frame.core :as re-frame]
   [datascript.parser :as datascript-parser]))


(re-frame/reg-sub
 ::datascript-db
 (fn [query-vec dynamic-vec]
   ds/conn)
 (fn [datascript-db query-vec]
   datascript-db))


(re-frame/reg-sub
 ::game-ppp
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] query-vec]
   (->> (ds/ppp db g)
        (map (fn [[t pts nposs]]
               [t (/ pts nposs)]))
        (into {}))))


(re-frame/reg-sub
 ::team-ppp
 :<- [::game-ppp]
 (fn [ppp [_ t]]
   (get ppp t)))


(re-frame/reg-sub
 ::efg
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (->> (ds/efg db g)
        (into {}))))


(re-frame/reg-sub
 ::team-efg
 :<- [::efg]
 (fn [efg [_ t]]
   (get efg t)))


(re-frame/reg-sub
 ::off-reb-rate
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (->> (ds/off-reb-rate db g)
        (into {}))))


(re-frame/reg-sub
 ::team-off-reb-rate
 :<- [::off-reb-rate]
 (fn [rate [_ t]]
   (get rate t)))


(re-frame/reg-sub
 ::turnover-rate
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (->> (ds/turnover-rate db g)
        (into {}))))


(re-frame/reg-sub
 ::team-turnover-rate
 :<- [::turnover-rate]
 (fn [rate [_ t]]
   (get rate t)))


(re-frame/reg-sub
 ::pps
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (->> (ds/pps db g)
        (into {}))))


(re-frame/reg-sub
 ::team-pps
 :<- [::pps]
 (fn [pps [_ t]]
   (get pps t)))


(re-frame/reg-sub
 ::ft-rate
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (->> (ds/ft-rate db g)
        (into {}))))


(re-frame/reg-sub
 ::team-ft-rate
 :<- [::ft-rate]
 (fn [ft-rate [_ t]]
   (get ft-rate t)))


(re-frame/reg-sub
 ::fts-per-shot
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (->> (ds/fts-per-shot db g)
        (into {}))))


(re-frame/reg-sub
 ::team-fts-per-shot
 :<- [::fts-per-shot]
 (fn [fts-per-shot [_ t]]
   (get fts-per-shot t)))


(re-frame/reg-sub
 ::game-id
 (fn [db _]
   (:game-id db)))


(re-frame/reg-sub
 ::game-ids
 :<- [::datascript-db]
 (fn [db _]
   (d/q '[:find [?g ...]
          :where
          [?g :game/home-team]
          [?g :game/away-team]]
        db)))


(re-frame/reg-sub
 ::team-has-possession?
 (fn [_]
   (re-frame/subscribe [::team]))
 (fn [team [_ t]]
   (and (some? t) (= (:db/id team) t))))


(re-frame/reg-sub
 ::team
 :<- [::datascript-db]
 :<- [::game-id]
 :<- [::init]
 (fn [[db g init] _]
   (if (some? init)
     (get init :init/team)
     (ds/team-possession db g))))


(re-frame/reg-sub
 ::show-preview?
 :<- [::team]
 :<- [::action]
 (fn [[team action] _]
   (and (some? team)
        (some? action))))


(re-frame/reg-sub
 ::preview-db-tx-report
 :<- [::datascript-db]
 :<- [::game-id]
 :<- [::action]
 :<- [::players]
 :<- [::init]
 :<- [::show-preview?]
 (fn [[db g action players init preview?] _]
   (if preview?
     (d/with db [[:db.fn/call ds/append-action-tx-data g action players init]])
     (d/with db nil))))


(re-frame/reg-sub
 ::preview-entities
 :<- [::preview-db-tx-report]
 (fn [tx-report _]
   (set (vals (:tempids tx-report)))))


(re-frame/reg-sub
 ::preview-db-after
 :<- [::preview-db-tx-report]
 (fn [tx-report _]
   (:db-after tx-report)))


(re-frame/reg-sub
 ::preview-possessions
 :<- [::preview-db-after]
 :<- [::game-id]
 (fn [[db g] _]
   (sort-by :possession/order > (ds/possessions db g))))


(re-frame/reg-sub
 ::preview-score
 :<- [::preview-db-after]
 :<- [::game-id]
 (fn [[db g] _]
   (into {} (ds/score db g))))


(re-frame/reg-sub
 ::preview-team-score
 :<- [::preview-score]
 (fn [score [_ t]]
   (get score t 0)))


(re-frame/reg-sub
 ::action
 (fn [db _]
   (get db :action)))


(re-frame/reg-sub
 ::possessions?
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (ds/possessions? db g)))


(re-frame/reg-sub
 ::action-player
 (fn [db]
   (get-in db [:action :action/player])))


(re-frame/reg-sub
 ::game-teams
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (when (some? g) ;; can't issue datascript pull with nil entity id, so wait for game-id to be populated
     (ds/teams db g))))


(re-frame/reg-sub
 ::score
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] query-vec]
   (into {} (ds/score db g))))


(re-frame/reg-sub
 ::team-score
 :<- [::score]
 (fn [score [_ t]]
   (get score t 0)))


(re-frame/reg-sub
 ::action-type
 (fn [db]
   (get-in db [:action :action/type])))


(re-frame/reg-sub
 ::shot-make?
 (fn [db]
   (get-in db [:action :shot/make?])))


(re-frame/reg-sub
 ::shot-location
 (fn [db]
   (let [angle (get-in db [:action :shot/angle])
         distance (get-in db [:action :shot/distance])]
     (when (and (some? angle) (some? distance))
       [angle distance]))))


(re-frame/reg-sub
 ::shot-chart-teams
 (fn [db]
   (get-in db [:shot-chart :teams] #{})))


(re-frame/reg-sub
 ::shot-chart-players-input
 (fn [db]
   (get-in db [:shot-chart :players-input])))


(re-frame/reg-sub
 ::shot-chart-players
 :<- [::shot-chart-players-input]
 (fn [players-input]
   (->> (string/split players-input #"\s+")
        (map parse-long)
        (filter number?)
        set)))


(re-frame/reg-sub
 ::shot-chart-offense-input
 (fn [db]
   (get-in db [:shot-chart :offense-input])))


(re-frame/reg-sub
 ::shot-chart-offense
 :<- [::shot-chart-offense-input]
 (fn [offense-input _]
   (->> (string/split offense-input #"\s+")
        (map parse-long)
        (filter number?)
        set)))


(re-frame/reg-sub
 ::shot-chart-games
 (fn [db _]
   (get-in db [:shot-chart :games] #{})))


(re-frame/reg-sub
 ::defensive-filters
 :<- [::shot-chart-games]
 :<- [::shot-chart-teams]
 :<- [::shot-chart-offense]
 (fn [[games teams defense] _]
   (remove nil?
           [(when (seq games)
              {:in '[[?g ...]]
               :args [games]
               :where '[(or [?g :game/home-team ?dt]
                            [?g :game/away-team ?dt])]})
            (when (seq teams)
              {:in '[[?dt ...]]
               :args [teams]
               :where '[(or [?g :game/home-team ?dt]
                            [?g :game/away-team ?dt])
                        (not [?p :possession/team ?dt])]})
            (when (seq defense)
              {:in '[?defense-subset subset?]
               :where '[[?a :defense/players ?defense-tuple]
                        [(set ?defense-tuple) ?defense]
                        [(subset? ?defense-subset ?defense)]]
               :args [defense clojure.set/subset?]})])))


;; Here are problems:
;; 1. the order of the clauses is off, so filtering clauses aren't first (important for query performance reasons)
;; 2. when you use q+, you can't see the whole query, so you don't know what affects what
;; 3. duplicate inputs aren't allowed, so you have to do things like subset? and subset1?
;; Idea:
;; the entire query, with filter clauses and all, is written
;; filter input is a map of symbols to arguments
;; if a clause has a function or predicate with an unbound input (do to the symbol->arg being nil)
;; remove that clause
;; that way, you can control the order of clauses (for performance reasons)
;; and then, it's trivial to get the query :in inputs and order of arguments in the call to d/q right
(defn q+
  [base-query filters & args]
  (let [query-map (datascript-parser/query->map base-query)
        input->arg-map (apply merge (map (fn [{:keys [in args]}] (zipmap in args)) filters))

        ;; in the :where part of query-map, the filtering clauses should go first
        query-with-filters (-> query-map
                               (update :where #(concat % (mapcat :where filters)))
                               (update :in #(concat % (keys input->arg-map))))]
    (apply d/q query-with-filters (concat args (vals input->arg-map)))))


(re-frame/reg-sub
 ::ppp
 :<- [::datascript-db]
 :<- [::filters]
 (fn [[db filters] _]
   (let [base-query '[:find [(sum ?pts) (count-distinct ?p)]
                      :keys pts possessions
                      :in $ %
                      :with ?a
                      :where
                      (actions ?g ?t ?p ?a)
                      (pts ?a ?pts)]
         {:keys [pts possessions] :as result} (q+ base-query filters db query/rules)]
     (assoc result :offrtg (* 100 (/ pts possessions))))))


(re-frame/reg-sub
 ::defensive-ppp
 :<- [::datascript-db]
 :<- [::defensive-filters]
 (fn [[db filters] _]
   (let [base-query '[:find [(sum ?pts) (count-distinct ?p)]
                      :keys pts possessions
                      :in $ %
                      :with ?a
                      :where
                      (actions ?g ?t ?p ?a)
                      (pts ?a ?pts)]
         {:keys [pts possessions] :as result} (q+ base-query filters db query/rules)]
     (assoc result :defrtg (* 100 (/ pts possessions))))))


(re-frame/reg-sub
 ::true-shooting
 :<- [::datascript-db]
 :<- [::filters]
 (fn [[db filters] _]
   (let [base-query '[:find (avg ?pts) .
                      :in $ %
                      :with ?a
                      :where
                      (actions ?g ?t ?p ?a)
                      (not [?a :action/type :action.type/turnover])
                      (pts ?a ?pts)]
         pps (q+ base-query filters db query/rules)]
     (* pps 50))))


(re-frame/reg-sub
 ::true-shooting-average
 :<- [::datascript-db]
 (fn [db _]
   (let [base-query '[:find ?t (avg ?pts)
                      :in $ %
                      :with ?a
                      :where
                      (actions ?g ?t ?p ?a)
                      (not [?a :action/type :action.type/turnover])
                      (pts ?a ?pts)]
         results (d/q base-query db query/rules)
         sum (apply + (map second results))]
     (* 50 (/ sum (count results))))))


(re-frame/reg-sub
 ::free-throw-rate
 :<- [::datascript-db]
 :<- [::filters]
 (fn [[db filters] _]
   (let [ftm-query '[:find (sum ?ftm) .
                     :in $ %
                     :with ?a
                     :where
                     (actions ?g ?t ?p ?a)
                     [?a :ft/made ?ftm]]
         ftm (q+ ftm-query filters db query/rules)
         fga-query '[:find (count-distinct ?a) .
                     :in $ %
                     :where
                     (actions ?g ?t ?p ?a)
                     (fga? ?a)]
         fga (q+ fga-query filters db query/rules)]
     (/ ftm fga))))


(re-frame/reg-sub
 ::effective-field-goal-percentage
 :<- [::datascript-db]
 :<- [::filters]
 (fn [[db filters] _]
   (let [efg-query '[:find (avg ?efgs) .
                     :in $ %
                     :with ?a
                     :where
                     (actions ?g ?t ?p ?a)
                     (fga? ?a)
                     (efgs ?a ?efgs)]]
     (* 100 (q+ efg-query filters db query/rules)))))


(re-frame/reg-sub
 ::games-filter
 :<- [::shot-chart-games]
 (fn [games _]
   (when (seq games)
     {:in '[[?g ...]]
      :args [games]})))


(re-frame/reg-sub
 ::teams-filter
 :<- [::shot-chart-teams]
 (fn [teams _]
   (when (seq teams)
     {:in '[[?t ...]]
      :args [teams]})))


(re-frame/reg-sub
 ::players-filter
 :<- [::shot-chart-players]
 (fn [players _]
   (when (seq players)
     {:in '[?player-numbers]
      :where '[[?a :action/player ?player]
               [(contains? ?player-numbers ?player)]]
      :args [players]})))


(re-frame/reg-sub
 ::offense-filter
 :<- [::shot-chart-offense]
 (fn [offense _]
   (when (seq offense)
     {:in '[?offense-subset subset?]
      :where '[[?a :offense/players ?offense-tuple]
               [(set ?offense-tuple) ?offense]
               [(subset? ?offense-subset ?offense)]]
      :args [offense clojure.set/subset?]})))


(re-frame/reg-sub
 ::filters
 :<- [::games-filter]
 :<- [::teams-filter]
 :<- [::players-filter]
 :<- [::offense-filter]
 (fn [[games-filter teams-filter players-filter offense-filter] _]
   (remove nil?
           [games-filter
            teams-filter
            players-filter
            offense-filter])))


(re-frame/reg-sub
 ::offensive-rebounding-rate
 :<- [::datascript-db]
 :<- [::games-filter]
 :<- [::teams-filter]
 :<- [::players-filter]
 :<- [::offense-filter]
 (fn [[db games-filter teams-filter players-filter offense-filter] _]
   (let [base-query '[:find (avg ?off-rebs) .
                      :in $ %
                      :with ?a
                      :where
                      (actions ?g ?t ?p ?a)
                      (rebound? ?a)]
         filters (remove nil? [games-filter
                               teams-filter
                               (if-some [{[players] :args} players-filter]
                                 {:in '[[?player ...]]
                                  :args [players]
                                  :where '[[?a :offense/players ?offense-tuple]
                                           [(set ?offense-tuple) ?offense]
                                           [(contains? ?offense ?player)]
                                           (off-rebs-player ?a ?player ?off-rebs)]}
                                 {:where '[(off-rebs ?a ?off-rebs)]})
                               offense-filter])]
     (q+ base-query filters db query/rules))))


(re-frame/reg-sub
 ::points-per-75
 :<- [::datascript-db]
 :<- [::games-filter]
 :<- [::teams-filter]
 :<- [::players-filter]
 :<- [::offense-filter]
 (fn [[db games-filter teams-filter players-filter offense-filter]]
   (let [base-query '[:find [(sum ?pts) (count-distinct ?p)]
                      :keys pts possessions
                      :in $ %
                      :with ?a
                      :where
                      (actions ?g ?t ?p ?a)
                      (pts-player ?a ?player ?pts)]
         filters (remove nil?
                         [games-filter
                          teams-filter
                          (when-some [{[players] :args} players-filter]
                            {:in '[[?player ...] intersection]
                             :args [players]
                             :where '[[?a :offense/players ?offense-tuple]
                                      [(set ?offense-tuple) ?offense]
                                      [(contains? ?offense ?player)]
                                      (pts-player ?a ?player ?pts)]})
                          offense-filter])
         {:keys [pts possessions] :as result} (q+ base-query filters db query/rules)]
     (assoc result :pts-per-75 (* 100 #_75 (/ pts possessions))))))


(re-frame/reg-sub
 ::usage-rate
 :<- [::datascript-db]
 :<- [::games-filter]
 :<- [::teams-filter]
 :<- [::players-filter]
 :<- [::offense-filter]
 (fn [[db games-filter teams-filter players-filter offense-filter]]
   (let [base-query '[:find (avg ?plays) .
                      :in $ %
                      :with ?a
                      :where
                      (actions ?g ?t ?p ?a)
                      (plays-player ?a ?player ?plays)]
         filters (remove nil?
                         [games-filter
                          teams-filter
                          (when-some [{[players] :args} players-filter]
                            {:in '[[?player ...]]
                             :args [players]
                             :where '[[?a :offense/players ?offense-tuple]
                                      [(set ?offense-tuple) ?offense]
                                      [(contains? ?offense ?player)]]})
                          offense-filter])
         usage-rate (q+ base-query filters db query/rules)]
     (* 100 usage-rate))))


(re-frame/reg-sub
 ::turnovers-per-play
 :<- [::datascript-db]
 :<- [::filters]
 (fn [[db filters] _]
   (let [base-query '[:find [(sum ?tos) (count-distinct ?a)]
                      :keys turnovers plays
                      :in $ %
                      :where
                      (actions ?g ?t ?p ?a)
                      (tos ?a ?tos)]
         {:keys [turnovers plays]} (q+ base-query filters db query/rules)]
     (/ turnovers plays))))


(re-frame/reg-sub
 ::shots
 :<- [::datascript-db]
 :<- [::filters]
 (fn [[db filters] _]
   (let [shots-query '[:find
                       (pull ?g [:game/datetime {:game/home-team [:team/name]
                                                 :game/away-team [:team/name]}])
                       (pull ?t [:team/name])
                       (pull ?a [:action/player :shot/distance :shot/angle :shot/make?]) ?pts
                       :keys game team action pts
                       :in $ %
                       :where
                       (actions ?g ?t ?p ?a)
                       [?a :action/type :action.type/shot]
                       (pts ?a ?pts)]]
     (q+ shots-query filters db query/rules))))


(re-frame/reg-sub
 ::teams
 :<- [::datascript-db]
 :<- [::games-filter]
 (fn [[db games-filter] _]
   (let [filters (remove nil? [games-filter])
         base-query '[:find [(pull ?t [*]) ...]
                      :in $
                      :where
                      (or [?g :game/home-team ?t]
                          [?g :game/away-team ?t])
                      [?g :game/possession]]]
     (q+ base-query filters db))))


(re-frame/reg-sub
 ::games
 :<- [::datascript-db]
 :<- [::teams-filter]
 :<- [::players-filter]
 :<- [::offense-filter]
 (fn [[db teams-filter players-filter offense-filter] _]
   (let [filters (remove nil? [teams-filter players-filter offense-filter])
         games-query '[:find [(pull ?g [:db/id :game/datetime
                                        {:game/home-team [:team/name]
                                         :game/away-team [:team/name]}])
                              ...]
                       :in $ %
                       :where
                       (actions ?g ?t ?p ?a)
                       (or [?g :game/home-team ?t]
                           [?g :game/away-team ?t])]]
     (q+ games-query filters db query/rules))))


(re-frame/reg-sub
 ::ft-attempted
 (fn [db]
   (get-in db [:action :ft/attempted])))


(re-frame/reg-sub
 ::foul?
 (fn [db]
   (get-in db [:action :shot/foul?])))


(re-frame/reg-sub
 ::ft?
 :<- [::foul?]
 :<- [::action-type]
 (fn [[foul? action-type] _]
   (or foul? (contains? #{:action.type/bonus :action.type/technical} action-type))))


(re-frame/reg-sub
 ::rebounder
 (fn [db]
   (get-in db [:action :rebound/player])))


(re-frame/reg-sub
 ::off-reb?
 (fn [db]
   (get-in db [:action :rebound/off?])))


(re-frame/reg-sub
 ::team-reb?
 (fn [db]
   (get-in db [:action :rebound/team?])))


(re-frame/reg-sub
 ::stealer
 (fn [db]
   (get-in db [:action :steal/player])))


(re-frame/reg-sub
 ::reboundable?
 (fn [db _]
   (db/reboundable? (:action db))))


(re-frame/reg-sub
 ::period
 :<- [::datascript-db]
 :<- [::game-id]
 :<- [::init-period]
 (fn [[db g init-period] _]
   (if (some? init-period)
     init-period
     (:possession/period (ds/last-possession db g)))))


(re-frame/reg-sub
 ::players
 (fn [db _]
   (get db :players)))


(re-frame/reg-sub
 ::team-players-on-court
 :<- [::players]
 (fn [players [_ t]]
   (sort (get-in players [t :on-court]))))


(re-frame/reg-sub
 ::team-players-on-bench
 :<- [::players]
 (fn [players [_ t]]
   (sort (get-in players [t :on-bench]))))


(re-frame/reg-sub
 ::team-players-on-court-ft
 :<- [::players]
 (fn [players [_ t]]
   (sort (get-in players [t :on-court-ft]))))


(re-frame/reg-sub
 ::team-players-on-bench-ft
 :<- [::players]
 (fn [players [_ t]]
   (sort (get-in players [t :on-bench-ft]))))


(re-frame/reg-sub
 ::init
 (fn [db _]
   (get db :init)))


(re-frame/reg-sub
 ::init-period
 :<- [::init]
 (fn [init _]
   (get init :init/period)))


(re-frame/reg-sub
 ::mid-period?
 :<- [::init]
 :<- [::possessions?]
 (fn [[init possessions?]]
   (and (nil? init) possessions?)))


(re-frame/reg-sub
 ::need-action-player?
 :<- [::action-player]
 :<- [::action-type]
 (fn [[player type] _]
   (or (nil? player) (nil? type))))

(re-frame/reg-sub
 ::need-rebound-player?
 :<- [::action-player]
 :<- [::reboundable?]
 (fn [[player reboundable?] _]
   (and (some? player) reboundable?)))

(re-frame/reg-sub
 ::need-stealer-player?
 :<- [::action-player]
 :<- [::action-type]
 (fn [[player type] _]
   (and (= type :action.type/turnover) (some? player))))


(re-frame/reg-sub
 ::ft-results
 (fn [db _]
   (get-in db [:action :ft/results])))


;; TODO - add five count check for ft/offense/defense
(re-frame/reg-sub
 ::valid?
 :<- [::action]
 :<- [::players]
 :<- [::team]
 :<- [::sub?]
 (fn [[action players team sub?] _]
   (let [five-players? (->> (vals players)
                            (map :on-court)
                            (every? #(= 5 (count %))))
         {:action/keys [player type]} action]
     (and five-players?
          (some? team)
          (some? player)
          (some? type)
          (not sub?)
          ;; (or (not reboundable?) rebound?) ;; buzzer beaters don't have a rebounder
          ))))


(re-frame/reg-sub
 ::sub?
 (fn [db _]
   (get-in db [:sub?] false)))


(defn cos-turns
  [turns]
  (Math/cos (* 2 Math/PI turns)))


(defn sin-turns
  [turns]
  (Math/sin (* 2 Math/PI turns)))


(defn in-sector?
  [{:keys [min-angle max-angle min-distance max-distance]
    :or {min-angle -0.5 min-distance 0
         max-angle 0.5 max-distance ##Inf}}
   angle distance]
  (let [effective-distance (cond (and (= min-angle -0.5)
                                      (< angle -0.25)) (abs (* distance (sin-turns angle)))
                                 (and (= max-angle 0.5)
                                      (> angle 0.25)) (abs (* distance (sin-turns angle)))
                                 :else distance)]
    (and (>= angle min-angle)
         (< angle max-angle)
         (>= effective-distance min-distance)
         (< effective-distance max-distance))))


(defn make-arc-sectors
  [breakpoints]
  (let [sorted (sort breakpoints)]
    (map #(hash-map :min-distance %1 :max-distance %2
                    :min-angle -0.5 :max-angle 0.5) (into [0] sorted) sorted)))


(defn make-angles
  [portions]
  (flatten [-0.5
            (->>
             (iterate #(+ % (/ 0.5 portions)) -0.25)
             (take-while #(<= (.toFixed % 2) 0.25))
             (drop 1)
             (drop-last 1))
            0.5]))


(defn make-sectors
  [distance-angles-map]
  (let [distances (sort (keys distance-angles-map))
        distance-sectors (map #(hash-map :min-distance %1 :max-distance %2)
                              (into [0] distances)
                              distances)]
    (mapcat (fn [{:keys [max-distance] :as distance-sector}]
              (let [angles (get distance-angles-map max-distance)]
                (map (fn [min-angle max-angle]
                       (assoc distance-sector
                              :min-angle min-angle
                              :max-angle max-angle))
                     angles
                     (rest angles))))
            distance-sectors)))


(def zone-layouts
  [;; arcs detailed, 3's split in fourths
   {:layout (make-arc-sectors [36 84 144 (+ 10 (* 19 12)) (+ 9 (* 23 12)) (* 30 12)])
    :label "Arcs"}
   #_{:layout (make-arc-sectors [36 66 102 162 (+ 10 (* 19 12)) (+ 9 (* 23 12)) (* 30 12)])
      :label "Arcs"}
     ;; one less arc from arcs detailed, 3's split in thirds
   {:layout (make-arc-sectors [30 54 78 108 150 192 (+ 10 (* 19 12)) (* 22 12) (* 25.5 12) (* 30 12)])
    :label "Detailed Arcs"}
      ;; every two feet to 30 ft
   {:layout (make-arc-sectors (take-while #(<= % (* 12 30)) (iterate #(+ % 12) 12)))
    :label "Every Arc"}
   #_{:layout (make-sectors
          ;; detailed
               {36 [-0.5 -0.125 0.125 0.5]
                78 [-0.5 -0.125 0.125 0.5]
                144 [-0.5 -0.125 0.125 0.5]
                (+ 10 (* 19 12)) (make-angles 3)
                        ;(+ 9 (* 23 12)) [-0.5 -0.2 -0.083333 0.083333 0.2 0.5] #_(make-angles 5)
                (* 30 12) [-0.5 -0.1786 -0.065 0.065 0.1786 0.5]})
      :label "Less Detailed Sectors"}
          ;; detailed with NBA 3
   {:layout (make-sectors
             {48 [-0.5 -0.125 0.125 0.5]
              132 [-0.5 -0.125 0.125 0.5]
              (+ 10 (* 19 12)) (make-angles 3)
              (* 30 12) [-0.5 -0.1786 -0.065 0.065 0.1786 0.5]})
    :label "Sectors"}
          ;; fat bands
   {:layout (make-sectors
             {36 [-0.5 -0.125 0.125 0.5]
              78 [-0.5 -0.125 0.125 0.5]
              144 [-0.5 -0.125 0.125 0.5]
              (+ 10 (* 19 12)) (make-angles 3)
              (+ 9 (* 23 12)) [-0.5 -0.2 -0.083333 0.083333 0.2 0.5] #_(make-angles 5)
              (* 30 12) [-0.5 -0.083333 0.083333 0.5]})
    :label "Detailed Sectors"}])


(re-frame/reg-sub
 ::zone-layouts
 (fn [_ _]
   (map-indexed (fn [idx itm]
                  (assoc itm :id (keyword (str idx)))) zone-layouts)))


(re-frame/reg-sub
 ::zone-layout
 (fn [db _]
   (get-in db [:shot-chart :zone-layout] :0)))


(re-frame/reg-sub
 ::sectors
 :<- [::zone-layout]
 :<- [::zone-layouts]
 (fn [[layout-id layouts] _]
   (->> layouts
        (filter #(= layout-id (:id %)))
        first
        :layout)))


(re-frame/reg-sub
 ::shot-data-by-sector
 :<- [::datascript-db]
 :<- [::filters]
 :<- [::sectors]
 (fn [[db filters sectors] _]
   (let [sector-data-query '[:find ?sector (avg ?pts) (count-distinct ?a) (sum ?pts)
                             :keys sector pps shots pts
                             :in $ % [?sector ...] in-sector?
                             :where
                             (actions ?g ?t ?p ?a)
                             [?a :action/type :action.type/shot]
                             [?a :shot/angle ?angle]
                             [?a :shot/distance ?distance]
                             [(in-sector? ?sector ?angle ?distance)]
                             (pts ?a ?pts)]]
     (q+ sector-data-query filters db query/rules
         sectors
         in-sector?))))


(re-frame/reg-sub
 ::shot-data
 :<- [::datascript-db]
 :<- [::filters]
 (fn [[db filters] _]
   (let [shot-data-query '[:find [(count-distinct ?a) (sum ?pts) (avg ?pts)]
                           :keys shots pts pps
                           :in $ %
                           :where
                           (actions ?g ?t ?p ?a)
                           [?a :action/type :action.type/shot]
                           (pts ?a ?pts)]]
     (q+ shot-data-query filters db query/rules))))


(re-frame/reg-sub
 ::show-shots?
 (fn [db _]
   (get-in db [:shot-chart :show-shots?])))


(re-frame/reg-sub
 ::show-zones?
 (fn [db _]
   (get-in db [:shot-chart :show-zones?])))


(re-frame/reg-sub
 ::zone-by
 (fn [db _]
   (get-in db [:shot-chart :zone-by] :pps)))


(re-frame/reg-sub
 ::active-panel
 (fn [db _]
   (get db :active-panel)))