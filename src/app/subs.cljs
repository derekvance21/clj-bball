(ns app.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::possessions
 (fn [db]
   (get-in db [:game :game/possession])))

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

(re-frame/reg-sub
 ::team
 :-> :team)

(re-frame/reg-sub
 ::score
 (fn [db]
   (get-in db [:game :score])))

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
