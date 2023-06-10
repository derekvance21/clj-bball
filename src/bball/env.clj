(ns bball.env
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]))


(def env-map
  (if (.exists (io/file "env.edn"))
    (edn/read-string (slurp "env.edn"))
    {}))


(defn env
  [k]
  (get env-map k (System/getenv (name k))))

