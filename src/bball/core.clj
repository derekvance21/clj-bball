(ns bball.core
  (:require [bball.parser :as p]
            [bball.db :as db]
            [datomic.client.api :as d]
            [clojure.edn :as edn]))

(def client (d/client db/dev-config))

(def db {:db-name "clj-bball-db-dev"})

(comment
  (d/delete-database client db)
  )

(d/create-database client db)

(def conn (d/connect client db))

(d/transact conn {:tx-data db/schema})

(defn file->tx
  [file]
  (-> file slurp edn/read-string p/parse))

(def game-files ["games/2022-09-04-Vegas-Seattle.edn"
                 "games/2022-09-06-Vegas-Seattle.edn"])

(def games (map file->tx game-files))

(d/transact conn {:tx-data games})
