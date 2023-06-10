(require '[datomic.api :as d])

@(def db-uri (System/getenv "DATOMIC_DB_URI"))

(d/create-database db-uri)
