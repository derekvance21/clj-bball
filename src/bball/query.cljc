(ns bball.query)


(def rules '[;; FILTER
             [(fta? ?a)
              [?a :ft/attempted ?ftas]
              [(> ?ftas 0)]]

             [(fga? ?a)
              [?a :action/type :action.type/shot]
              (not [?a :shot/make? true])
              (not (fta? ?a))]
             [(fga? ?a)
              [?a :action/type :action.type/shot]
              [?a :shot/make? true]]
             ;; below works in datascript, having everything in one rule, using or and and
             #_[(fga? ?a)
                [?a :action/type :action.type/shot]
                (or [?a :shot/make? true]
                    (and [?a :shot/make? false]
                         (not (fta? ?a))))]

             [(rebound? ?a)
              (or [?a :rebound/player]
                  [?a :rebound/team? true])]

             [(in? ?p ?a ?t ?number)
              [?p :possession/team ?t]
              [?a :offense/players ?number]]
             [(in? ?p ?a ?t ?number)
              (not [?p :possession/team ?t])
              [?a :defense/players ?number]]

             [(subset? ?sub ?set)
              [(clojure.set/subset? ?sub ?set)]]

             [(floor? ?a ?players)
              (lineup ?a :offense/players ?lineup)
              (subset? ?players ?lineup)]
             [(floor? ?a ?players)
              (lineup ?a :defense/players ?lineup)
              (subset? ?players ?lineup)]

             ;; MAP
             ; this should probably be ?g ?p ?t ?a to help me remember better, mirroring the order below
             [(actions ?g ?t ?p ?a)
              [?g :game/possession ?p]
              [?p :possession/team ?t]
              [?p :possession/action ?a]]

             [(fts ?a ?count)
              [?a :ft/made ?count]]
             [(fts ?a ?count)
              (not [?a :ft/made])
              [(ground 0) ?count]]

             [(fts-player ?a ?player ?fts)
              [?a :action/player ?player]
              [?a :ft/made ?fts]]
             [(fts-player ?a ?player ?fts)
              (not [?a :action/player ?player])
              [(ground 0) ?fts]]
             
             [(fg-pts ?a ?pts)
              [?a :shot/make? true]
              [?a :shot/value ?pts]]
             [(fg-pts ?a ?pts)
              (not [?a :shot/make? true])
              [(ground 0) ?pts]]

             [(fg-pts-player ?a ?player ?pts)
              [?a :shot/make? true]
              [?a :action/player ?player]
              [?a :shot/value ?pts]]
             [(fg-pts-player ?a ?player ?pts)
              (or (not [?a :shot/make? true])
                  (not [?a :action/player ?player]))
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

             [(pts-player ?a ?player ?pts)
              (fts-player ?a ?player ?fts)
              (fg-pts-player ?a ?player ?fg-pts)
              [(+ ?fts ?fg-pts) ?pts]]

             [(off-rebs ?a ?count)
              [?a :rebound/off? true]
              [(ground 1) ?count]]
             [(off-rebs ?a ?count)
              (not [?a :rebound/off? true])
              [(ground 0) ?count]]

             [(off-rebs-player ?a ?player ?count)
              [?a :rebound/player ?player]
              [?a :rebound/off? true]
              [(ground 1) ?count]]
             [(off-rebs-player ?a ?player ?count)
              (or (not [?a :rebound/player ?player])
                  (not [?a :rebound/off? true]))
              [(ground 0) ?count]]

             [(tos ?a ?count)
              [?a :action/type :action.type/turnover]
              [(ground 1) ?count]]
             [(tos ?a ?count)
              (not [?a :action/type :action.type/turnover])
              [(ground 0) ?count]]

             [(lineup ?a ?type ?lineup)
              [?a ?type ?ps]
              [(set ?ps) ?lineup]]

             [(sector ?value ?inches ?sector)
              [(= ?value 2)]
              (sector-2pt ?inches ?sector)]
             [(sector ?value ?inches ?sector)
              [(= ?value 3)]
              (sector-3pt ?inches ?sector)]

             [(sector-2pt ?inches ?sector)
              [(< ?inches 36)]
              [(ground "0-3") ?sector]]
             [(sector-2pt ?inches ?sector)
              [(>= ?inches 36)]
              [(< ?inches 120)]
              [(ground "3-10") ?sector]]
             [(sector-2pt ?inches ?sector)
              [(>= ?inches 120)]
              [(ground "10-3P") ?sector]]

             [(sector-3pt ?inches ?sector)
              [(< ?inches 288)]
              [(ground "3P-24") ?sector]]
             [(sector-3pt ?inches ?sector)
              [(>= ?inches 288)]
              [(ground "24+") ?sector]]])
