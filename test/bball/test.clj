(ns bball.test
  (:require [clojure.test :as t]
            bball.test.parser-test
            bball.test.query-test))

(defn run
  [& opts]
  (t/run-tests 'bball.test.parser-test
               'bball.test.query-test))
