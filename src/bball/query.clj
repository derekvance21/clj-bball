(ns bball.query
  (:require [datahike.api :as d]))

(def rules '[[(actions ?g ?t ?p ?a)
              [?g :game/possession ?p]
              [?p :possession/team ?t]
              [?p :possession/action ?a]]

             [(ft-pts ?a ?pts)
              [?a :ft/made ?pts]]
             [(ft-pts ?a ?pts)
              [(missing? $ ?a :ft/made)]
              [(ground 0) ?pts]]

             [(fg-pts ?a ?pts)
              [?a :shot/make? true]
              [?a :shot/value ?pts]]
             [(fg-pts ?a ?pts)
              [?a :shot/make? false]
              [(ground 0) ?pts]]
             [(fg-pts ?a ?pts)
              [(missing? $ ?a :shot/make?)]
              [(ground 0) ?pts]]

             [(poss-pts ?p ?pts)
              [(bball.query/poss-pts $ ?p) ?pts]]

             [(pts ?a ?pts)
              (ft-pts ?a ?ft-pts)
              (fg-pts ?a ?fg-pts)
              [(+ ?ft-pts ?fg-pts) ?pts]]

             [(off-rebs ?a ?count)
              [?a :shot/off-reb? true]
              [(ground 1) ?count]]
             [(off-rebs ?a ?count)
              [?a :shot/off-reb? false]
              [(ground 0) ?count]]

             [(tos ?a ?count)
              [?a :action/type :action.type/turnover]
              [(ground 1) ?count]]
             [(tos ?a ?count)
              (not [?a :action/type :action.type/turnover])
              [(ground 0) ?count]]

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
              [?a :shot/make? false]
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

             [(fts ?a ?count)
              [?a :ft/made ?count]]
             [(fts ?a ?count)
              [(missing? $ ?a :ft/made)]
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