# syntax=docker/dockerfile:1

FROM datomic-pro

COPY remote-transactor.properties ./config/

CMD ["bin/transactor", "-Ddatomic.printConnectionInfo=true", "config/remote-transactor.properties"]

EXPOSE 4334
EXPOSE 4335