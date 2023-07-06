(ns bball.db
  (:require
   [bball.env :as env]
   [datomic.api :as d]
   [datomic.client.api :as client.d]
   [clojure.edn :as edn]
   [datascript.core :as datascript]
   [bball.parser :as parser]
   [bball.schema :as schema]
   [bball.query :as query]))


(def db-uri (env/env :DATOMIC_DB_URI))


(def get-connection
  (memoize
   (fn []
     (d/create-database db-uri)
     (d/connect db-uri))))


(defn get-db
  []
  (d/db (get-connection)))


;; I SHOULD USE THIS
;; THIS SHOULD BE SHARED BETWEEN CLJ/CLJS
;; BUT MAYBE JUST HAVE THE PATTERN AND FUNCTION BE DB-AGNOSTIC
;; need to dissoc :ft/results []
;; and :ft/results [false nil] type-thing
(defn datascript-game->tx-map
  [db g]
  (let [schema-keys (map :db/ident schema/schema)
        pattern
        '[* {:game/home-team [:team/name]
             :game/away-team [:team/name]
             :game/possession [* {:possession/team [:team/name]}]}]
        pull-result->tx-map
        (fn pull-result->tx-map
          [pull-result]
          (cond
            (set? pull-result) (vec pull-result)
            (map? pull-result) (reduce-kv
                                (fn [m k v] (assoc m k (pull-result->tx-map v)))
                                {}
                                (select-keys pull-result schema-keys))
            (vector? pull-result) (vec (map pull-result->tx-map pull-result))
            :else pull-result))]
    (pull-result->tx-map (d/pull db pattern g))))


;; THIS ISN'T A COMPLETE SOLUTION!
;; [(true? ?b)] gets assertions, but it won't get the most recent assertion
(defn datomic->datascript-tx-data
  [db]
  (vec
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
        (map :db/ident schema/schema))))


(defn datomic->datascript-db
  [db]
  (let [tx-data (datomic->datascript-tx-data db)
        schema (schema/datomic->datascript-schema schema/schema)
        empty-db (datascript/empty-db schema)]
    (datascript/db-with empty-db tx-data)))


(defn datascript->datomic-tx-data
  [db]
  (let [schema (->> schema/schema
                    (map (juxt :db/ident identity))
                    (into {}))
        ->tuple (fn [coll tuple-type]
                  (let [pred (case tuple-type
                               :db.type/boolean boolean?)]
                    (vec (take (max 2 (count (filter pred coll))) (concat coll (repeat nil))))))
        datom->tx (fn [{:keys [e a v]}]
                    (let [ref? (and (= :db.type/ref (get-in schema [a :db/valueType]))
                                    (number? v))
                          tuple-type? (and (= :db.type/tuple (get-in schema [a :db/valueType]))
                                           (contains? (get schema a) :db/tupleType))]
                      [:db/add (- e) a (cond
                                         ref? (- v)
                                         tuple-type? (->tuple v (get-in schema [a :db/tupleType]))
                                         (coll? v) (vec v)
                                         :else v)]))]
    (->> (datascript/datoms db :eavt)
         (filter (fn [{:keys [a]}] (contains? schema a)))
         (remove (fn [{:keys [a v]}] (and (= a :ft/results) (empty? v))))
         (map datom->tx))))




