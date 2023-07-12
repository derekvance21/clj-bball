(ns bball.core
  (:require
   [datomic.api :as datomic]
   [bball.db :as db]
   [bball.env :as env]
   [clojure.edn :as edn]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.defaults :as defaults]
   [ring.util.request :as request]
   [ring.util.response :as response]
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route])
  (:gen-class))


(defn wrap-cors
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (-> resp
          (response/header "Access-Control-Allow-Origin" "*")))))


(defroutes app-routes
  (GET "/" [] "<html><head><title>clj-bball</title></head><body><div style=\"height: 90vh; display: flex; flex-direction: column; justify-content: center; align-items: center\"><p style=\"font-family: sans-serif; \">Hello, Ring!</p></div></body></html>")
  (route/files "/" {:root "resources/public"})
  (wrap-cors (route/files "/games/" {:root "resources/games"}))
  (GET "/db" [] {:headers {"Access-Control-Allow-Origin" "*"
                           "Content-Type" "application/edn"}
                 :body (pr-str (db/datomic->datascript-db (db/get-db)))})
  (POST "/db" [] (fn [req]
                   (let [tx-data (edn/read-string (request/body-string req))
                         tx-result (deref (datomic/transact (db/get-connection) tx-data))
                         db-after (:db-after tx-result)]
                     {:body (pr-str (db/datomic->datascript-db db-after))
                      :headers {"Access-Control-Allow-Origin" "*"}})))
  (route/not-found "Not Found"))


(def app
  (defaults/wrap-defaults #'app-routes (assoc-in defaults/site-defaults [:security :anti-forgery] false)))


(defn start
  []
  (jetty/run-jetty #'app {:port (parse-long (env/env :APP_SERVER_PORT "8900"))
                          :join? false
                          ;; (from jetty docs) :host - If null or 0.0.0.0, then bind to all interfaces.
                          }))


(defn -main [& args]
  (let [opts (apply hash-map (map edn/read-string args))
        {:keys [dev]} opts]
    (println (str "Serving app at port " (parse-long (env/env :APP_SERVER_PORT "8900")) (when dev " [DEV]")))
    (start)))


(comment
  (def server (start))
  (.stop server)
  (.start server))

