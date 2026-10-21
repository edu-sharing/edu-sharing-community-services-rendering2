#!/usr/bin/env bash
# Emulates the Helm "Master+Worker" topology locally: one master JVM plus WORKERS worker JVMs
# (controller,job-manager,converter,avconverter), each worker in its own cgroup with a CPU quota and a
# memory limit like a k8s pod (limits.cpu / limits.memory). ffmpeg child processes inherit the cgroup,
# so they compete with the worker's JVM for the same quota - exactly as in a worker pod.
#
# Requires: the infrastructure of the root compose.yml (README step 1), cgroup v2 with the cpu controller
# delegated to the user (systemd-run --user), JDK 21, ffmpeg on the PATH.
#
#   loadtest/local-cluster.sh build     # compiles the service and writes the runtime classpath
#   loadtest/local-cluster.sh start     # starts master + workers, waits for /actuator/health
#   loadtest/local-cluster.sh stats     # CPU time and CFS throttling per pod cgroup
#   loadtest/local-cluster.sh stop
#
# Queue and converter settings follow the Helm chart defaults (values.yaml), not application.properties -
# customers run the chart. Deviations via EXTRA_PROPS, e.g.
#   EXTRA_PROPS="-Dapp.queue.av.concurrency=1" loadtest/local-cluster.sh start
#
# Topology with dedicated AV converters (Helm roles.avconverter) on the same CPU budget, e.g.
#   WORKER_ROLES=controller,job-manager,converter WORKER_CPU=1 AV_CONVERTERS=1 AV_CPU=2 loadtest/local-cluster.sh start
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORKERS=${WORKERS:-2}
WORKER_CPU=${WORKER_CPU:-2}          # limits.cpu of a worker pod
WORKER_MEM=${WORKER_MEM:-6G}         # limits.memory of a worker pod
WORKER_ROLES=${WORKER_ROLES:-controller,job-manager,converter,avconverter}   # Helm roles.worker.appRoles
AV_CONVERTERS=${AV_CONVERTERS:-0}    # additional pods with only the avconverter role
AV_CPU=${AV_CPU:-2}
AV_MEM=${AV_MEM:-4G}
AV_BASE_PORT=${AV_BASE_PORT:-8095}
MASTER_CPU=${MASTER_CPU:-0.5}
MASTER_MEM=${MASTER_MEM:-1G}
# Optional taskset CPU list for all JVMs (e.g. "0-11" to stay on the P-cores of a hybrid CPU).
CPU_SET=${CPU_SET:-}
WORKER_BASE_PORT=${WORKER_BASE_PORT:-8085}   # worker i: API WORKER_BASE_PORT+i-1, actuator +1000
MASTER_PORT=${MASTER_PORT:-8099}
DOCUMENT_CONVERTER=${DOCUMENT_CONVERTER:-http://localhost:8081}
# Optional large video, linked in as classpath:loadtest/large-video.mp4
# (k6: nodeId TEST_loadtest/large-video.mp4, see scenarios/video-mix.js).
VIDEO_FIXTURE=${VIDEO_FIXTURE:-}
EXTRA_PROPS=${EXTRA_PROPS:-}
STATE_DIR=${STATE_DIR:-$ROOT/service/target/loadtest-cluster}
CP_FILE=$STATE_DIR/classpath.txt
UNIT_PREFIX=rs2-lt

# Helm chart defaults (deploy/docker/helm/service/src/main/chart/values.yaml, config.queue.* and
# config.converter.*), so a measurement matches a customer installation.
HELM_DEFAULTS=(
  -Dapp.queue.prefetch=1
  -Dapp.queue.job.concurrency=20
  -Dapp.queue.image.concurrency=4
  -Dapp.queue.av.concurrency=2
  -Dapp.queue.edu-html.concurrency=5
  -Dapp.queue.document.concurrency=8
  -Dapp.queue.jupyter.concurrency=8
  -Dapp.converter.video.ffmpegThreads=4
  -Dapp.converter.video.ffmpegPreset=veryfast
  -Dapp.converter.audio.ffmpegThreads=1
  -Dapp.converter.document.extensions=doc,docx,ppt,pptx,xls,xlsx,odt,odp,ods,txt,ott,rtf,csv
)

unit() { echo "$UNIT_PREFIX-$1"; }
cgroup_dir() { echo "/sys/fs/cgroup$(systemctl --user show "$(unit "$1").scope" -p ControlGroup --value)"; }
names() {
  echo master
  for i in $(seq 1 "$WORKERS"); do echo "worker$i"; done
  for i in $(seq 1 "$AV_CONVERTERS"); do echo "av$i"; done
}

build() {
  mkdir -p "$STATE_DIR"
  "$ROOT/service/mvnw" -q -Pdev -f "$ROOT/pom.xml" -pl service compile dependency:build-classpath \
    -Dmdep.outputFile="$CP_FILE" -Dmdep.includeScope=runtime
  echo "classpath written to $CP_FILE"
}

launch() { # name roles apiPort cpus mem
  local name=$1 roles=$2 port=$3 cpus=$4 mem=$5
  local quota active
  quota=$(awk -v c="$cpus" 'BEGIN { printf "%d%%", c * 100 }')
  active=$(awk -v c="$cpus" 'BEGIN { n = int(c + 0.999); print (n < 1 ? 1 : n) }')
  local pin=()
  [[ -n $CPU_SET ]] && pin=(taskset -c "$CPU_SET")
  # shellcheck disable=SC2086 # EXTRA_PROPS is a deliberately word-split list of -D flags
  nohup systemd-run --user --scope -q --unit="$(unit "$name")" -p CPUQuota="$quota" -p MemoryMax="$mem" \
    "${pin[@]}" java -XX:ActiveProcessorCount="$active" -XX:MaxRAMPercentage=50 \
    -cp "$ROOT/service/target/classes:$(cat "$CP_FILE")" \
    -Dspring.profiles.active=debug,loadtest \
    -Dapp.security.enabled=false \
    -Dapp.roles="$roles" \
    -Dserver.port="$port" -Dmanagement.server.port=$((port + 1000)) \
    -Dapp.documentConverter.host="$DOCUMENT_CONVERTER" \
    "${HELM_DEFAULTS[@]}" $EXTRA_PROPS \
    org.edu_sharing.rendering.ServicesRenderingService2ApplicationKt \
    >"$STATE_DIR/$name.log" 2>&1 &
  echo "$name: roles=$roles api=:$port actuator=:$((port + 1000)) cpu=$cpus mem=$mem"
}

wait_healthy() { # name actuatorPort
  local deadline=$((SECONDS + 180))
  until curl -sf "http://localhost:$2/actuator/health" >/dev/null 2>&1; do
    if ((SECONDS > deadline)); then echo "$1 not healthy after 180 s, see $STATE_DIR/$1.log" >&2; exit 1; fi
    sleep 2
  done
  echo "$1 healthy"
}

start() {
  [[ -f $CP_FILE ]] || build
  mkdir -p "$STATE_DIR"
  if [[ -n $VIDEO_FIXTURE ]]; then
    mkdir -p "$ROOT/service/target/classes/loadtest"
    ln -sf "$(realpath "$VIDEO_FIXTURE")" "$ROOT/service/target/classes/loadtest/large-video.mp4"
  fi
  # master first: it owns the scheduled jobs and the admin API, but serves no /public traffic.
  launch master master "$MASTER_PORT" "$MASTER_CPU" "$MASTER_MEM"
  wait_healthy master $((MASTER_PORT + 1000))
  for i in $(seq 1 "$WORKERS"); do
    launch "worker$i" "$WORKER_ROLES" $((WORKER_BASE_PORT + i - 1)) "$WORKER_CPU" "$WORKER_MEM"
  done
  for i in $(seq 1 "$AV_CONVERTERS"); do
    launch "av$i" avconverter $((AV_BASE_PORT + i - 1)) "$AV_CPU" "$AV_MEM"
  done
  for i in $(seq 1 "$WORKERS"); do wait_healthy "worker$i" $((WORKER_BASE_PORT + i - 1 + 1000)); done
  for i in $(seq 1 "$AV_CONVERTERS"); do wait_healthy "av$i" $((AV_BASE_PORT + i - 1 + 1000)); done
  local urls=()
  for i in $(seq 1 "$WORKERS"); do urls+=("http://localhost:$((WORKER_BASE_PORT + i - 1))"); done
  echo "BASE_URL=$(IFS=,; echo "${urls[*]}")"
}

stop() {
  # every pod of any earlier start, not just those of the current WORKERS/AV_CONVERTERS
  systemctl --user stop "$UNIT_PREFIX-*.scope" 2>/dev/null || true
  echo stopped
}

# CPU time and CFS throttling per pod cgroup (cumulative since start). nr_throttled/throttled_usec grow
# whenever the pod hits its quota — the effect that starves the JVM next to a running ffmpeg.
stats() {
  printf '%-9s %12s %12s %14s %12s\n' pod cpu_s periods throttled_pct throttled_s
  for n in $(names); do
    local dir
    dir=$(cgroup_dir "$n" 2>/dev/null) || continue
    [[ -f $dir/cpu.stat ]] || continue
    awk -v pod="$n" '
      $1 == "usage_usec" { u = $2 } $1 == "nr_periods" { p = $2 }
      $1 == "nr_throttled" { t = $2 } $1 == "throttled_usec" { tu = $2 }
      END { printf "%-9s %12.1f %12d %13.1f%% %12.1f\n", pod, u / 1e6, p, (p ? 100 * t / p : 0), tu / 1e6 }
    ' "$dir/cpu.stat"
  done
}

case ${1:-} in
  build) build ;;
  start) start ;;
  stop) stop ;;
  stats) stats ;;
  *) echo "usage: $0 build|start|stop|stats" >&2; exit 2 ;;
esac
