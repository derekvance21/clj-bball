(ns app.fx
  (:require
   [re-frame.core :as re-frame]
   [app.ds :as ds]
   [datascript.core :as datascript]))


(re-frame/reg-fx
 ::ds
 (fn [value]
   (when-not (identical? @ds/conn value)
     (datascript/reset-conn! ds/conn value))))

