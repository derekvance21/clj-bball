(ns bball.core
  (:require
   [bball.parser :as p]
   [bball.db :as db]
   [bball.query :as q]
   [datomic.api :as d]
   [datomic.client.api :as client.d]
   [clojure.edn :as edn]
   [datascript.core :as datascript]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.defaults :as defaults]
   [compojure.core :refer [defroutes GET]]
   [compojure.route :as route])
  (:gen-class))


;; (comment
;;   (def client
;;     (d/client
;;      {:server-type :peer-server
;;       :endpoint "localhost:8998"
;;       :secret "mysecret"
;;       :access-key "myaccesskey"
;;       :validate-hostnames false}))
;;   ;; (d/create-database client {:db-name "games"}) ;; NOT AVAILABLE WITH PEER SERVER  
;;   (def client-conn (d/connect client {:db-name "games"})))

;; (def client
;;   (d/client
;;    {:server-type :peer-server
;;     :endpoint "localhost:8998"
;;     :secret "mysecret"
;;     :access-key "myaccesskey"
;;     :validate-hostnames false}))
;; (def client-conn (d/connect client {:db-name "games"}))
;; (d/q '[:find ?g (pull ?t [:team/name]) (sum ?pts)
;;        :in $ %
;;        :with ?a
;;        :where
;;        (actions ?g ?t ?p ?a)
;;        (pts ?a ?pts)]
;;      (d/db client-conn)
;;      q/rules)


(def db-uri "datomic:mem://games")


(d/create-database db-uri)


(def conn (d/connect db-uri))


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


;; (comment
;;   (d/transact conn {:tx-data db/schema}))


;; (comment
;;   (defn datom->tx
;;     [datom schema]
;;     (let [{:keys [e a v]} datom
;;           ref? (and (= :db.type/ref (get-in schema [a :db/valueType]))
;;                     (number? v))]
;;       [:db/add (- e) a (cond
;;                          ref? (- v)
;;                          (coll? v) (vec v)
;;                          :else v)]))


;;   @(def ds-db (edn/read-string {:readers datascript/data-readers} (slurp "games/2023-03-04-lynden-christian-nooksack-valley.edn")))


;;   @(def lc-nv-tx-lists
;;      (let [schema-ds-format (into {} (map (juxt :db/ident identity) db/schema))]
;;        (->>
;;         (datascript/datoms ds-db :eavt)
;;         (filter (fn [{:keys [a]}] (contains? schema-ds-format a)))
;;         (map #(datom->tx % schema-ds-format)))))


;;   (d/transact conn {:tx-data lc-nv-tx-lists}))


;; (comment
;;   (defn file->tx
;;     [file]
;;     (-> file slurp edn/read-string p/parse))


;;   @(def games (map file->tx ["games/2022-09-04-Vegas-Seattle.edn"
;;                              "games/2022-09-06-Vegas-Seattle.edn"
;;                              "games/2022-02-05-Blaine-Ferndale.edn"]))


;;   (d/transact conn {:tx-data games}))


;; (comment
;;   (->> (d/q '[:find ?g (pull ?t [:team/name]) (sum ?pts)
;;               :in $ %
;;               :with ?a
;;               :where
;;               (actions ?g ?t ?p ?a)
;;               (pts ?a ?pts)]
;;             (d/db conn)
;;             q/rules)
;;        (sort-by #(nth % 2) >)
;;        (sort-by first)))





;; (comment
;;   @(def datascript-tx-data (datomic->datascript-tx-data (d/db conn)))


;;   @(def datascript-schema
;;      (->> db/schema
;;           (remove #(= "action.type" (namespace (:db/ident %)))) ;; removes enums
;;           (map
;;            (fn [{:db/keys [ident valueType] :as sch}]
;;              [ident
;;               (select-keys sch [:db/cardinality :db/unique :db/index :db/tupleAttrs :db/isComponent :db/doc
;;                                 (when (and (= valueType :db.type/ref) (not= "type" (name ident)))
;;                                   :db/valueType)])]))
;;           (into {})))


;;   @(def datascript-db
;;      (datascript/db-with
;;       (datascript/empty-db datascript-schema)
;;       datascript-tx-data))


;;   (->> (datascript/q
;;         '[:find ?g (pull ?t [:team/name]) (sum ?pts)
;;           :in $ %
;;           :with ?a
;;           :where
;;           (actions ?g ?t ?p ?a)
;;           (pts ?a ?pts)]
;;         datascript-db q/rules)
;;        (sort-by #(nth % 2) >)
;;        (sort-by first)))


(defroutes app-routes
  (GET "/" [] "<html><head><title>clj-bball</title></head><body><div style=\"height: 90vh; display: flex; flex-direction: column; justify-content: center; align-items: center\"><p style=\"font-family: sans-serif; \">Hello, Ring!</p></div></body></html>")
  (route/files "/" {:root "resources/public"})
  #_(GET "/db" [] {:headers {"Access-Control-Allow-Origin" "*"
                             "Content-Type" "application/edn"}
                   :body (comment "DB WILL GO HERE")})
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
