(ns bball.query
  (:require [datomic.client.api :as d]))

(def rules '[;; FILTER
             [(fta? ?a)
              [?a :ft/attempted ?ftas]
              [(> ?ftas 0)]]

             [(fga? ?a)
              [?a :action/type :action.type/shot]
              [?a :shot/make? false]
              (not (fta? ?a))]
             [(fga? ?a)
              [?a :action/type :action.type/shot]
              [?a :shot/make? true]]
             
             [(in? ?p ?a ?t ?number)
              [?p :possession/team ?t]
              [?a :offense/players ?number]]
             [(in? ?p ?a ?t ?number)
              (not [?p :possession/team ?t])
              [?a :defense/players ?number]]

             ;; TODO: predicate for possession ending (should be equivalent to *containing*) in turnover
             [(poss-to? ?p)]

             ;; MAP
             [(actions ?g ?t ?p ?a)
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
              (not [?a :shot/make? true])
              [(ground 0) ?pts]]

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
              [?a :shot/make? true]
              [?a :shot/value 2]
              [(ground 1) ?count]]
             [(efgs ?a ?count)
              [?a :shot/make? true]
              [?a :shot/value 3]
              [(ground 1.5) ?count]]
             [(efgs ?a ?count)
              (not [?a :shot/make? true])
              [(ground 0) ?count]]

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
             ])

(defn poss-pts [db p]
  (ffirst (d/q '[:find (sum ?pts)
                 :in $ % ?p
                 :where
                 [?p :possession/action ?a]
                 (pts ?a ?pts)]
               db rules p)))

(defn game-team-fts [db g t]
  (ffirst (d/q '[:find (sum ?fts)
                 :in $ % ?g ?t
                 :with ?a
                 :where
                 (actions ?g ?t ?p ?a)
                 [?a :ft/made ?fts]]
               db rules g t)))

(defn game-team-fgas [db g t]
  (ffirst (d/q '[:find (count ?a)
                 :in $ % ?g ?t
                 :where
                 (actions ?g ?t ?p ?a)
                 (fga? ?a)]
               db rules g t)))
