#!/bin/sh
[ "$DEBUG" != "" ] && set -x
set -eu

mongodb_host=${MONGODB_HOST:?Error: MONGODB_HOST environment variable is not set}
mongodb_port=${MONGODB_PORT:?Error: MONGODB_PORT environment variable is not set}
mongodb_username=${MONGODB_USER:-MONGODB_USER environment variable is not set}
mongodb_password=${MONGODB_PASSWORD:-MONGODB_PASSWORD environment variable is not set}
mongodb_authdb=${MONGODB_AUTH_DB:-admin}


minio_host=${MINIO_HOST:?Error: MINIO_HOST environment variable is not set}
minio_port=${MINIO_PORT:?Error: MINIO_PORT environment variable is not set}

# Wait for MongoDB to be ready
until nc -z "${mongodb_host}" "${mongodb_port}"; do
  echo "Waiting for ${mongodb_host}:${mongodb_port}..."
  sleep 2
done

# Wait for MinIO to be ready
until nc -z "${minio_host}" "${minio_port}"; do
  echo "Waiting for ${minio_host}:${minio_port}..."
  sleep 2
done

# we need to wait, because mongodb sometimes need some more time...
sleep 2

export AWS_S3_ENDPOINT="http://${minio_host}:${minio_port}"
export MONGODB_URL="mongodb://${mongodb_username}:${mongodb_password}@${mongodb_host}:${mongodb_port}/?authSource=${mongodb_authdb}"
sh -c "$*"
