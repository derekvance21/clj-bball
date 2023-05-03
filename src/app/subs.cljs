(ns app.subs
  (:require
   [re-frame.core :as re-frame]
   [datascript.core :as d]
   [app.datascript :as ds]
   [app.db :as db]))


(re-frame/reg-sub
 ::datascript-db
 (fn [query-vec dynamic-vec]
   ds/conn)
 (fn [datascript-db query-vec]
   datascript-db))


(re-frame/reg-sub
 ::ppp
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] query-vec]
   (->> (ds/ppp db g)
        (map (fn [[t pts nposs]]
               [t (/ pts nposs)]))
        (into {}))))


(re-frame/reg-sub
 ::team-ppp
 :<- [::ppp]
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
 ::teams
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
 ::ft-attempted
 (fn [db]
   (get-in db [:action :ft/attempted])))


(re-frame/reg-sub
 ::foul?
 (fn [db]
   (get-in db [:action :shot/foul?])))


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
 :<- [::action-type]
 :<- [::action-player]
 :<- [::reboundable?]
 (fn [[type player reboundable?] _]
   (and (some? type) (not= type :action.type/technical) (some? player) reboundable?)))

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
