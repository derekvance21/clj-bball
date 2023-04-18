(ns app.fx
  (:require
   [re-frame.core :as re-frame]
   [app.ds :as ds]
   [datascript.core :as datascript]))


(re-frame/reg-fx
 ::ds
 (fn [db]
   (when-not (identical? @ds/conn db)
     (datascript/reset-conn! ds/conn db))))

