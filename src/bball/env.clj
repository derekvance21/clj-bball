(ns bball.env
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]))


(def env-map
  (if (.exists (io/file "env.edn"))
    (edn/read-string (slurp "env.edn"))
    {}))


(defn env
  ([k]
   (env k nil))
  ([k not-found]
   (get env-map k (or (System/getenv (name k)) not-found))))
