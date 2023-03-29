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
   (fn [[g] ?event?]
     (db/possessions @g))))
(defn reg-sub-raw-10x
  ([id handler-fn]
   nil)
  ([id signals-fn comp-fn]
   nil))

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
(re-frame/reg-sub-raw
 ::possessions
 (fn [app-db event]
   (let [g (re-frame/subscribe [::game-id])
         reaction-id (atom nil)
         reaction (ratom/make-reaction
                   (fn []
                     (trace/with-trace
                       {:operation (first event)
                        :op-type :sub/run
                        :tags {:query-v event
                               :reaction @reaction-id}}
                       (let [subscription (db/possessions @g)]
                         (trace/merge-trace! {:tags {:value subscription}})
                         subscription))))]
     (reset! reaction-id (interop/reagent-id reaction))
     reaction)))

(re-frame/reg-sub
 ::sorted-possessions
 :<- [::possessions]
 :-> (partial sort-by :possession/order))

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
(re-frame/reg-sub-raw
 ::score
 (fn [db event]
   (let [g (re-frame/subscribe [::game-id])]
     (reagent/reaction (db/score @g)))))

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
    (re-frame/subscribe [::ft-made])])
 (fn [[make? foul? fta ftm] _]
   (or (and foul? (< ftm fta))
       (and (not foul?) (not make?)))))
