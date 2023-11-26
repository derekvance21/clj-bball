(ns build
  (:require
   [clojure.tools.build.api :as build]))


(def class-dir "target/classes")


(def basis (build/create-basis))


(def uber-file "target/standalone.jar")


(defn clean [_]
  (build/delete {:path "target"}))


(defn ^:export uber [_]
  (clean nil)
  (build/copy-dir {:src-dirs ["src" "resources"]
                   :target-dir class-dir})
  (build/compile-clj {:basis basis
                      :class-dir class-dir})
  (build/uber {:class-dir class-dir
               :uber-file uber-file
               :basis basis
               :main 'bball.core}))
