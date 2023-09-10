(ns app.effects
  (:require
   [re-frame.core :as re-frame]
   [app.datascript :as ds]
   [datascript.core :as d]))


(re-frame/reg-fx
 ::ds
 (fn [db]
   (when (and (not
               (identical? @ds/conn db) ;; identical? != = . ((juxt = identical?) db (d/db-with db [])) => [true false]
               )
              (d/db? db))
     (reset! ds/conn db) ;; d/reset-conn! has to produce a transaction report, but underneath just runs reset!
     )))


(re-frame/reg-fx
 ::fetch
 (fn [{:keys [resource options on-success on-failure]}]
   (-> (js/fetch resource (clj->js options))
       (.then (fn [response] (.text response)))
       (.then (fn [text] (on-success text)))
       (.catch (fn [error] (on-failure error))))))


(re-frame/reg-fx
 ::download
 (fn [{:keys [filename data]}]
   (let [url (.createObjectURL js/URL (js/File. [data] filename))]
     (doto (.createElement js/document "a")
       (.setAttribute "download" filename)
       (.setAttribute "href" url)
       (.click))
     (.revokeObjectURL js/URL url))))
