(ns app.subs
  (:require
   [re-frame.core :as re-frame]
   [app.db :as db]))


(defn reg-sub-raw-10x
  ([id handler-fn]
   nil)
  ([id signals-fn comp-fn]
   nil))


(re-frame/reg-sub
 ::datascript-db
 (fn [query-vec dynamic-vec]
   db/conn)
 (fn [datascript-db query-vec]
   datascript-db))


(re-frame/reg-sub
 ::ppp
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] query-vec]
   (->> (db/ppp db g)
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
   (->> (db/efg db g)
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
   (->> (db/off-reb-rate db g)
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
   (->> (db/turnover-rate db g)
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
   (->> (db/pps db g)
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
   (->> (db/ft-rate db g)
        (into {}))))


(re-frame/reg-sub
 ::team-ft-rate
 :<- [::ft-rate]
 (fn [ft-rate [_ t]]
   (get ft-rate t)))


(re-frame/reg-sub
 ::box-score
 :<- [::game-id]
 :<- [::datascript-db]
 (fn [[g db] query-vec]
   (reduce (fn [box-score [t player pts]]
             (update box-score t (fn [ppts] (conj ppts [player pts]))))
           {} (db/box-score db g))))


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
 (fn [[db g init]]
   (if (some? init)
     (get init :init/team)
     (db/team-possession db g))))


(re-frame/reg-sub
 ::possessions
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (db/possessions db g)))


(re-frame/reg-sub
 ::sorted-possessions
 :<- [::possessions]
 (fn [possessions _]
   (->> possessions
        (sort-by :possession/order >)
        (map #(update % :possession/action (partial sort-by :action/order >))))))


(re-frame/reg-sub
 ::possessions?
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (db/possessions? db g)))


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
     (db/teams db g))))


;; NOTE - the results of ALL (unmodified) reg-sub-raw subscriptions are not shown in re-frame 10x debugging menu
;;        they are always listed as "NOT-RUN", even if their subscriptions are successfully causing renders
;; (re-frame/reg-sub
;;  ::score
;;  (fn [db event]
;;    (let [g (re-frame/subscribe [::game-id])]
;;      (reagent/reaction (db/score @g)))))


(re-frame/reg-sub
 ::score
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] query-vec]
   (into {} (db/score db g))))


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
 ::shot-value
 (fn [db]
   (get-in db [:action :shot/value])))


(re-frame/reg-sub
 ::shot-distance
 (fn [db]
   (get-in db [:action :shot/distance])))


(re-frame/reg-sub
 ::ft-made
 (fn [db]
   (get-in db [:action :ft/made])))


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
   (get-in db [:action :shot/rebounder])))


(re-frame/reg-sub
 ::off-reb?
 (fn [db]
   (get-in db [:action :shot/off-reb?])))


(re-frame/reg-sub
 ::stealer
 (fn [db]
   (get-in db [:action :turnover/stealer])))


(re-frame/reg-sub
 ::reboundable?
 (fn [_]
   [(re-frame/subscribe [::shot-make?])
    (re-frame/subscribe [::foul?])
    (re-frame/subscribe [::ft-attempted])
    (re-frame/subscribe [::ft-made])
    (re-frame/subscribe [::action-type])])
 (fn [[make? foul? fta ftm type] _]
   (let [fts? (or foul? (= :action.type/bonus type))
         shot? (= type :action.type/shot)]
     (or (and fts? (< ftm fta))
         (and shot? (not fts?) (not make?))))))


(re-frame/reg-sub
 ::period
 :<- [::datascript-db]
 :<- [::game-id]
 :<- [::init-period]
 (fn [[db g init-period] _]
   (if (some? init-period)
     init-period
     (:possession/period (db/last-possession db g)))))


(re-frame/reg-sub
 ::players
 (fn [db _]
   (get db :players)))


(re-frame/reg-sub
 ::team-players
 :<- [::players]
 (fn [players [_ t]]
   (get players t
        [nil nil nil nil nil])))


(re-frame/reg-sub
 ::defense-players
 :<- [::players]
 :<- [::team]
 (fn [[players {t :db/id}] _]
   (->> (dissoc players t)
        vals
        first
        (filter some?))))


(re-frame/reg-sub
 ::offense-players
 :<- [::players]
 :<- [::team]
 (fn [[players {t :db/id}] _]
   (filter some? (get players t []))))


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

