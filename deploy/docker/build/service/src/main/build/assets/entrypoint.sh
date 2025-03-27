#!/bin/sh
[ "$DEBUG" != "" ] && set -x
set -eu

mongodb_host=${MONGODB_HOST:?Error: MONGODB_HOST environment variable is not set}
mongodb_port=${MONGODB_PORT:?Error: MONGODB_PORT environment variable is not set}

minio_host=${MINIO_HOST:?Error: MINIO_HOST environment variable is not set}
minio_port=${MINIO_PORT:?Error: MINIO_PORT environment variable is not set}

rabbitmq_host=${RABBITMQ_HOST:?Error: RABBITMQ_HOST environment variable is not set}
rabbitmq_port=${RABBITMQ_PORT:?Error: RABBITMQ_PORT environment variable is not set}

redis_host=${REDIS_HOST:?Error: REDIS_HOST environment variable is not set}
redis_port=${REDIS_PORT:?Error: REDIS_PORT environment variable is not set}

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

# Wait for RabbitMQ to be ready
until nc -z "${rabbitmq_host}" "${rabbitmq_port}"; do
  echo "Waiting for ${rabbitmq_host}:${rabbitmq_port}..."
  sleep 2
done

# Wait for Reddis to be ready
until nc -z "${redis_host}" "${redis_port}"; do
  echo "Waiting for ${redis_host}:${redis_port}..."
  sleep 2
done

# Wait for repository to be ready
for var in $(env | grep 'app.repository.registration.id.*.url' | cut -d'=' -f2); do
  until [ "$(curl -sSfq -w "%{http_code}\n" -o /dev/null -H 'Accept: application/json' "${var}/rest/_about/status/SERVICE?timeoutSeconds=3")" = 200 ]; do
  	echo >&2 "Waiting for ${var} service ..."
  	sleep 3
  done
done

exec sh -c "$@"
