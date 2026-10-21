#!/bin/sh
[ "$DEBUG" != "" ] && set -x
set -eu

mongodb_host=${MONGODB_HOST:?Error: MONGODB_HOST environment variable is not set}
mongodb_port=${MONGODB_PORT:?Error: MONGODB_PORT environment variable is not set}

s3_host=${S3_HOST:?Error: S3_HOST environment variable is not set}
s3_port=${S3_PORT:?Error: S3_PORT environment variable is not set}

rabbitmq_host=${RABBITMQ_HOST:?Error: RABBITMQ_HOST environment variable is not set}
rabbitmq_port=${RABBITMQ_PORT:?Error: RABBITMQ_PORT environment variable is not set}

redis_host=${REDIS_HOST:?Error: REDIS_HOST environment variable is not set}
redis_port=${REDIS_PORT:?Error: REDIS_PORT environment variable is not set}

# Infrastructure waits are bounded: abort with a clear error instead of hanging forever.
infra_wait_timeout=${INFRA_WAIT_TIMEOUT:-300}
# Repository wait is best effort only (0 = do not wait); the application registers repositories later on its own.
repository_wait_timeout=${REPOSITORY_WAIT_TIMEOUT:-60}

# wait_for_tcp <name> <host> <port>: block until the port is open, exit 1 after INFRA_WAIT_TIMEOUT seconds
wait_for_tcp() {
  deadline=$(($(date +%s) + infra_wait_timeout))
  until nc -z -w 3 "$2" "$3"; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then
      echo >&2 "ERROR: $1 at $2:$3 not reachable after ${infra_wait_timeout}s, aborting."
      exit 1
    fi
    echo "Waiting for $1 at $2:$3..."
    sleep 2
  done
}

wait_for_tcp MongoDB "${mongodb_host}" "${mongodb_port}"
wait_for_tcp S3 "${s3_host}" "${s3_port}"
wait_for_tcp RabbitMQ "${rabbitmq_host}" "${rabbitmq_port}"
wait_for_tcp Redis "${redis_host}" "${redis_port}"

# Wait (best effort) for the configured repositories; continue after the deadline.
if [ "${repository_wait_timeout}" -gt 0 ]; then
  repo_deadline=$(($(date +%s) + repository_wait_timeout))
  for entry in $(env | grep '^app\.repository\.registration\.id\.[^=]*\.url='); do
    repo_url=${entry#*=}
    until [ "$(curl -sSfq --connect-timeout 3 -m 5 -w "%{http_code}\n" -o /dev/null -H 'Accept: application/json' "${repo_url}/rest/_about/status/SERVICE?timeoutSeconds=3" 2>/dev/null || true)" = 200 ]; do
      if [ "$(date +%s)" -ge "${repo_deadline}" ]; then
        echo >&2 "WARN: repository ${repo_url} not ready after ${repository_wait_timeout}s, starting anyway."
        break
      fi
      echo >&2 "Waiting for ${repo_url} service ..."
      sleep 3
    done
  done
fi

# exec the inner shell command too, so the JVM replaces the shell and receives SIGTERM as PID 1
exec sh -c "exec $*"
