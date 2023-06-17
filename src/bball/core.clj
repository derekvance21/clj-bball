(ns bball.core
  (:require
   [bball.db :as db]
   [bball.env :as env]
   [clojure.edn :as edn]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.defaults :as defaults]
   [compojure.core :refer [defroutes GET]]
   [compojure.route :as route])
  (:gen-class))


(defroutes app-routes
  (GET "/" [] "<html><head><title>clj-bball</title></head><body><div style=\"height: 90vh; display: flex; flex-direction: column; justify-content: center; align-items: center\"><p style=\"font-family: sans-serif; \">Hello, Ring!</p></div></body></html>")
  (route/files "/" {:root "resources/public"})
  (GET "/db" [] {:headers {"Access-Control-Allow-Origin" "*"
                           "Content-Type" "application/edn"}
                 :body (pr-str (db/datomic->datascript-db (db/get-db)))})
  (route/not-found "Not Found"))


(def app
  (defaults/wrap-defaults #'app-routes defaults/site-defaults))


(defn start
  []
  (jetty/run-jetty #'app {:port (parse-long (env/env :APP_SERVER_PORT))
                          :join? false
                          ;; (from jetty docs) :host - If null or 0.0.0.0, then bind to all interfaces.
                          }))


(defn -main [& args]
  (let [opts (apply hash-map (map edn/read-string args))
        {:keys [dev]} opts]
    (println (str "Serving app at port" (parse-long (env/env :APP_SERVER_PORT)) (when dev " [DEV]")))
    (start)))


(comment
  (def server (start))
  (.stop server)
  (.start server))

