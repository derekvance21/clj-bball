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
     "resources/games/2023-02-18-Northwest-Blaine.edn"
     "resources/games/2023-02-14-Blaine-Nooksack-Valley.edn"
     "resources/games/2023-02-11-Lynden-Christian-Blaine.edn"])


  (for [db-tx-data (map (comp datascript->datomic-tx-data
                              (partial edn/read-string {:readers datascript/data-readers})
                              slurp)
                        db-game-files)]
    (d/transact conn {:tx-data db-tx-data}))


  ;; WARNING - these do not have distance data!
  (def parser-game-files
    ["resources/games/2022-09-04-Vegas-Seattle.edn"
     "resources/games/2022-09-06-Vegas-Seattle.edn"
     "resources/games/2022-02-05-Blaine-Ferndale.edn"])


  (d/transact conn {:tx-data (map (comp parser/parse edn/read-string slurp) parser-game-files)})


  (def score-query '[:find ?g ?team (sum ?pts)
                     :in $ %
                     :with ?a
                     :where
                     (actions ?g ?t ?p ?a)
                     (pts ?a ?pts)
                     [?t :team/name ?team]])


  [(d/q score-query (d/db conn) query/rules)
   (datascript/q score-query (datomic->datascript-db (d/db conn)) query/rules)]
  

  (->> (d/q '[:find ?team ?numbers ?sector (avg ?pts) (count ?a)
              :in $ % [[?team ?numbers] ...]
              :where
              (actions ?g ?t ?p ?a)
              [?a :shot/distance ?inches]
              [?a :shot/value ?value]
              (sector ?value ?inches ?sector)
              (pts ?a ?pts)
              [?t :team/name ?team]
              [?a :action/player ?player]
              [(contains? ?numbers ?player)]]
            (d/db conn)
            query/rules
            (map vector (repeat "Blaine") [#{1} #{2 5} #{3} #{4} #{35} #{42}]))
       (sort-by (fn [[_ _ sector _]] (->> ["0-3" "3-10" "10-3P" "3P-24" "24+"]
                                          (map-indexed vector)
                                          (filter #(= sector (second %)))
                                          ffirst)))
       (sort-by (comp first second)))
  ;; => (["Blaine" #{1} "0-3" 1.4285714285714286 14]
  ;;     ["Blaine" #{1} "3-10" 0.6363636363636364 11]
  ;;     ["Blaine" #{1} "10-3P" 0.2857142857142857 7]
  ;;     ["Blaine" #{1} "3P-24" 0.7058823529411765 17]

  ;;     ["Blaine" #{2 5} "0-3" 2.0 3]
  ;;     ["Blaine" #{2 5} "3-10" 0.0 1]
  ;;     ["Blaine" #{2 5} "3P-24" 1.0714285714285714 14]

  ;;     ["Blaine" #{3} "0-3" 1.4 10]
  ;;     ["Blaine" #{3} "3-10" 1.2380952380952381 21]
  ;;     ["Blaine" #{3} "10-3P" 1.0 2]
  ;;     ["Blaine" #{3} "3P-24" 1.5 14]

  ;;     ["Blaine" #{4} "0-3" 1.6666666666666667 3]
  ;;     ["Blaine" #{4} "3-10" 0.6666666666666666 6]
  ;;     ["Blaine" #{4} "3P-24" 0.7692307692307693 13]

  ;;     ["Blaine" #{35} "0-3" 2.0 4]
  ;;     ["Blaine" #{35} "3-10" 1.2222222222222223 9]
  ;;     ["Blaine" #{35} "10-3P" 0.2857142857142857 7]
  ;;     ["Blaine" #{35} "3P-24" 1.3636363636363635 11]
  ;;     ["Blaine" #{35} "24+" 0.8461538461538461 13]

  ;;     ["Blaine" #{42} "0-3" 1.1428571428571428 14]
  ;;     ["Blaine" #{42} "3-10" 0.0 4]
  ;;     ["Blaine" #{42} "3P-24" 1.2857142857142858 14])
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
