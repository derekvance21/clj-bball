(ns app.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [app.events :as events]
   [app.views :as views]
   [app.config :as config]))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn ^:export init []
  (re-frame/dispatch-sync [::events/initialize])
  (dev-setup)
  (mount-root))


(comment
  (def remote-db (atom nil))

  (-> (js/fetch "http://localhost:8008/db")
      (.then (fn [resp] (let [entries (.entries (.-headers resp))]
                          (println (.next entries))
                          (println (.next entries))) (.text resp)))
      (.then (fn [text] (reset! remote-db (cljs.reader/read-string text))))
      (.catch (fn [error] (println error))))

  (->>
   (datascript.core/q
    '[:find ?g ?team (sum ?pts)
      :in $ %
      :with ?a
      :where
      (actions ?g ?t ?p ?a)
      [?t :team/name ?team]
      (pts ?a ?pts)]
    @remote-db
    bball.query/rules)
   (sort-by #(nth % 2) >)
   (sort-by first))
  )