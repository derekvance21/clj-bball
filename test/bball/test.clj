(ns bball.test
  (:require [clojure.test :as t]
            bball.test.parser-test))

(defn run
  [& opts]
  (t/run-tests 'bball.test.parser-test))
