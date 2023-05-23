(ns bball.core
  (:require
   [bball.parser :as parser]
   [bball.db :as db]
   [bball.query :as query]
   [datomic.client.api :as d]
   [clojure.edn :as edn]
   [datascript.core :as datascript]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.defaults :as defaults]
   [compojure.core :refer [defroutes GET]]
   [compojure.route :as route])
  (:gen-class))


(def client (d/client {:server-type :peer-server
                       :access-key "key"
                       :secret "secret"
                       :endpoint "localhost:4336"
                       :validate-hostnames false}))


(def conn (d/connect client {:db-name "db"}))


(defn datomic->datascript-tx-data
  [db]
  (d/q '[:find ?add ?tempid ?attr ?value
         :in $ % [?attr ...]
         :where
         [?e ?a ?v ?tx ?b]
         [(true? ?b)]
         [?a :db/ident ?attr]
         (value ?a ?v ?value)
         [(- ?e) ?tempid]
         [(ground :db/add) ?add]]
       db
       '[[(value ?a ?v ?value)
          [?a :db/valueType :db.type/ref]
          (ref-value ?v ?value)]
         [(value ?a ?v ?value)
          (not [?a :db/valueType :db.type/ref])
          [(identity ?v) ?value]]
         [(ref-value ?v ?value)
          [(missing? $ ?v :db/ident)]
          [(- ?v) ?value]]
         [(ref-value ?v ?value)
          [?v :db/ident ?value]]]
       (map :db/ident db/schema)))


(defn datomic->datascript-db
  [db]
  (datascript/db-with
   (datascript/empty-db (db/datomic->datascript-schema db/schema))
   (datomic->datascript-tx-data db)))


(defn datascript->datomic-tx-data
  [db]
  (let [schema (->> db/schema
                    (map (juxt :db/ident identity))
                    (into {}))
        datom->tx (fn [{:keys [e a v]}]
                    (let [ref? (and (= :db.type/ref (get-in schema [a :db/valueType]))
                                    (number? v))]
                      [:db/add (- e) a (cond
                                         ref? (- v)
                                         (coll? v) (vec v)
                                         :else v)]))]
    (->> (datascript/datoms db :eavt)
         (filter (fn [{:keys [a]}] (contains? schema a)))
         (map datom->tx))))


(comment


  (d/transact conn {:tx-data db/schema})


  (def db-game-files
    ["resources/games/2023-03-04-lynden-christian-nooksack-valley.edn"
     "resources/games/2023-02-25-Zillah-Blaine.edn"
     "resources/games/2023-02-18-Northwest-Blaine.edn"])


  (for [db-tx-data (map (comp datascript->datomic-tx-data
                              (partial edn/read-string {:readers datascript/data-readers})
                              slurp)
                        db-game-files)]
    (d/transact conn {:tx-data db-tx-data}))


  (def edn-game-files
    ["resources/games/2022-09-04-Vegas-Seattle.edn"
     "resources/games/2022-09-06-Vegas-Seattle.edn"
     "resources/games/2022-02-05-Blaine-Ferndale.edn"])


  (d/transact conn {:tx-data (map (comp parser/parse edn/read-string slurp) edn-game-files)})


  (let [score-query
        '[:find ?g ?team (sum ?pts)
          :in $ %
          :with ?a
          :where
          (actions ?g ?t ?p ?a)
          (pts ?a ?pts)
          [?t :team/name ?team]]]
    [(d/q score-query (d/db conn) query/rules)
     (datascript/q score-query (datomic->datascript-db (d/db conn)) query/rules)])
  )


(defroutes app-routes
  (GET "/" [] "<html><head><title>clj-bball</title></head><body><div style=\"height: 90vh; display: flex; flex-direction: column; justify-content: center; align-items: center\"><p style=\"font-family: sans-serif; \">Hello, Ring!</p></div></body></html>")
  (route/files "/" {:root "resources/public"})
  (GET "/db" [] {:headers {"Access-Control-Allow-Origin" "*"
                           "Content-Type" "application/edn"}
                 :body (pr-str (datomic->datascript-db (d/db conn)))})
  (route/not-found "Not Found"))


(def app
  (defaults/wrap-defaults #'app-routes defaults/site-defaults))


(defn start
  []
  (jetty/run-jetty #'app {:port 8008 :join? false}))


(defn -main [& args]
  (let [opts (apply hash-map (map edn/read-string args))
        {:keys [dev]} opts]
    (println (str "starting app!" (when dev " [DEV]")))
    (start)))


(comment
  (def server (start))
  (.stop server)
  (.start server))
