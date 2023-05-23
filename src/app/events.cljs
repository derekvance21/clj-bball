(ns app.events
  (:require
   [re-frame.core :as re-frame]
   [app.datascript :as ds]
   [app.db :as db]
   [app.coeffects :as cofx]
   [app.effects :as fx]
   [app.interceptors :as interceptors]
   [datascript.core :as d]))


(re-frame/reg-event-fx
 ::initialize-ds-with-local-storage
 [(re-frame/inject-cofx ::cofx/local-storage-datascript-db)]
 (fn [{:keys [ls-datascript-db]} _]
   (let [game-id (d/q
                  '[:find ?g .
                    :where [?g :game/teams]]
                  ls-datascript-db)
         players (->> (ds/game->team-players ls-datascript-db game-id)
                      (map (fn [[team-id players]] [team-id {:on-court players}]))
                      (into {})) ;; TODO - use last action's players as initial players map
         ]
     {:db {:game-id game-id
           :players players}
      ::fx/ds ls-datascript-db})))


(re-frame/reg-event-fx
 ::initialize-blank-ds
 [(re-frame/inject-cofx ::cofx/local-storage-datascript-db)]
 (fn [_ _]
   (let [game-tempid -1
         team1-tempid -2
         team2-tempid -3
         {:keys [db-after tempids]} (d/with ds/empty-db [{:db/id game-tempid
                                                          :game/teams [{:db/id team1-tempid :team/name "Home"}
                                                                       {:db/id team2-tempid :team/name "Away"}]}])
         game-id (get tempids game-tempid)
         team1-id (get tempids team1-tempid)
         team2-id (get tempids team2-tempid)
         players {team1-id {:on-court #{0 1 2 3 4}}
                  team2-id {:on-court #{5 6 7 8 9}}}]
     {:db (assoc db/init-db :game-id game-id :players players)
      ::fx/ds db-after})))


(re-frame/reg-event-fx
 ::initialize
 [cofx/inject-ds
  (re-frame/inject-cofx ::cofx/local-storage-datascript-db)]
 (fn [{:keys [ls-datascript-db]} _]
   {:fx [[:dispatch (if (some? ls-datascript-db)
                      [::initialize-ds-with-local-storage]
                      [::initialize-blank-ds])]]}))


(re-frame/reg-event-db
 ::set-player
 [(re-frame/path [:action :action/player])
  re-frame/trim-v]
 (fn [_ [player]]
   player))


(re-frame/reg-event-db
 ::set-action-turnover
 [interceptors/ft-players
  (re-frame/path [:action])]
 (fn [action _]
   (-> action
       (select-keys [:action/player])
       (assoc :action/type :action.type/turnover))))


(re-frame/reg-event-db
 ::set-action-bonus
 [interceptors/ft-players
  interceptors/ftm
  interceptors/ft-results
  (re-frame/path [:action])]
 (fn [action _]
   (-> action
       (select-keys [:action/player])
       (assoc :action/type :action.type/bonus
              :ft/attempted 1))))


(re-frame/reg-event-db
 ::set-action-technical
 [interceptors/ft
  (re-frame/path [:action])]
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
  interceptors/ft
  (re-frame/path [:action :shot/make?])
  re-frame/trim-v]
 (fn [_ [make?]]
   make?))


(re-frame/reg-event-db
 ::set-action-shot-with-info
 [interceptors/rebound
  interceptors/ft
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


;; TODO - add an interceptor to set [:players :on-court-ft] when shooting ft's
(re-frame/reg-event-db
 ::set-shot-foul?
 [interceptors/ft-players
  interceptors/rebound
  interceptors/ft
  re-frame/trim-v]
 (fn [db [foul?]]
   (let [{:keys [action players]} db]
     (cond-> db
       true (update :action assoc :shot/foul? foul?)
       (not foul?) (update :action dissoc :ft/attempted :ft/made :shot/foul?)
       foul? (update :players
                     (fn [players-map]
                       (->> players-map
                            (map (fn [[team-id team-players]]
                                   [team-id (assoc team-players
                                                   :on-court-ft (:on-court team-players)
                                                   :on-bench-ft (:on-bench team-players))]))
                            (into {}))))))))


(re-frame/reg-event-db
 ::set-rebounder
 [(re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [offense? rebounder]]
   (-> action
       (dissoc :rebound/team?)
       (assoc :rebound/off? offense?)
       (assoc :rebound/player rebounder))))


(re-frame/reg-event-db
 ::set-team-reb?
 [(re-frame/path [:action])
  re-frame/trim-v]
 (fn [action [offense? team-reb?]]
   (-> action
       (dissoc :rebound/player)
       (assoc :rebound/off? offense?
              :rebound/team? team-reb?))))


(re-frame/reg-event-db
 ::set-init-team
 [re-frame/trim-v]
 (fn [db [team]]
   (-> db
       (assoc-in [:init :init/team] team)
       (dissoc :action))))


(re-frame/reg-event-fx
 ::next-period
 [cofx/inject-ds]
 (fn [{:keys [ds db]} _]
   {:db
    (-> db
        (assoc-in [:init :init/period]
                  (-> (ds/last-possession ds (:game-id db))
                      (get :possession/period 0)
                      inc))
        (dissoc :action))}))


(re-frame/reg-event-db
 ::undo-next-period
 (fn [db _]
   (dissoc db :init)))


;; TODO - add on-court-ft to action as ft/offense/defense in append-action...
(re-frame/reg-event-fx
 ::add-action
 [cofx/inject-ds
  interceptors/ds->local-storage]
 (fn [{:keys [ds db]} _]
   (let [{:keys [action game-id players init]} db
         tx-data [[:db.fn/call ds/append-action-tx-data game-id action players init]]
         new-ds (d/db-with ds tx-data)]
     {:db (-> db
              (dissoc :init :action)
              (update :players
                      (fn [players-map]
                        (->> players-map
                             (map (fn [[team-id players]]
                                    (let [next-on-court (get players :on-court-ft (:on-court players))
                                          next-on-bench (get players :on-bench-ft (:on-bench players))]
                                      [team-id (-> players
                                                   (dissoc :on-court-ft :on-bench-ft)
                                                   (assoc :on-court next-on-court
                                                          :on-bench next-on-bench))])))
                             (into {})))))
      ::fx/ds new-ds})))


#_{:clj-kondo/ignore [:not-empty?]}
(re-frame/reg-event-fx
 ::undo-last-action
 [cofx/inject-ds
  interceptors/ds->local-storage]
 (fn [{:keys [ds db]} _]
   (if (some? (:action db))
     {:db (dissoc db :action)}
     (let [{:keys [game-id]} db
           new-ds (d/db-with ds [[:db.fn/call ds/undo-last-action-tx-data game-id]])]
       {::fx/ds new-ds}))))


;; TODO - use interceptor to dissoc action fields that used the benched player
(re-frame/reg-event-db
 ::put-player-to-bench
 (fn [db [_ team-id player]]
   (let [action-player (get-in db [:action :action/player])
         rebounder (get-in db [:action :rebound/player])]
     (cond-> db
       true (update-in [:players team-id :on-court] (fnil disj #{}) player)
       true (update-in [:players team-id :on-bench] (fnil conj #{}) player)
       ;; (= player action-player) (update :action dissoc :action/player) ;; TODO - this doesn't work b/c it doesn't do an offense? check
       ))))


(re-frame/reg-event-db
 ::put-player-to-court
 (fn [db [_ team-id player]]
   (-> db
       (update-in [:players team-id :on-court] (fnil conj #{}) player)
       (update-in [:players team-id :on-bench] (fnil disj #{}) player))))


(re-frame/reg-event-db
 ::put-player-to-ft-bench
 (fn [db [_ team-id player]]
   (-> db
       (update-in [:players team-id :on-court-ft] (fnil disj #{}) player)
       (update-in [:players team-id :on-bench-ft] (fnil conj #{}) player))))


(re-frame/reg-event-db
 ::put-player-to-ft-court
 (fn [db [_ team-id player]]
   (-> db
       (update-in [:players team-id :on-court-ft] (fnil conj #{}) player)
       (update-in [:players team-id :on-bench-ft] (fnil disj #{}) player))))


(re-frame/reg-event-db
 ::add-player
 (fn [db [_ team-id player]]
   (update-in db [:players team-id :on-bench] (fnil conj #{}) player)))


(re-frame/reg-event-fx
 ::update-team-name
 [cofx/inject-ds]
 (fn [{:keys [ds]} [_ team-id team-name]]
   (try
     ;; db/db-with may error because of upsert conflicts (two teams with the same name)
     {::fx/ds (d/db-with ds [{:db/id team-id
                              :team/name team-name}])}
     (catch js/Object e))))


(re-frame/reg-event-db
 ::delete-bench-player
 (fn [db [_ team-id player]]
   (update-in db [:players team-id :on-bench] (fnil disj #{}) player)))


(re-frame/reg-event-fx
 ::new-game
 (fn [_ _]
   {:db db/init-db
    :fx [[:dispatch [::initialize-blank-ds]]]}))


(re-frame/reg-event-db
 ::add-ft-attempted
 (fn [db _]
   (-> db
       (update-in [:action :ft/attempted] inc)
       (update-in [:action :ft/results] conj false))))


(re-frame/reg-event-db
 ::pop-ft-attempted
 [interceptors/rebound
  interceptors/ftm]
 (fn [db _]
   (-> db
       (update-in [:action :ft/attempted] dec)
       (update-in [:action :ft/results] pop))))


(re-frame/reg-event-db
 ::set-ft-result
 [interceptors/rebound
  interceptors/ftm
  re-frame/trim-v]
 (fn [db [idx make?]]
   (update-in db [:action :ft/results] assoc idx make?)))


(re-frame/reg-event-db
 ::toggle-sub?
 (fn [db _]
   (update-in db [:sub?] not)))

