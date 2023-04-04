(ns app.subs
  (:require
   [re-frame.core :as re-frame]
   [re-frame.trace :as trace]
   [re-frame.interop :as interop]
   [reagent.core :as reagent]
   [reagent.ratom :as ratom]
   [app.db :as db]))


;; TODO - this would require some work but would be cool
;;        it would be a function similar to reg-sub which would allow you to define
;;        input signals and such, then wrap it with make-reaction and with-trace
;;        to make it visible in re-frame-10x
;; I want to be able to write it like:
(comment
  (reg-sub-raw-10x
   ::possessions
   (fn [_]
     [(re-frame/subscribe [::game-id])])
   (fn [[g] query-vec]
     (db/possessions @g))))


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
   (get-in db [:game :db/id])))


(re-frame/reg-sub
 ::team-has-possession?
 (fn [_]
   (re-frame/subscribe [::team]))
 (fn [team [_ t]]
   (and t (= (:db/id team) t))))


(re-frame/reg-sub
 ::team
 :-> :team)


;; HERE - I've copied the logic in re-frame.subs/reg-sub, attempting to match it's call to register-handler it makes
;;        using make-reaction, trace/with-trace, and trace/merge-trace!
;;        This allows me to see the result of the sub in the re-frame 10x debug menu.
;;        Although, I am getting some wildly slow startup times for the app on refresh
;; (re-frame/reg-sub-raw
;;  ::possessions
;;  (fn [app-db event]
;;    (let [g (re-frame/subscribe [::game-id])
;;          reaction-id (atom nil)
;;          reaction (ratom/make-reaction
;;                    (fn []
;;                      (trace/with-trace
;;                        {:operation (first event)
;;                         :op-type :sub/run
;;                         :tags {:query-v event
;;                                :reaction @reaction-id}}
;;                        (let [subscription (db/possessions @g)]
;;                          (trace/merge-trace! {:tags {:value subscription}})
;;                          subscription))))]
;;      (reset! reaction-id (interop/reagent-id reaction))
;;      reaction)))


(re-frame/reg-sub
 ::possessions
 :<- [::datascript-db]
 :<- [::game-id]
 (fn [[db g] _]
   (db/possessions db g)))


(re-frame/reg-sub
 ::sorted-possessions
 :<- [::possessions]
 :-> (partial sort-by :possession/order >))


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
 (fn [db]
   (get-in db [:game :game/teams])))


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
 :-> :period)


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
