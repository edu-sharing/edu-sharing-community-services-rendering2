#!/bin/sh
[ "$DEBUG" != "" ] && set -x
set -eu

mongodb_host=${MONGODB_HOST:?Error: MONGODB_HOST environment variable is not set}
mongodb_port=${MONGODB_PORT:?Error: MONGODB_PORT environment variable is not set}

s3_host=${S3_HOST:?Error: S3_HOST environment variable is not set}
s3_port=${S3_PORT:?Error: S3_PORT environment variable is not set}

# Wait for MongoDB to be ready
until nc -z "${mongodb_host}" "${mongodb_port}"; do
  echo "Waiting for ${mongodb_host}:${mongodb_port}..."
  sleep 2
done

# Wait for MinIO to be ready
until nc -z "${s3_host}" "${s3_port}"; do
  echo "Waiting for ${s3_host}:${s3_port}..."
  sleep 2
done

# we need to wait, because mongodb sometimes need some more time...
sleep 5

exec sh -c "$@"
