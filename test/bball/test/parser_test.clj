(ns bball.test.parser-test
  (:require [bball.parser :as p]
            [clojure.test :as t :refer [deftest is]]))

(def edn '[{:teams {:V {:team/name "Vegas"}
                    :S {:team/name "Seattle"}}
            :game {:game/minutes 40
                   :game/datetime #inst "2022-09-06T07:00:00.000-00:00"}}
           [:S (in 5 10 13 24 30)
            :V (in 0 10 12 22 41)
            :V 12 three miss 22 reb two make
            :S 30 three miss reb 24 turnover :V 10 steal
            period
            :S 24 two make (ft 1 1)
            :V 41 two miss :S 13 block :V reb 22 two miss (ft make miss)
            :V (out 0) (in 2)
            :S 30 reb 10 three make
            period]])

(def parsed {:game/teams [{:team/name "Vegas"} {:team/name "Seattle"}]
             :game/minutes 40
             :game/datetime #inst "2022-09-06T07:00:00.000-00:00"
             :game/possession
             [{:possession/team {:team/name "Vegas"}
               :possession/action
               [{:action/player 12
                 :action/type :action.type/shot
                 :shot/value 3
                 :shot/make? false
                 :shot/rebounder 22
                 :shot/off-reb? true
                 :action/order 0
                 :offense/players [0 10 12 22 41]
                 :defense/players [5 10 13 24 30]}
                {:action/player 22
                 :action/type :action.type/shot
                 :shot/value 2
                 :shot/make? true
                 :action/order 1
                 :offense/players [0 10 12 22 41]
                 :defense/players [5 10 13 24 30]}]
               :possession/order 0}
              {:possession/team {:team/name "Seattle"}
               :possession/action
               [{:action/player 30
                 :action/type :action.type/shot
                 :shot/value 3
                 :shot/make? false
                 :shot/rebounder 30
                 :shot/off-reb? true
                 :action/order 0
                 :offense/players [5 10 13 24 30]
                 :defense/players [0 10 12 22 41]}
                {:action/player 24 :action/type :action.type/turnover :action/order 1 :turnover/stealer 10
                 :offense/players [5 10 13 24 30]
                 :defense/players [0 10 12 22 41]}]
               :possession/order 1}
              {:possession/team {:team/name "Seattle"}
               :possession/action
               [{:action/player 24,
                 :action/type :action.type/shot,
                 :shot/value 2,
                 :shot/make? true,
                 :ft/made 1,
                 :ft/attempted 1,
                 :action/order 0
                 :offense/players [5 10 13 24 30]
                 :defense/players [0 10 12 22 41]}],
               :possession/order 2}
              {:possession/team {:team/name "Vegas"}
               :possession/action
               [{:action/player 41
                 :action/type :action.type/shot
                 :shot/value 2
                 :shot/make? false
                 :shot/off-reb? true
                 :shot/blocker 13
                 :action/order 0
                 :offense/players [0 10 12 22 41]
                 :defense/players [5 10 13 24 30]}
                {:action/player 22
                 :action/type :action.type/shot
                 :shot/value 2
                 :shot/make? false
                 :shot/rebounder 30
                 :shot/off-reb? false
                 :ft/made 1
                 :ft/attempted 2
                 :action/order 1
                 :offense/players [0 10 12 22 41]
                 :defense/players [5 10 13 24 30]}]
               :possession/order 3}
              {:possession/team {:team/name "Seattle"}
               :possession/action
               [{:action/player 10
                 :action/type :action.type/shot
                 :shot/value 3
                 :shot/make? true
                 :action/order 0
                 :offense/players [5 10 13 24 30]
                 :defense/players [2 10 12 22 41]}]
               :possession/order 4}]})

(deftest game-interpreter
  (is (= parsed (p/parse edn))))

(t/run-test game-interpreter)
