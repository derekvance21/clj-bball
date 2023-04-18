(ns app.cofx
  (:require
   [app.ds :as ds]
   [datascript.core :as d]
   [re-frame.core :as re-frame]))


(re-frame/reg-cofx
 ::local-storage-datascript-db
 (fn [cofx _]
   (let [ls-db (ds/local-storage->db)]
     (if (d/db? ls-db)
       (assoc cofx :ls-datascript-db ls-db)
       cofx))))


(re-frame/reg-cofx
 ::ds
 (fn [cofx _]
   (assoc cofx :ds @ds/conn)))


(def inject-ds (re-frame/inject-cofx ::ds))

