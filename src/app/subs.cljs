(ns app.subs
  (:require
   [re-frame.core :as re-frame]
   [cljs.pprint :as pp]))

(re-frame/reg-sub
 ::game-input
 (fn [db]
   (:game-input db)))

(re-frame/reg-sub
 ::game-object-string
 (fn [db]
   (-> db :game-object pp/pprint with-out-str)))

(re-frame/reg-sub
 ::game-score
 (fn [db]
   (:game-score db)))
