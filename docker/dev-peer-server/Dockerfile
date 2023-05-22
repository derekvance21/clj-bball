# syntax=docker/dockerfile:1

FROM datomic-pro

COPY ./init.clj ./

CMD bin/repl < init.clj && bin/run -m datomic.peer-server -h peer-server -p 4336 -a key,secret -d db,datomic:dev://transactor:4334/db?password=datomic

EXPOSE 4336
