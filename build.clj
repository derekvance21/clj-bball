(ns build
  (:require
   [clojure.tools.build.api :as build]
   [shadow.cljs.devtools.api :as shadow.api]))


(def class-dir "target/classes")
(def basis (build/create-basis {:project "deps.edn"}))
(def uber-file "target/standalone.jar")

(defn clean [_]
  (build/delete {:path "target"}))

(defn ^:export uber [_]
  (clean nil)
  (shadow.api/release :app) ;; TODO - this doesn't work yet?
  (build/copy-dir {:src-dirs ["src" "resources"]
                   :target-dir class-dir})
  (build/compile-clj {:basis basis
                      :src-dirs ["src"]
                      :class-dir class-dir})
  (build/uber {:class-dir class-dir
               :uber-file uber-file
               :basis basis
               :main 'bball.core}))

