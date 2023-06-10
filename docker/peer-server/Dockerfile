# syntax=docker/dockerfile:1

FROM registry.digitalocean.com/dereks-registry/datomic-pro

COPY init/ ./init/

RUN chmod u+x init/start.sh

CMD ["init/start.sh"]
