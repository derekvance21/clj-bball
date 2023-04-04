(ns app.events
  (:require
   [re-frame.core :as re-frame]
   [app.db :as db]
   [datascript.core :as d]))


(re-frame/reg-fx
 :transact
 (fn [{conn :conn tx-data :tx-data on-transact :on-transact}]
   (let [tx-report (d/transact! conn tx-data)]
     (when on-transact
       (re-frame/dispatch (conj on-transact tx-report))))))


(re-frame/reg-event-db
 ::set-game-info
 [re-frame/trim-v]
 (fn [db [gtid {db-after :db-after tempids :tempids}]]
   (let [g (get tempids gtid)
         game (d/pull db-after [:db/id {:game/teams [:db/id :team/name]}] g)]
     (assoc db :game game))))


(re-frame/reg-event-fx
 ::initialize
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn]} _]
   (let [tempid -1]
     {:db {:period 1}
      :transact {:conn conn
                 :tx-data [{:db/id tempid
                            :game/teams [{:team/name "Blaine"}
                                         {:team/name "Zillah"}]}]
                 :on-transact [::set-game-info tempid]}})))


(re-frame/reg-event-db
 ::set-player
 [(re-frame/path [:action :action/player])
  re-frame/trim-v]
 (fn [_ [player]]
   player))


(re-frame/reg-event-db
 ::set-action-shot
 [(re-frame/path [:action])]
 (fn [action _]
   (-> action
       (select-keys [:action/player])
       (assoc :action/type :action.type/shot))))


(re-frame/reg-event-db
 ::set-action-turnover
 [(re-frame/path [:action])]
 (fn [action _]
   (-> action
       (select-keys [:action/player])
       (assoc :action/type :action.type/turnover))))


(re-frame/reg-event-db
 ::set-action-bonus
 [(re-frame/path [:action])]
 (fn [action _]
   (-> action
       (select-keys [:action/player])
       (assoc :action/type :action.type/bonus))))


(re-frame/reg-event-db
 ::set-action-technical
 [(re-frame/path [:action])]
 (fn [action _]
   (-> action
       (select-keys [:action/player])
       (assoc :action/type :action.type/technical
              :ft/attempted 2))))


(re-frame/reg-event-db
 ::set-stealer
 [(re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [stealer]]
   (if stealer
     (assoc action :turnover/stealer stealer)
     (dissoc action :turnover/stealer))))


(def fta-interceptor
  (re-frame/on-changes
   (fn [value make? foul?]
     (if foul?
       (if make? 1 value)
       0))
   [:action :ft/attempted] [:action :shot/value] [:action :shot/make?] [:action :shot/foul?]))


(re-frame/reg-event-db
 ::set-shot-make?
 [fta-interceptor
  (re-frame/path [:action :shot/make?])
  re-frame/trim-v]
 (fn [_ [make?]]
   make?))


(re-frame/reg-event-db
 ::set-shot-value
 [fta-interceptor
  re-frame/trim-v
  (re-frame/path [:action :shot/value])]
 (fn [_ [value]]
   value))


(re-frame/reg-event-db
 ::set-shot-distance
 [(re-frame/path [:action :shot/distance])
  re-frame/trim-v]
 (fn [_ [distance]]
   distance))


(re-frame/reg-event-db
 ::set-shot-foul?
 [fta-interceptor
  (re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [foul?]]
   (cond-> action
     true (assoc :shot/foul? foul?)
     (not foul?) (dissoc :ft/attempted :ft/made :shot/foul?))))


(re-frame/reg-event-db
 ::set-ft-made
 [(re-frame/path [:action :ft/made])
  re-frame/trim-v]
 (fn [_ [ftm]]
   ftm))


(re-frame/reg-event-db
 ::set-ft-attempted
 [(re-frame/path [:action :ft/attempted])
  re-frame/trim-v]
 (fn [_ [fta]]
   fta))


(re-frame/reg-event-db
 ::set-rebounder
 [(re-frame/path [:action :shot/rebounder])
  re-frame/trim-v]
 (fn [_ [rebounder]]
   rebounder))


(re-frame/reg-event-db
 ::set-off-reb?
 [(re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [off-reb?]]
   (-> action
       (dissoc :shot/rebounder)
       (assoc :shot/off-reb? off-reb?))))


(re-frame/reg-event-db
 ::set-team
 [(re-frame/path :team)
  re-frame/trim-v]
 (fn [_ [team]]
   team))


(re-frame/reg-event-fx
 ::add-action
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn db]} _]
   (let [{action :action game :game period :period} db
         last-possession (db/last-possession (:db/id game))
         t (get-in db [:team :db/id])
         same-possession? (= t (get-in last-possession [:possession/team :db/id]))
         offense (get-in db [:players t])
         defense (-> (get db :players) (dissoc t) vals first)
         tx (if same-possession?
              (let [last-action (apply max-key :action/order (:possession/action last-possession))
                    order (inc (get last-action :action/order 0))]
                {:db/id (:db/id last-possession)
                 :possession/action [(assoc action
                                            :action/order order
                                            :offense/players offense
                                            :defense/players defense)]})
              (let [order (inc (get last-possession :possession/order 0))]
                {:db/id (:db/id game)
                 :game/possession [{:possession/order order
                                    :possession/team t
                                    :possession/period period
                                    :possession/action [(assoc action
                                                               :action/order 1
                                                               :offense/players offense
                                                               :defense/players defense)]}]}))]
     {:transact {:conn conn
                 :tx-data [tx]}
      :db (cond-> db
            true (dissoc :action)
            (not (or (:shot/off-reb? action)
                     (= :action.type/technical (:action/type action))))
            (assoc :team (->> (get-in db [:game :game/teams])
                              (filter #(not= (:db/id %) (get-in db [:team :db/id])))
                              first)))})))


(re-frame/reg-event-fx
 ::set-action
 [(re-frame/inject-cofx ::db/datascript-conn)
  re-frame/trim-v]
 (fn [{:keys [conn db]} [id]]
   (let [action (d/pull @conn '[*] id)]
     {:db (assoc db :action action)})))


(re-frame/reg-event-db
 ::next-period
 (fn [db _]
   (update db :period inc)))


(re-frame/reg-event-db
 ::set-on-court-player
 [re-frame/trim-v]
 (fn [db [t i player]]
   (update-in db [:players t]
              (fnil assoc [nil nil nil nil nil])
              i player)))
