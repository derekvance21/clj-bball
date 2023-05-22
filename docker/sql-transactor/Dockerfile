# syntax=docker/dockerfile:1

FROM datomic-pro

COPY sql-transactor.properties ./config/

CMD ["bin/transactor", "-Ddatomic.printConnectionInfo=true", "config/sql-transactor.properties"]

EXPOSE 4334
EXPOSE 4335