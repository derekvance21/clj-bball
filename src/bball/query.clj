(ns bball.query
  (:require [datahike.api :as d]))

(def rules '[[(actions ?g ?t ?p ?a)
              [?g :game/possession ?p]
              [?p :possession/team ?t]
              [?p :possession/action ?a]]

             [(fts ?a ?count)
              [?a :ft/made ?count]]
             [(fts ?a ?count)
              (not [?a :ft/made])
              [(ground 0) ?count]]

             [(fg-pts ?a ?pts)
              [?a :shot/make? true]
              [?a :shot/value ?pts]]
             [(fg-pts ?a ?pts)
              [?a :shot/make? false]
              [(ground 0) ?pts]]
             [(fg-pts ?a ?pts)
              (not [?a :shot/make?])
              [(ground 0) ?pts]]

             [(poss-pts ?p ?pts)
              [(bball.query/poss-pts $ ?p) ?pts]]

             [(pts ?a ?pts)
              (fts ?a ?fts)
              (fg-pts ?a ?fg-pts)
              [(+ ?fts ?fg-pts) ?pts]]

             [(off-rebs ?a ?count)
              [?a :shot/off-reb? true]
              [(ground 1) ?count]]
             [(off-rebs ?a ?count)
              (not [?a :shot/off-reb? true])
              [(ground 0) ?count]]

             [(tos ?a ?count)
              [?a :action/type :action.type/turnover]
              [(ground 1) ?count]]
             [(tos ?a ?count)
              (not [?a :action/type :action.type/turnover])
              [(ground 0) ?count]]
             
             ;; TODO: predicate for possession ending (should be equivalent to *containing*) in turnover
             [(poss-to? ?p)
              ]

             [(fga? ?a)
              [?a :action/type :action.type/shot]
              [?a :shot/make? false]
              (or [?a :ft/attempted 0]
                  [(missing? $ ?a :ft/attempted)])]
             [(fga? ?a)
              [?a :action/type :action.type/shot]
              [?a :shot/make? true]]

             [(fgas ?a ?count)
              (fga? ?a)
              [(ground 1) ?count]]
             [(fgas ?a ?count)
              (not (fga? ?a))
              [(ground 0) ?count]]

             [(fgs ?a ?count)
              [?a :shot/make? true]
              [(ground 1) ?count]]
             [(fgs ?a ?count)
              (not [?a :shot/make? true])
              [(ground 0) ?count]]

             [(efgs ?a ?count)
              [?a :shot/value 3]
              [?a :shot/make? true]
              [(ground 1.5) ?count]]
             [(efgs ?a ?count)
              [?a :shot/value 2]
              [?a :shot/make? true]
              [(ground 1) ?count]]
             [(efgs ?a ?count)
              (not [?a :shot/make? true])
              [(ground 0) ?count]]

             [(game-team-fts ?g ?t ?fts)
              [(bball.query/game-team-fts $ ?g ?t) ?fts]]

             [(game-team-fgas ?g ?t ?fgas)
              [(bball.query/game-team-fgas $ ?g ?t) ?fgas]]])

(defn poss-pts [db p]
  (d/q '[:find (sum ?pts) .
         :in $ % ?p
         :where
         [?p :possession/action ?a]
         (pts ?a ?pts)]
       db rules p))

(defn game-team-fts [db g t]
  (d/q '[:find (sum ?fts) .
         :in $ % ?g ?t
         :with ?a
         :where
         (actions ?g ?t ?p ?a)
         [?a :ft/made ?fts]]
       db rules g t))

(defn game-team-fgas [db g t]
  (d/q '[:find (count ?a) .
         :in $ % ?g ?t
         :where
         (actions ?g ?t ?p ?a)
         (fga? ?a)]
       db rules g t))

(defn box-score
  [db game-eid]
  (->> (d/q '[:find (pull ?t [:team/name]) ?number (sum ?pts)
              :in $ % ?g
              :with ?a
              :where
              (actions ?g ?t ?p ?a)
              [?a :player/number ?number]
              (pts ?a ?pts)]
            db
            rules
            game-eid)
       (map #(update % 0 :team/name))
       (sort-by last >)
       (sort-by first)
       (into [[:team :player :points]])))
