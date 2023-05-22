(require '[datomic.api :as d])
(d/create-database "datomic:dev://transactor:4334/db?password=datomic")
