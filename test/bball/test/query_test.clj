(ns bball.test.query-test
  (:require [clojure.test :as t :refer [is deftest testing]]
            [bball.query :as q]
            [bball.db :as db]
            [bball.core :refer [file->tx]]
            [datomic.client.api :as d]))

(def ^:dynamic *conn*)

(deftest game-query
  (binding [*conn* (let [client (d/client db/dev-config)
                         db {:db-name "clj-bball-db-test"}]
                     (d/delete-database client db)
                     (d/create-database client db)
                     (d/connect client db))]

    (d/transact *conn* {:tx-data db/schema})
    (d/transact *conn* {:tx-data [(file->tx "games/2022-09-06-Vegas-Seattle.edn")]})

    (testing "FT%"
      (is (= (-> '[:find (pull ?t [:team/name]) (sum ?fts) (sum ?ftas)
                   :in $ %
                   :with ?a
                   :where
                   (actions ?g ?t ?p ?a)
                   [?a :ft/attempted ?ftas]
                   [?a :ft/made ?fts]]
                 (d/q (d/db *conn*) q/rules)
                 set)
             #{[{:team/name "Las Vegas Aces"} 15 19]
               [{:team/name "Seattle Storm"} 19 26]})))
    (testing "Pace"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (count ?p) ?minutes
                         :in $ %
                         :where
                         [?g :game/minutes ?minutes]
                         [?g :game/possession ?p]
                         [?p :possession/team ?t]]
                       (d/db *conn*)
                       q/rules))
             #{[{:team/name "Las Vegas Aces"} 78 40]
               [{:team/name "Seattle Storm"} 78 40]})))
    (testing "FT/FGA"
      (is (= (set (d/q '[:find ?team (sum ?fts) (sum ?fgas)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         (fts ?a ?fts)
                         (fgas ?a ?fgas)
                         [?t :team/name ?team]]
                       (d/db *conn*)
                       q/rules))
             #{["Las Vegas Aces" 15 63]
               ["Seattle Storm" 19 70]})))
    (testing "eFG%"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (avg ?efgs)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         (fga? ?a)
                         (efgs ?a ?efgs)]
                       (d/db *conn*)
                       q/rules))
             #{[{:team/name "Seattle Storm"} 0.5214285714285715]
               [{:team/name "Las Vegas Aces"} 0.6507936507936508]})))
    (testing "TO%"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (sum ?turnovers) (count-distinct ?p)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         (tos ?a ?turnovers)]
                       (d/db *conn*)
                       q/rules))
             #{[{:team/name "Seattle Storm"} 9 78]
               [{:team/name "Las Vegas Aces"} 12 78]})))
    (testing "3Pt%"
      (is (= (set (d/q '[:find ?team (sum ?3fgs) (count ?3fgs) (avg ?3fgs)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         (fga? ?a)
                         [?a :shot/value 3]
                         (fgs ?a ?3fgs)
                         [?t :team/name ?team]]
                       (d/db *conn*)
                       q/rules))
             #{["Las Vegas Aces" 10 24 0.4166666666666667]
               ["Seattle Storm" 11 26 0.4230769230769231]})))
    (testing "OffReb%"
      (is (= (set (d/q '[:find ?team (sum ?off-rebs) (count ?off-rebs) (avg ?off-rebs)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         [?a :shot/rebounder]
                         (off-rebs ?a ?off-rebs)
                         [?t :team/name ?team]]
                       (d/db *conn*)
                       q/rules))
             #{["Las Vegas Aces" 5 29 0.1724137931034483]
               ["Seattle Storm" 11 40 0.275]})))
    (testing "OffRtg"
      (is (= (set (d/q '[:find ?team (sum ?pts) (count-distinct ?p)
                         :in $ %
                         :where
                         (actions ?g ?t ?p ?a)
                         (pts ?a ?pts)
                         [?t :team/name ?team]]
                       (d/db *conn*)
                       q/rules))
             #{["Seattle Storm" 92 78]
               ["Las Vegas Aces" 97 78]})))
    (testing "Box Score"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) ?number (sum ?pts)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         [?a :action/player ?number]
                         (pts ?a ?pts)]
                       (d/db *conn*)
                       q/rules))
             #{[{:team/name "Las Vegas Aces"} 41 4]
               [{:team/name "Seattle Storm"} 10 8]
               [{:team/name "Las Vegas Aces"} 5 0]
               [{:team/name "Las Vegas Aces"} 12 31]
               [{:team/name "Seattle Storm"} 30 42]
               [{:team/name "Las Vegas Aces"} 0 18]
               [{:team/name "Las Vegas Aces"} 10 15]
               [{:team/name "Seattle Storm"} 5 8]
               [{:team/name "Seattle Storm"} 7 3]
               [{:team/name "Las Vegas Aces"} 2 6]
               [{:team/name "Las Vegas Aces"} 22 23]
               [{:team/name "Seattle Storm"} 13 0]
               [{:team/name "Seattle Storm"} 31 2]
               [{:team/name "Seattle Storm"} 20 0]
               [{:team/name "Seattle Storm"} 24 29]})))))
