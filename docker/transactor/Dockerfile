# syntax=docker/dockerfile:1

FROM datomic-pro

COPY transactor.properties ./config/

# this sort of thing is possible, btw: echo "(+ 2 2)" | bin/repl => 4

CMD ["bin/transactor", "-Ddatomic.printConnectionInfo=true", "config/transactor.properties"]

EXPOSE 4334
EXPOSE 4335
