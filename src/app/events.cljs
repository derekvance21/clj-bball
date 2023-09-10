(ns app.events
  (:require
   [re-frame.core :as re-frame]
   [app.datascript :as datascript]
   [app.db :as db]
   [app.coeffects :as cofx]
   [app.effects :as fx]
   [app.interceptors :as interceptors]
   [cljs.reader :as reader]
   [datascript.core :as d]
   [bball.game-utils :as game-utils]
   [app.env :as env]
   [clojure.pprint :as pprint]
   [clojure.string :as string]))


(def blaine-games
  ["2022-12-06-Blaine-Bellingham.edn"
   "2022-12-10-Oak-Harbor-Blaine.edn"
   "2022-12-14-Blaine-Lakewood.edn"
   "2022-12-16-Squalicum-Blaine.edn"
   "2023-01-02-Nooksack-Valley-Blaine.edn"
   "2023-01-05-Sedro-Woolley-Blaine.edn"
   "2023-01-10-Blaine-Meridian.edn"
   "2023-01-12-Blaine-Lynden.edn"
   "2023-01-16-Mount-Baker-Blaine.edn"
   "2023-01-20-Blaine-Burlington-Edison.edn"
   "2023-01-23-Mount-Vernon-Blaine.edn"
   "2023-01-26-Blaine-Lynden-Christian.edn"
   "2023-01-28-Blaine-Anacortes.edn"
   "2023-02-01-Sehome-Blaine.edn"
   "2023-02-02-Blaine-Ferndale.edn"
   "2023-02-08-Blaine-Meridian.edn"
   "2023-02-11-Lynden-Christian-Blaine.edn"
   "2023-02-14-Blaine-Nooksack-Valley.edn"
   "2023-02-18-Northwest-Blaine.edn"
   "2023-02-25-Zillah-Blaine.edn"])


(re-frame/reg-event-fx
 ::load-remote-game
 (fn [_ [_ remote-file]]
   {::fx/fetch {:resource (str env/URL "/games/" remote-file)
                :options {:method :GET}
                :on-success (fn [text]
                              (let [tx-map (try
                                             (reader/read-string text)
                                             (catch js/Object e
                                               (.error js/console e)))]
                                (when (map? tx-map)
                                  (re-frame/dispatch [::transact-game-tx-map tx-map]))))
                :on-failure (fn [error]
                              (.error js/console error))}}))


(re-frame/reg-event-fx
 ::transact-game-tx-map
 [cofx/inject-ds]
 (fn [{:keys [ds]} [_ tx-map]]
   {::fx/ds (d/db-with ds [tx-map])}))


