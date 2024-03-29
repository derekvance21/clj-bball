(ns bball.core
  (:require
   [clojure.java.io :as io]
   [bball.env :as env]
   [clojure.edn :as edn]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.defaults :as defaults]
   [ring.util.response :as response]
   [compojure.core :refer [defroutes GET]]
   [compojure.route :as route])
  (:gen-class))


(defn wrap-cors
  [handler]
  (fn [req]
    (response/header (handler req) "Access-Control-Allow-Origin" "*")))


(defn folder-files
  [folder]
  (->> (file-seq (io/file folder))
       (filter #(.isFile %))))


(defn parse-edn
  [file]
  (with-open [r (clojure.java.io/reader file)]
    (clojure.edn/read (java.io.PushbackReader. r))))


(defroutes app-routes
  (GET "/" []
    (-> (response/resource-response "public/index.html")
        (response/content-type "text/html")))
  (GET "/games" []
    (-> (response/resource-response "games.edn") #_(response/response (pr-str (mapv parse-edn (folder-files "resources/games"))))
        (response/content-type "application/edn")
        (response/header "Access-Control-Allow-Origin" "*")))
  (route/not-found "Not Found"))


(def app
  (defaults/wrap-defaults #'app-routes defaults/site-defaults))


(def port (parse-long (env/env :APP_SERVER_PORT "8900")))


(defn start
  []
  (jetty/run-jetty #'app {:port port
                          :join? false
                          ;; (from jetty docs) :host - If null or 0.0.0.0, then bind to all interfaces.
                          }))


(defn -main [& args]
  (let [opts (apply hash-map (map edn/read-string args))
        {:keys [dev]} opts]
    (println (str "Serving app at port http://localhost:" port))
    (start)))


(comment
  (def server (start))
  (.stop server)
  (.start server))

