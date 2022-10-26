(ns bball.test.query-test
  (:require [clojure.test :as t :refer [is deftest testing]]
            [bball.query :refer [rules]]
            [bball.parser :refer [parse]]
            [bball.db :refer [test-config]]
            [datahike.api :as d]))

(deftest game-query
  (let [_ (do (when (d/database-exists? test-config) (d/delete-database test-config))
              (d/create-database test-config))
        conn (d/connect test-config)
        _ (d/transact conn [(-> "games/2022-09-06-Vegas-Seattle.edn" slurp read-string parse)])]
    (testing "FT%"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (sum ?fts) (sum ?ftas)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         [?a :ft/attempted ?ftas]
                         [?a :ft/made ?fts]]
                       @conn
                       rules))
             #{[{:team/name "Las Vegas Aces"} 15 19]
               [{:team/name "Seattle Storm"} 19 26]})))
    (testing "Pace"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (count ?p) ?minutes
                         :in $ %
                         :where
                         [?g :game/minutes ?minutes]
                         [?g :game/possession ?p]
                         [?p :possession/team ?t]]
                       @conn
                       rules))
             #{[{:team/name "Las Vegas Aces"} 78 40]
               [{:team/name "Seattle Storm"} 78 40]})))
    (testing "FT/FGA"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) ?fts ?fgas ?ft-fgas
                         :in $ %
                         :where
                         [?t :team/name]
                         (game-team-fts ?g ?t ?fts)
                         (game-team-fgas ?g ?t ?fgas)
                         [(/ ?fts ?fgas) ?ft-fgas]]
                       @conn
                       rules))
             #{[{:team/name "Las Vegas Aces"} 15 63 15/63]
               [{:team/name "Seattle Storm"} 19 70 19/70]})))
    (testing "eFG%"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (avg ?efgs)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         (fga? ?a)
                         (efgs ?a ?efgs)]
                       @conn
                       rules))
             #{[{:team/name "Seattle Storm"} 0.5214285714285715]
               [{:team/name "Las Vegas Aces"} 0.6507936507936508]})))
    (testing "TO%"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (sum ?turnovers) (count-distinct ?p)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         (tos ?a ?turnovers)]
                       @conn
                       rules))
             #{[{:team/name "Seattle Storm"} 9 78]
               [{:team/name "Las Vegas Aces"} 12 78]})))
    (testing "3Pt%"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (sum ?3fgs) (count ?3fgs) (avg ?3fgs)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         (fga? ?a)
                         [?a :shot/value 3]
                         (fgs ?a ?3fgs)]
                       @conn
                       rules))
             #{[{:team/name "Las Vegas Aces"} 10 24 10/24]
               [{:team/name "Seattle Storm"} 11 26 11/26]})))
    (testing "OffReb%"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (sum ?off-rebs) (count ?off-rebs) (avg ?off-rebs)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         [?a :shot/rebounder]
                         (off-rebs ?a ?off-rebs)]
                       @conn
                       rules))
             #{[{:team/name "Las Vegas Aces"} 5 29 5/29]
               [{:team/name "Seattle Storm"} 11 40 11/40]})))
    (testing "OffRtg"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) (sum ?pts) (count-distinct ?p) (avg ?pts)
                         :in $ % ?g
                         :where
                         (actions ?g ?t ?p)
                         (poss-pts ?p ?pts)]
                       @conn
                       rules))
             #{[{:team/name "Seattle Storm"} 92 78 92/78]
               [{:team/name "Las Vegas Aces"} 97 78 97/78]})))
    (testing "Box Score"
      (is (= (set (d/q '[:find (pull ?t [:team/name]) ?number (sum ?pts)
                         :in $ %
                         :with ?a
                         :where
                         (actions ?g ?t ?p ?a)
                         [?a :player/number ?number]
                         (pts ?a ?pts)]
                       @conn
                       rules))
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