(re-frame/reg-event-fx
 ::initialize
 (fn [_ _]
   ;; TODO - this is a problem to fetch this and then have to wait for it to come back
   ;; b/c you might end up waiting ~10s+ for the response to come back
   ;; so do you:
   ;; 1. only send over game transaction maps and then transact them in the background?
   ;; 2. do something else?
   {:db db/init-db
    :fx (into [[::fx/ds (datascript/empty-db)]
               [:dispatch [::transact-local-storage-game]]]
              (map #(vector :dispatch [::load-remote-game %]) blaine-games))}))


;; TODO - there needs to be much better error checking here
;; ls-game could be stale or invalid but still some? and map?
;; which would put the app in a crumby state
(re-frame/reg-event-fx
 ::transact-local-storage-game
 [cofx/inject-ds
  (re-frame/inject-cofx ::cofx/local-storage-game)]
 (fn [{:keys [ds db ls-game]} _]
   (if (and (some? ls-game) (map? ls-game))
     (let [{:keys [db-after tempids]} (d/with ds [(assoc ls-game :db/id -1)])
           game-id (get tempids -1)
           players (datascript/get-players-map db-after game-id)]
       {:db (cond-> db
              true (assoc :game-id game-id :players players)
              (seq (:game/possession ls-game)) (dissoc :init))
        ::fx/ds db-after})
     (when-not (contains? db :game-id)
       {:fx [[:dispatch [::start-new-game]]]}))))


(re-frame/reg-event-fx
 ::start-new-game
 [cofx/inject-ds]
 (fn [{:keys [ds db]} _]
   (let [game-tempid -1
         start-tx-map [{:db/id game-tempid :game/home-team {:team/name "Home"} :game/away-team {:team/name "Away"}}]
         {:keys [db-after tempids]} (d/with ds start-tx-map)
         game-id (get tempids game-tempid)]
     ;; [console warning] re-frame: ":fx" effect should not contain a :db effect
     {:fx [[::fx/ds db-after]
           [:db (-> db
                    (assoc :game-id game-id :init {:init/period 1})
                    (dissoc :players))]]})))


(re-frame/reg-event-fx
 ::set-game
 [cofx/inject-ds]
 (fn [{:keys [ds db]} [_ game-id]]
   (let [players (datascript/get-players-map ds game-id)]
     {:db (assoc db :game-id game-id :players players)})))


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
  interceptors/ft-results ;; TODO - this doesn't work if you reselect bonus, because there's no change (onchanges) to ft/attempted, as it's still 1
  (re-frame/path [:action])]
 (fn [action _]
   (-> action
       (select-keys [:action/player])
       (assoc :action/type :action.type/bonus
              :ft/attempted 1
              :ft/results [false]))))


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
                  (-> (datascript/last-possession ds (:game-id db))
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
         tx-data [[:db.fn/call datascript/append-action-tx-data game-id action players init]]
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
           new-ds (d/db-with ds [[:db.fn/call datascript/undo-last-action-tx-data game-id]])]
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
 (fn [{:keys [action] :as db} [_ team-id player]]
   (cond-> db
     true (update-in [:players team-id :on-bench] (fnil conj #{}) player)
     (db/ft? action) (update-in [:players team-id :on-bench-ft] (fnil conj #{}) player))))


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


(re-frame/reg-event-fx
 ::submit-game
 [cofx/inject-ds]
 (fn [{:keys [ds db]}]
   {::fx/fetch
    {:url (str env/URL "/db")
     :method "POST"
     :body [(game-utils/datascript-game->tx-map ds (:game-id db))]
     :on-success (fn [text]
                   (def remote-db (reader/read-string text)))
     :on-failure println}}))


(re-frame/reg-event-db
 ::set-shot-chart-players-input
 (fn [db [_ players-input]]
   (assoc-in db [:shot-chart :players-input] players-input)))


(re-frame/reg-event-db
 ::set-shot-chart-offense-input
 (fn [db [_ offense-input]]
   (assoc-in db [:shot-chart :offense-input] offense-input)))


(re-frame/reg-event-db
 ::set-shot-chart-teams
 (fn [db [_ team-ids]]
   (assoc-in db [:shot-chart :teams] team-ids)))

(re-frame/reg-event-db
 ::set-shot-chart-games
 (fn [db [_ games-set]]
   (assoc-in db [:shot-chart :games] games-set)))

(re-frame/reg-event-db
 ::toggle-show-shots?
 (fn [db _]
   (update-in db [:shot-chart :show-shots?] not)))

(re-frame/reg-event-db
 ::toggle-show-zones?
 (fn [db _]
   (update-in db [:shot-chart :show-zones?] not)))

(re-frame/reg-event-db
 ::zone-by
 (fn [db [_ type]]
   (assoc-in db [:shot-chart :zone-by] type)))

(re-frame/reg-event-db
 ::set-active-panel
 (fn [db [_ panel]]
   (assoc db :active-panel panel)))


(re-frame/reg-event-fx
 ::download-game
 [cofx/inject-ds]
 (fn [{:keys [ds db]} _]
   (let [{:keys [game-id]} db
         {:game/keys [home-team away-team] :as game} (game-utils/datascript-game->tx-map ds game-id)
         yyyy-mm-dd (first (string/split (.toISOString (js/Date.)) #"T"))
         filename (str yyyy-mm-dd
                       "-" (:team/name home-team)
                       "-" (:team/name away-team)
                       ".edn")
         data (with-out-str (pprint/pprint game))]
     {::fx/download {:filename filename
                     :data data}})))