(comment

  (def score-query
    '[:find ?g (pull ?t [*]) (sum ?pts)
      :keys game-id team points
      :in $ %
      :with ?a
      :where
      (actions ?g ?t ?p ?a)
      (pts ?a ?pts)])


  (def client (client.d/client {:server-type :peer-server
                                :access-key "key"
                                :secret "secret"
                                :endpoint "localhost:4336"
                                :validate-hostnames false}))


  (def client-conn (client.d/connect client {:db-name "db"}))


  @(d/transact (get-connection) schema/schema)


  (d/q score-query
       (-> (get-db)
           (d/with [{:game/home-team {:team/name "Miami Heat"}
                     :game/away-team {:team/name "Golden State Warriors"}
                     :game/datetime #inst "2023-11-01T19:30-04:00"
                     :game/minutes 48
                     :game/possession [{:possession/order 1
                                        :possession/period 1
                                        :possession/team {:team/name "Miami Heat"}
                                        :possession/action [{:action/order 1
                                                             :action/player 7
                                                             :action/type :action.type/turnover
                                                             :steal/player 30}]}
                                       {:possession/order 2
                                        :possession/period 1
                                        :possession/team {:team/name "Golden State Warriors"}
                                        :possession/action [{:action/order 1
                                                             :action/player 22
                                                             :action/type :action.type/shot
                                                             :shot/value 3
                                                             :shot/make? true
                                                             :shot/distance 288}]}]}])
           :db-after)
       query/rules)
  

  (-> (get-db)
      (d/with [{:game/home-team {:team/name "Miami Heat"}
                :game/away-team {:team/name "Golden State Warriors"}
                :game/datetime #inst "2023-11-01T19:30-04:00"
                :game/minutes 48
                :game/possession [{:possession/order 1
                                   :possession/period 1
                                   :possession/team {:team/name "Miami Heat"}
                                   :possession/action [{:action/order 1
                                                        :action/player 7
                                                        :action/type :action.type/turnover
                                                        :steal/player 30}]}
                                  {:possession/order 2
                                   :possession/period 1
                                   :possession/team {:team/name "Golden State Warriors"}
                                   :possession/action [{:action/order 1
                                                        :action/player 22
                                                        :action/type :action.type/shot
                                                        :shot/value 3
                                                        :shot/make? true
                                                        :shot/distance 288}]}]}])
      :db-after
      ((fn [db]
        (d/pull db '[*]
                [:game/home-team+away-team+datetime
                 [(d/entid db [:team/name "Miami Heat"])
                  (d/entid db [:team/name "Golden State Warriors"])
                  #inst "2023-11-01T19:30-04:00"]])))
      )
  
  ;; when you refer to the :game/home-team+away-team+datetime tuple,
  ;; it has to be integer refs for home-team and away-team. So use d/entid
  ;; whereas when you transact for the first time, you can use upsert maps
  



  (def db-game-files
    ["resources/games/2023-03-04-lynden-christian-nooksack-valley.edn"
     "resources/games/2023-02-25-Zillah-Blaine.edn"
     "resources/games/2023-02-18-Northwest-Blaine.edn"
     "resources/games/2023-02-14-Blaine-Nooksack-Valley.edn"
     "resources/games/2023-02-11-Lynden-Christian-Blaine.edn"
     "resources/games/2023-02-08-Blaine-Meridian.edn"])


  ;; TODO - use datascript-game->datomic-tx-map
  (for [db-tx-data (map (comp datascript->datomic-tx-data
                              (partial edn/read-string {:readers datascript/data-readers})
                              slurp)
                        db-game-files)]
    (d/transact (get-connection) db-tx-data))


  ;; WARNING - these do not have distance data!
  (def parser-game-files
    ["resources/games/2022-09-04-Vegas-Seattle.edn"
     "resources/games/2022-09-06-Vegas-Seattle.edn"
     "resources/games/2022-02-05-Blaine-Ferndale.edn"])


  (d/transact (get-connection) {:tx-data (map (comp parser/parse edn/read-string slurp) parser-game-files)})


  ;; here's the problem with this when using datomic.client.api:
  ;; d/as-of only works with (d/db (get-connection))
  ;; and NOT (d/with-db (get-connection))
  ;; however, d/with only works with (d/with-db (get-connection))
  ;; and NOT (d/db (get-connection))
  ;; so it's hard to transact on an "empty" (with schema) datomic db
  (let [schema-t (:t (first (client.d/tx-range client-conn {})))
        db->db-with-schema (fn [db] (client.d/as-of db schema-t))]
    #_(client.d/with
       (db->db-with-schema (client.d/db client-conn))
       {:tx-data [[:db/add -1 :team/name "TEST TEAM"]]}) ;; Execution Error
    (client.d/q
     '[:find ?team :where [?t :team/name ?team]]
     (:db-after
      (client.d/with
       (db->db-with-schema (client.d/with-db client-conn))
       {:tx-data [[:db/add -1 :team/name "TEST TEAM"]]}))) ;; still includes rest of conn's db
    )




  (d/q score-query (d/db (get-connection)) query/rules)


  ;; this still doesn't work, because no matter which order they are called,
  ;; calling d/with with d/as-of will first apply d/with, then d/as-of
  ;; see: https://docs.datomic.com/pro/time/filters.html#as-of-not-branch
  ;; "it does **not** let you branch the past"
  (let [schema-t (:t (first (d/tx-range (d/log (get-connection)) nil nil)))
        db-with-schema (d/as-of (d/db (get-connection)) schema-t)
        datomic-tx-data (datascript->datomic-tx-data (datomic->datascript-db (d/db (get-connection))))
        db-branch (:db-after (d/with db-with-schema datomic-tx-data))]
    (d/q score-query db-branch query/rules))


  (let [datomic-db (d/db (get-connection))
        datascript-db (datomic->datascript-db (d/db (get-connection)))
        datomic-tx-data (datascript->datomic-tx-data datascript-db)
        datomic-db-again (:db-after (d/with datomic-db datomic-tx-data))]
    [(d/q score-query datomic-db query/rules)
     (datascript/q score-query datascript-db query/rules)
     (d/q score-query datomic-db-again query/rules) ;; doubled up results, which is expected
     ])


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
            (d/db (get-connection))
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


  ;; example log api usage
  ;; gets all datoms transacted from ?t1 to ?t2
  (->> (d/q '[:find ?e ?attr ?v ?instant ?op
              :in $ ?log ?t1 ?t2
              :where
              [(tx-ids ?log ?t1 ?t2) [?tx-id ...]]
              [(tx-data ?log ?tx-id) [[?e ?a ?v ?tx ?op]]]
              [?tx :db/txInstant ?instant]
              [?a :db/ident ?attr]]
            (d/db (get-connection)) (d/log (get-connection))
            #inst "2023-05-23" #inst "2023-05-25")
       (sort-by #(nth % 3)))

  ;; TODO - try out query-stats by adding :query-stats key to query
  ;; ala (d/q {:query '[:find ?t :where [?t :team/name]] :args [db] :query-stats :query-stats/test


  (defn game-filter
    [db g]
    (let [entities (set (d/q '[:find [?e ...]
                               :in $ % ?g
                               :where
                               (refs? ?g ?e)]
                             db
                             '[;; ?e1 refers to ?e2
                               [(refs? ?e1 ?e2)
                                [?e1 ?a ?e2]
                                [?a :db/valueType :db.type/ref]]
                               ;; ?e2 is an attribute of ?e1
                               [(refs? ?e1 ?e2)
                                [?e1 ?e2]]
                               ;; ?e1 refers to ?r, which refs? ?e2
                               [(refs? ?e1 ?e2)
                                [?e1 ?a ?r]
                                [?a :db/valueType :db.type/ref]
                                (refs? ?r ?e2)]]
                             g))]
      (d/filter
       db
       (fn [_ {:keys [e a v tx]}]
         (or (contains? entities e)
             (= e g))))))


  (let [db (d/db (get-connection))
        g (-> (d/q '[:find [?g ...] :where [?g :game/possession]] db)
              (nth 4))]
    (d/q score-query (game-filter db g) query/rules))
  )