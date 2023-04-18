(ns app.events
  (:require
   [re-frame.core :as re-frame]
   [app.ds :as ds]
   [app.db :as db]
   [app.cofx :as cofx]
   [app.fx :as fx]
   [app.interceptors :as interceptors]
   [datascript.core :as datascript]))


(re-frame/reg-event-fx
 ::initialize
 [cofx/inject-ds
  (re-frame/inject-cofx ::cofx/local-storage-datascript-db)]
 (fn [{:keys [ds ls-datascript-db]} _]
   ;; TODO - there should be a separate event for the case where there is a local storage db
   ;; and this would dispatch it
   (if (some? ls-datascript-db)
     (let [game-id (datascript/q
                    '[:find ?g .
                      :where [?g :game/teams]]
                    ls-datascript-db)
           players (->> (ds/game->team-players ls-datascript-db 1)
                        (map #(update % 1 (comp vec (partial take 5))))
                        (into {})) ;; TODO - use last action's players as initial players map
           ]
       {:db {:game-id game-id
             :players players}
        :fx [[::fx/ds ls-datascript-db]]})
     (let [game-tempid -1
           team1-tempid -2
           team2-tempid -3
           {:keys [db-after tempids]} (datascript/with ds [{:db/id game-tempid
                                                            :game/teams [{:db/id team1-tempid :team/name "Home"}
                                                                         {:db/id team2-tempid :team/name "Away"}]}])
           game-id (get tempids game-tempid)
           players {(get tempids team1-tempid) [0 1 2 3 4]
                    (get tempids team2-tempid) [5 6 7 8 9]}]

       {:db (assoc db/init-db :game-id game-id :players players)
        :fx [[::fx/ds db-after]]}))))


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
   (if (some? stealer)
     (assoc action :steal/player stealer)
     (dissoc action :steal/player))))


;; will be used later, b/c on mobile, can't right-click to set a "make"
(re-frame/reg-event-db
 ::set-shot-make?
 [interceptors/rebound
  interceptors/fta
  (re-frame/path [:action :shot/make?])
  re-frame/trim-v]
 (fn [_ [make?]]
   make?))


(re-frame/reg-event-db
 ::set-action-shot-with-info
 [interceptors/rebound
  interceptors/fta
  interceptors/shot-value
  (re-frame/path [:action])
  re-frame/trim-v]
 (fn [{:action/keys [type] :as action} [turns radius make?]]
   (cond-> action
     (not= type :action.type/shot)
     (dissoc :steal/player
             :ft/made
             :ft/attempted
             :rebound/player
             :rebound/team?
             :rebound/off?)
     true
     (assoc :action/type :action.type/shot
            :shot/angle turns
            :shot/distance radius
            :shot/make? make?))))




(re-frame/reg-event-db
 ::set-shot-foul?
 [interceptors/fta
  (re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [foul?]]
   (cond-> action
     true (assoc :shot/foul? foul?)
     (not foul?) (dissoc :ft/attempted :ft/made :shot/foul?))))


(re-frame/reg-event-db
 ::set-ft-made
 [interceptors/rebound
  (re-frame/path [:action :ft/made])
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
 [(re-frame/path [:action :rebound/player])
  re-frame/trim-v]
 (fn [_ [rebounder]]
   rebounder))


(re-frame/reg-event-db
 ::set-off-reb?
 [(re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [off-reb?]]
   (-> action
       (dissoc :rebound/player)
       (assoc :rebound/off? off-reb?))))

(re-frame/reg-event-db
 ::set-team-reb?
 [(re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [team-reb?]]
   (-> action
       (dissoc :rebound/player)
       (assoc :rebound/team? team-reb?))))


(re-frame/reg-event-db
 ::set-init-team
 [(re-frame/path [:init :init/team])
  re-frame/trim-v]
 (fn [_ [team]]
   team))


(re-frame/reg-event-fx
 ::next-period
 [cofx/inject-ds]
 (fn [{:keys [ds db]} _]
   {:db (assoc-in
         db [:init :init/period]
         (-> (ds/last-possession ds (:game-id db))
             (get :possession/period 0)
             inc))}))


(re-frame/reg-event-fx
 ::add-action
 [cofx/inject-ds
  interceptors/ds->local-storage]
 (fn [{:keys [ds db]} _]
   (let [{:keys [action game-id players init]} db
         tx-data [[:db.fn/call ds/append-action-tx-data game-id action players init]]
         new-ds (datascript/db-with ds tx-data)]
     {:fx [[::fx/ds new-ds]]
      :db (dissoc db :action :init) ;; TODO - only dissoc :action and :init if transaction was successful?
      })))


(re-frame/reg-event-fx
 ::undo-last-action
 [cofx/inject-ds
  interceptors/ds->local-storage]
 (fn [{:keys [ds db]} _]
   {:fx [[::fx/ds (datascript/db-with ds [[:db.fn/call ds/undo-last-action-tx-data (:game-id db)]])]]
    :db (dissoc db :action)}))


(re-frame/reg-event-db
 ::set-on-court-player
 [re-frame/trim-v]
 (fn [db [t i player]]
   (update-in
    db [:players t]
    (fnil assoc [nil nil nil nil nil])
    i player)))

