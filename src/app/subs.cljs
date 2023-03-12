(ns app.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::actions
 (fn [db]
   (:actions db)))

(re-frame/reg-sub
 ::action-input
 (fn [db]
   (get db :action-input)))

(re-frame/reg-sub
 ::teams
 (fn [db]
   (get-in db [:game :teams])))

(re-frame/reg-sub
 ::score
 (fn [db]
   (get-in db [:game :score])))