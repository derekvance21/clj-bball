#!/bin/bash

bin/repl < init/init.clj

HOST=${DATOMIC_PEER_SERVER_HOST:-peer-server}
PORT=${DATOMIC_PEER_SERVER_PORT:-4336}
KEY=${DATOMIC_ACCESS_KEY:-key}
SECRET=${DATOMIC_SECRET:-secret}
DB=${DATOMIC_DB:-db}
URI=${DATOMIC_DB_URI:-datomic:dev://transactor:4334/db?password=datomic}

bin/run \
    -m datomic.peer-server \
    -h $HOST \
    -p $PORT \
    -a $KEY,$SECRET \
    -d $DB,$URI
