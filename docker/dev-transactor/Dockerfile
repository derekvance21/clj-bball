# syntax=docker/dockerfile:1

FROM registry.digitalocean.com/dereks-registry/datomic-pro

COPY init/start.sh ./init/

RUN chmod u+x init/start.sh

CMD ["init/start.sh"]

EXPOSE 4334
EXPOSE 4335
