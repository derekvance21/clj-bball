(ns app.events
  (:require [cljs.math :as math]
            [re-frame.core :as re-frame]
            [app.db :as db]))


(re-frame/reg-fx
 :transact
 (fn [{:keys [conn tx-data on-transact]}]
   (let [tx-report (db/transact! conn tx-data)]
     (when on-transact
       (re-frame/dispatch (conj on-transact tx-report))))))


(re-frame/reg-event-db
 ::set-game-info
 [re-frame/trim-v]
 (fn [db [gtid [team-id1 team-id2] {:keys [tempids]}]]
   (assoc db
          :game-id (get tempids gtid)
          :players {(get tempids team-id1) [0 1 2 3 4]
                    (get tempids team-id2) [5 6 7 8 9]})))


(re-frame/reg-event-fx
 ::initialize
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn]} _]
   (let [tempid -1
         [team-id1 team-id2] [-2 -3]]
     {:db {:init {:init/period 1}}
      :transact {:conn conn
                 :tx-data [{:db/id tempid
                            :game/teams [{:db/id team-id1 :team/name "Blaine"}
                                         {:db/id team-id2 :team/name "King's"}]}]
                 :on-transact [::set-game-info tempid [team-id1 team-id2]]}})))


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
   [:action :ft/attempted]
   [:action :shot/value] [:action :shot/make?] [:action :shot/foul?]))


(def shot-value-interceptor
  (re-frame/on-changes
   (fn [distance angle]
     (when (and (some? distance) (some? angle))
       (let [three-point-distance (+ (* 12 19) 10)
             three? (if (> (abs angle) 0.25) ;; if below 3 point arc break
                      (>= (abs (* (math/sin (* angle 2 math/PI)) distance)) three-point-distance) ;; 
                      (>= distance three-point-distance))]
         (if three? 3 2))))
   [:action :shot/value]
   [:action :shot/distance] [:action :shot/angle]))


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
 ::set-shot-distance-ft
 [shot-value-interceptor
  (re-frame/path [:action :shot/distance])
  re-frame/trim-v]
 (fn [_ [distance]]
   (* 12 distance)))


(re-frame/reg-event-db
 ::set-action-shot-with-info
 [fta-interceptor
  shot-value-interceptor
  (re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [turns radius make?]]
   (assoc
    (if (= :action.type/shot (:action/type action))
      action
      (dissoc action :turnover/stealer :ft/made :ft/attempted :shot/rebounder :shot/off-reb?))
    :action/type :action.type/shot
    :shot/angle turns
    :shot/distance radius
    :shot/make? make?)))


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
 ::set-init-team
 [(re-frame/path [:init :init/team])
  re-frame/trim-v]
 (fn [_ [team]]
   team))


(re-frame/reg-event-fx
 ::next-period
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn db]} _]
   {:db (assoc-in
         db [:init :init/period]
         (-> (db/last-possession @conn (:game-id db))
             (get :possession/period 0)
             inc))}))


(re-frame/reg-event-fx
 ::add-action
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn db]} _]
   (let [{:keys [action game-id players init]} db]
     {:transact {:conn conn
                 :tx-data [[:db.fn/call db/append-action-tx-data game-id action players init]]}
      :db (dissoc db :action :init) ;; TODO - only dissoc :action and :init if transaction was successful?
      })))


(re-frame/reg-event-fx
 ::undo-last-action
 [(re-frame/inject-cofx ::db/datascript-conn)]
 (fn [{:keys [conn db]} _]
   {:transact {:conn conn
               :tx-data [[:db.fn/call db/undo-last-action-tx-data (get db :game-id)]]}}))


#_(re-frame/reg-event-fx
   ::set-action
   [(re-frame/inject-cofx ::db/datascript-conn)
    re-frame/trim-v]
   (fn [{:keys [conn db]} [id]]
     (let [action (d/pull @conn '[*] id)]
       {:db (assoc db :action action)})))


(re-frame/reg-event-db
 ::set-on-court-player
 [re-frame/trim-v]
 (fn [db [t i player]]
   (update-in
    db [:players t]
    (fnil assoc [nil nil nil nil nil])
    i player)))
