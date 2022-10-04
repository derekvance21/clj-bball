(ns bball.test.parser-test
  (:require [clojure.test :as t :refer [is deftest]]
            [bball.parser :refer [parse]]
            [clojure.data :refer [diff]]))

(deftest game-interpreter
  (is (= (parse '[{:teams {:V {:team/name "Vegas"}
                           :S {:team/name "Seattle"}}}
                  [:V 12 three miss 22 reb two make
                   :S 30 three miss reb 24 turnover
                   period
                   :S 24 two make (ft 1 1)
                   :V 41 two miss :V reb 22 two miss (ft 1 2)
                   :S 30 reb 10 three make
                   period]])
         {:game/possession
          [{:possession/team {:team/name "Vegas"}
            :possession/action
            [{:player/number 12
              :action/type :action.type/shot
              :shot/value 3
              :shot/make? false
              :shot/rebounder 22
              :shot/off-reb? true
              :action/order 0}
             {:player/number 22
              :action/type :action.type/shot
              :shot/value 2
              :shot/make? true
              :action/order 1}]
            :possession/order 0}
           {:possession/team {:team/name "Seattle"}
            :possession/action
            [{:player/number 30
              :action/type :action.type/shot
              :shot/value 3
              :shot/make? false
              :shot/rebounder 30
              :shot/off-reb? true
              :action/order 0}
             {:player/number 24 :action/type :action.type/turnover :action/order 1}]
            :possession/order 1}
           {:possession/team {:team/name "Seattle"}
            :possession/action
            [{:player/number 24,
              :action/type :action.type/shot,
              :shot/value 2,
              :shot/make? true,
              :ft/made 1,
              :ft/attempted 1,
              :action/order 0}],
            :possession/order 2}
           {:possession/team {:team/name "Vegas"}
            :possession/action
            [{:player/number 41
              :action/type :action.type/shot
              :shot/value 2
              :shot/make? false
              :shot/off-reb? true
              :action/order 0}
             {:player/number 22
              :action/type :action.type/shot
              :shot/value 2
              :shot/make? false
              :shot/rebounder 30
              :shot/off-reb? false
              :ft/made 1
              :ft/attempted 2
              :action/order 1}]
            :possession/order 3}
           {:possession/team {:team/name "Seattle"}
            :possession/action
            [{:player/number 10
              :action/type :action.type/shot
              :shot/value 3
              :shot/make? true
              :action/order 0}]
            :possession/order 4}]})))
