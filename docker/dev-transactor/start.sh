#!/bin/bash

if [ -z "$DATOMIC_ADMIN_PASSWORD" ]; then
  echo "Environment variable DATOMIC_ADMIN_PASSWORD not set"
  exit 1
fi

if [ -z "$DATOMIC_USER_PASSWORD" ]; then
  echo "Environment variable DATOMIC_USER_PASSWORD not set"
  exit 1
fi

echo -e "\
protocol=dev\n\
host=${DATOMIC_HOST:-0.0.0.0}\n\
alt-host=${DATOMIC_ALT_HOST:-localhost}\n\
port=${DATOMIC_PORT:-4334}\n\
h2-port=${DATOMIC_H2_PORT:-4335}\n\
storage-admin-password=$DATOMIC_ADMIN_PASSWORD\n\
storage-datomic-password=$DATOMIC_USER_PASSWORD\n\
memory-index-threshold=${DATOMIC_MEMORY_INDEX_THRESHOLD:-32m}\n\
memory-index-max=${DATOMIC_MEMORY_INDEX_MAX:-256m}\n\
object-cache-max=${OBJECT_CACHE_MAX:-128m}\n\
storage-access=remote\
" > transactor.properties

bin/transactor -Ddatomic.printConnectionInfo=true transactor.properties
