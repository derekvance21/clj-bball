(ns app.effects
  (:require
   [re-frame.core :as re-frame]
   [app.datascript :as ds]))


(re-frame/reg-fx
 ::ds
 (fn [db]
   (when-not (identical? @ds/conn db)
     (reset! ds/conn db) ;; d/reset-conn! has to produce a transaction report, but underneath just runs reset!
     )))

