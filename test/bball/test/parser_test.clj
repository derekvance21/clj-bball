(ns bball.test.parser-test
  (:require [clojure.test :as t :refer [is deftest]]
            [bball.parser :refer [parse]]))

(deftest game-interpreter
  (is (= (-> '[{:teams {:V {:team/name "Vegas"}
                        :S {:team/name "Seattle"}}}
               [:V 12 three miss 22 reb two make
                :S 30 three miss reb 24 turnover
                :V 41 two miss :V reb 22 two miss (ft 1 2)
                :S 30 reb 10 three make
                period]]
             parse
             (get :game))
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
              :action/ft-made 1
              :action/ft-attempted 2
              :action/order 1}]
            :possession/order 2}
           {:possession/team {:team/name "Seattle"}
            :possession/action
            [{:player/number 10
              :action/type :action.type/shot
              :shot/value 3
              :shot/make? true
              :action/order 0}]
            :possession/order 3}]})))
