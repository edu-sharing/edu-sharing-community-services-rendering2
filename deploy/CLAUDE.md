# deploy — CLAUDE.md

Module-local guidance for the Docker / Helm / docker-compose module. See repo-root
[`../CLAUDE.md`](../CLAUDE.md) for build/versioning context.

## ⚠️ Edit `src/` only — `target/` is generated
Every artifact here is produced by Maven into `target/`. **Only edit files under `src/`.**
When you rename or add a Spring property, update **both** delivery mechanisms so they stay
in sync:
- Compose: `deploy/docker/compose/src/main/compose/*.yml`
- Helm: `deploy/docker/helm/service/src/main/chart/templates/{configmap-env,secret-env}.yaml`
  (and the equivalent template for the affected module).

## Layout
```
deploy/docker/
├── compose/        # resource-filtered docker-compose YAML (Maven Resources Plugin)
├── build/          # per-module Dockerfiles + docker-maven-plugin
│   ├── service/  document-converter/  jupyter-converter/  lumi/  admin-frontend/
└── helm/           # per-module Helm charts + helm-maven-plugin
    ├── bundle/   service/  document-converter/  jupyter-converter/  lumi/  admin-frontend/
```

## Compose
`src/main/compose/1_rendering2-{common,dev,debug,remote,productive}.yml`. `common` defines
the base stack; the others override per profile (e.g. `dev` mounts local JARs and sets
`-Dspring.profiles.active=docker`). Services: `mongo-database`, `rendering2-rustfs-storage`
(S3-compatible), `rendering2-message-queue` (RabbitMQ), `redis-cache`, `rendering2-service`,
`rendering2-document-converter`, `rendering2-lumi`, `rendering2-jupyter-converter`,
`rendering2-admin-frontend`.
Values use `${RENDERING2_*}` env overrides with defaults; Maven resource filtering
substitutes `${docker.*}` build properties.

The **`rendering2-admin-frontend`** service shares the service's `VIRTUAL_HOST` and is routed
at a separate path (`VIRTUAL_PATH=/rendering-admin/`, env `RENDERING2_ADMIN_FRONTEND_PUBLIC_PATH`)
so the SPA is same-origin with the `/admin` API (no CORS). It also publishes a host port
(`RENDERING2_ADMIN_FRONTEND_PORT_HTTP`, default `10400`) for external Apache2 routing. Its
container env `BASE_HREF` (UI path) and `ADMIN_API_BASE` (service context-path, e.g. `/rendering`)
configure the static server at runtime — see [`../admin-frontend/CLAUDE.md`](../admin-frontend/CLAUDE.md).

## Dockerfiles (`build/<module>/src/main/build/Dockerfile`)
- **service** & **document-converter**: Amazon Corretto 21-alpine, Spring Boot layered-jar
  extraction (`java -Djarmode=tools -jar application.jar extract --layers --launcher`; Boot
  4.1 removed the old `-Djarmode=layertools` mode), non-root `worker` user, entrypoint sets
  `-Dspring.profiles.active=docker`. The document-converter image additionally installs
  **LibreOffice + fonts**.
- **lumi**: `node:21-alpine`, non-root `node` user, runs `dist/index.js`, exposes 3000. Creates
  `/application/library-cache` **owned by `node` before `USER node`** — `/application` is root-owned,
  so a volume mounted there would be created as root and the node user could not write into it
  (Docker seeds a fresh named volume from this directory's ownership; K8s uses the pod's `fsGroup`).
- **jupyter-converter**: `python:3.13` (Poetry install in a builder stage), runs `python -m main`.
- **admin-frontend**: `node:21-alpine`, serves the static Angular build via a dependency-free
  `server.mjs` (Node built-ins only; SPA fallback, `/ping` health, strips the `BASE_HREF`
  prefix, injects `BASE_HREF`/`ADMIN_API_BASE` into `index.html` at request time). Exposes 8080.

`docker-maven-plugin` builds images on `install` and pushes on `deploy`, naming them from the
root POM's `docker.registry` / `docker.repository` / `docker.prefix` and the git-derived `docker.tag`.

## Helm (`helm/<module>/src/main/chart/`)
Each chart emits these templates (service chart shown):
`configmap-env.yaml` (non-sensitive dotted Spring props), `secret-env.yaml`
(passwords/tokens), `configmap-file.yaml`, `statefulset.yaml`, `service.yaml`, Istio
`virtualservice.yaml` / `destinationrule.yaml` / `gateway.yaml`, `ingress.yaml`, `hpa.yaml`,
`poddisruptionbudget.yaml`, `servicemonitor.yaml`, `prometheusrule.yaml`,
`_helpers.tpl`. Charts depend on `edu_sharing-community-common-lib`. `helm-maven-plugin`
runs init → dependency-build → lint → package → upload; `bundle/` aggregates the modules.

## Read-only root filesystem
Every chart has `securityContext.readOnlyRootFilesystem` (default **`false`**, rendered into the main
container's `securityContext` only when `true`, so the default rendering is unchanged). It is
**prepared, not active**: enable it per chart only after the functional paths below were tested in staging.
The setting applies to the main container only; the service's varnish sidecars (`proxyCache`,
`proxyMetrics`) have their own `securityContext` and are deliberately **not** made read-only.

**Always-on `/tmp` volume:** all five charts mount a node-local `emptyDir` at `/tmp` (service and lumi:
unless `persistence.data.temp.create` provides a PVC instead; optional `persistence.data.temp.sizeLimit`).
It is deliberately not memory-backed. The Dockerfiles declare `VOLUME /tmp`, but Kubernetes
(containerd/CRI-O) ignores that, so without the mount `/tmp` would be part of the (read-only) root filesystem.

**Measured write paths** (`docker diff` on an idle container, then `docker run --read-only`):

| Image | Writes (outside volumes) | Needed for read-only |
|---|---|---|
| service | idle: only `/tmp` (`hsperfdata_*`, `tomcat.*`, `tomcat-docbase.*` for ports 8080 and 9080). Per source additionally only `java.io.tmpdir` (AV, ConverterWebServiceCaller, H5P, Onyx, EduHtml, S3 spool, metadata). ffmpeg is the system ffmpeg (`SystemFfmpegLocator`), nothing is unpacked | writable `/tmp` |
| document-converter | `/tmp` (`.jodconverter_*` = LibreOffice profile/socket, `OSL_PIPE_*`, `hsperfdata_*`, `tomcat*`, `CONVERTER_WORK_DIR`) and the home `/application` (`.cache/fontconfig`, `.config`) | writable `/tmp` and `HOME=/tmp` (txt->pdf and ->html tested, output byte-identical) |
| jupyter-converter | only `/app/__pycache__` (bytecode); nothing with `PYTHONDONTWRITEBYTECODE=1` (now set in the Dockerfile) | nothing (output byte-identical); `/tmp` added anyway |
| lumi | nothing at startup; uploads (multer) to `os.tmpdir()` resp. `H5P_UPLOAD_DIR`, library cache in `/application/library-cache` | writable `/tmp` and library cache volume (both already mounted by the chart; `H5P_UPLOAD_DIR` is not set by the chart, so uploads use `/tmp`) |
| admin-frontend | nothing | nothing; `/tmp` added anyway |

**Per chart when enabled:** service - `/tmp` (chart provides it); document-converter - `/tmp` plus
`HOME=/tmp`, which the chart sets automatically when the flag is on (`CONVERTER_WORK_DIR` stays empty =
`java.io.tmpdir/doc-converter`, i.e. `/tmp`; if you set it, it must point to a writable volume);
jupyter-converter, admin-frontend - nothing beyond `/tmp`; lumi - `/tmp` and, in `libraryCache.mode: package`,
the library cache volume (both mounted by the chart).

**Method:** `docker diff <container>` lists writes outside volumes. A Dockerfile `VOLUME` such as `/tmp` is
hidden by it, so list `/tmp` separately (`docker exec <c> ls -la /tmp`). Local check (no compose switch needed):

```bash
docker run --read-only --tmpfs /tmp <image>                    # service, lumi, jupyter-converter, admin-frontend
docker run --read-only --tmpfs /tmp -e HOME=/tmp <image>       # document-converter
```

**Not yet verified:** only idle behaviour and a txt->pdf/html and notebook conversion were measured. The
functional paths of the service and the converters under load (real AV/document/H5P conversions, S3
spool, large uploads) must be tested in staging before enabling the flag; activate afterwards, per chart.

## Env-var → Spring-property mapping (key pairs)
Two styles are used: bare env vars consumed by the `docker` profile properties, and direct
dotted Spring keys set as env entries.

| Env / key (compose) | Spring property | Default |
|---|---|---|
| `S3_HOST` / `S3_PORT` | `app.s3.host` / `app.s3.port` | `rendering2-rustfs-storage` / `9000` |
| `app.s3.accessKeyId` / `app.s3.secretAccessKey` | (direct) | `rendering2` / `rendering2` |
| `app.s3.bucket.mode` | (direct) | `byType` |
| `MONGODB_HOST` / `MONGODB_PORT` | `spring.mongodb.host` / `.port` | `mongo-database` / `27017` |
| `INFRA_WAIT_TIMEOUT` / `REPOSITORY_WAIT_TIMEOUT` | read only by the service `entrypoint.sh` (not Spring). `INFRA_WAIT_TIMEOUT`: max seconds to wait for each of Mongo/S3/RabbitMQ/Redis before the container exits 1. `REPOSITORY_WAIT_TIMEOUT`: best-effort total wait for `app.repository.registration.id.*.url` repos, then startup continues with a warning (`0` = do not wait). Helm `config.entrypoint.{infraWaitTimeout,repositoryWaitTimeout}`, compose `RENDERING2_SERVICE_{INFRA,REPOSITORY}_WAIT_TIMEOUT` | `300` / `60` |
| `RENDERING2_SERVICE_DATABASE_{NAME,USER}` | `spring.mongodb.database` / `.username` | `rendering` |
| `REDIS_HOST` / `REDIS_PORT` | `spring.redis.standalone.host` / `.port` | `redis-cache` / `6379` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `spring.rabbitmq.host` / `.port` | `rendering2-message-queue` / `5672` |
| `RENDERING2_QUEUE_<KEY>_CONCURRENCY` (Helm `config.queue.concurrency.<key>`) | `app.queue.<key>.concurrency` — the one per-queue scaling knob: max messages processed in parallel per pod. Meaning follows the queue's `mode` (code-only, in `application.properties`): STANDARD = registered `DirectMessageListenerContainer` consumers; REMOTE (sodix/omega/ddb — resolve a link/reference to externally-hosted material) = K (remote HTTP pool + prefetch, on virtual threads); SINGLE_ACTIVE (the h5p *import* queue) is forced to 1 and not exposed here — the h5p lookup queue in front of it is STANDARD and is exposed as `h5pLookup` | `1` (code); per-queue starting values in the chart |
| `RENDERING2_QUEUE_PREFETCH` (Helm `config.queue.prefetch`) | `app.queue.prefetch` — global prefetch for STANDARD/SINGLE_ACTIVE queues (remote queues derive prefetch from their concurrency) | `1` |
| `RENDERING2_QUEUE_<KEY>_PREFETCH` (Helm `config.queue.remotePrefetch.<key>`, `key` ∈ sodix/omega/ddb only) | `app.queue.<key>.prefetch` — optional per-REMOTE-queue prefetch override; must be >= the queue's concurrency (enforced at startup) | empty = derive from `concurrency` |
| `RENDERING2_SERVICE_CONVERTER_SPREADSHEET_TO_HTML_ENABLED` (Helm `config.converter.spreadsheet.enabled`) | `app.converter.spreadsheetToHtml.enabled` — gates `SpreadsheetRenderModule` via `@ConditionalOnProperty` (no `matchIfMissing`, so anything but `true` unregisters the module): spreadsheets (xls/xlsx/ods/csv) are converted to HTML by the document-converter, otherwise they fall back to the regular document rendering (PDF) | `true` |
| `app.public.{protocol,host,port,path}` | (direct) | `http` / nip.io host / `80` / `/rendering` |
| `server.servlet.context-path` | (direct) | `/rendering` |
| `app.session.<module>.nodePermissionExpirationTime` | (direct) | empty = not cached |
| `RENDERING2_LUMI_DATABASE_{NAME,USER}` | lumi Mongo db/user | `lumi` |
| `RENDERING2_LUMI_S3_MAX_SOCKETS` (Helm `config.s3.maxSockets`) | lumi `AWS_S3_MAX_SOCKETS` — parallel S3 connections; every H5P library/content file is one S3 request, so the AWS SDK default of 50 throttles the player | `256` |
| `RENDERING2_S3_RENDERING_BUCKET{,_QUOTA}` (Helm `config.home.registrations[0].buckets`, entry `renderingBucket`) | `app.repository.registration.id.<key>.externalBuckets.renderingBucket.{name,quota}` — externalBucket mode only; quota (0/empty = no limit, `@DataSizeUnit(BYTES)`-bound: a plain byte count or a human-readable size like `10GB`, Spring's binary units) enforced by the CacheCleaner | empty / `0` |
| `RENDERING2_S3_TEMP_BUCKET{,_QUOTA}` (Helm, entry `tempBucket`) | `app.repository.registration.id.<key>.externalBuckets.tempBucket.{name,quota}` — quota (same format as above) is informational only, never enforced (see `GET /admin/storage/usage?exact=true`) | empty / `0` |
| `RENDERING2_S3_DEFAULT_QUOTA` | `app.repository.registration.id.<key>.quota` — repo-wide fallback quota (same format as above), only in effect while none of the repo's buckets above (or the lumi content bucket) has its own quota | `0` |
| `RENDERING2_LUMI_CONTENT_BUCKET_QUOTA` (Helm `config.s3.buckets.contentQuota`, lumi chart) | lumi `CONTENT_AWS_S3_BUCKET_QUOTA` — quota for lumi's own content bucket (a plain byte count or a human-readable size like `10GB`, parsed by lumi's own `parseDataSize`, binary units matching the service side), queried live by rendering2 (`GET /edusharing/buckets`) instead of being duplicated into the rendering2 repository registration | `0` |
| `RENDERING2_LUMI_TEMP_BUCKET_EXPIRATION_DAYS` (Helm `config.s3.buckets.temporaryExpirationDays`, lumi chart) | lumi `TEMPORARY_AWS_S3_BUCKET_EXPIRATION_DAYS` — days until objects in the temp bucket expire (S3 lifecycle rule, re-applied on every lumi start, so a changed value takes effect after a restart; replaces any other lifecycle rule on that bucket) | `1` |
| `RENDERING2_LUMI_LIBRARY_CACHE` (Helm `config.libraryCache.mode`, lumi chart) | lumi `H5P_LIBRARY_CACHE` — `global` = one shared H5P library store, `package` = an isolated per-package cache holding each package's exact library versions. In `package` mode the cache directory (`H5P_LIBRARY_CACHE_DIR`, Helm `config.libraryCache.dir`) is authoritative and needs a volume. The chart follows the mode: the `volumeClaimTemplate` and its mount are rendered **only** for `package`, sized by `persistence.data.libraryCache.spec` — there is no separate create flag, so `package` without storage cannot be configured. Compose has no conditionals, so `rendering2-lumi-volume-library-cache` is always declared; in `global` mode it simply stays an empty, unused directory | `global` |
| `RENDERING2_LUMI_LIBRARY_CACHE_QUOTA` (Helm `config.libraryCache.quota`, lumi chart) | lumi `H5P_LIBRARY_CACHE_QUOTA` — **soft** size limit for the per-package library cache (plain byte count or `10GB`-style size, binary units); `0` = no limit. lumi never rejects an import for it: it reports size and quota via `GET /edusharing/buckets` (`libraryCache`) and the service's CacheCleaner frees the cache (least recently used H5P content first, through `DELETE /edusharing/:nodeHash`) when it is exceeded, as for the buckets. What really limits the cache is its volume - keep the quota below it. Exceeding it is logged (`WARN Library cache is over its soft quota`) and exported (`lumi_library_cache_over_quota`, `..._used_bytes`, `..._quota_bytes`) - alarm on it. Measured in **allocated blocks**, so it matches `du` on a PVC and `df` on a memory-backed volume | `0` |
| `RENDERING2_LUMI_LIBRARY_CACHE_RESCAN_HOURS` (Helm `config.libraryCache.rescanHours`, lumi chart) | lumi `H5P_LIBRARY_CACHE_RESCAN_HOURS` — how long a measurement of the library cache stays valid. The size is kept up to date incrementally; the full walk (a `stat` of every file, minutes for millions of them) only runs at a start when the last one is older than this, and then this often while running (always in the background, lumi is reachable at once). `0` = measure at every start only | `24` |
| `RENDERING2_LUMI_IMPORT_TIMEOUT_MS` (Helm `config.files.importTimeoutMs`) | lumi `H5P_IMPORT_TIMEOUT_MS` — deadline of one package import. Imports run strictly one at a time, so one that never returns used to block every later import until lumi was restarted (the service only saw timeouts). Past the deadline the request gets **504**, the S3 pool's sockets are destroyed (the usual reason is a socket that never answers) and, in `package` mode, the queue moves on. Keep it above the rendering service's H5P timeout (repository credential `timeout`, default 300 s, capped by `app.webclient.long-running-response-timeout-seconds` = 600 s), or lumi gives up on imports the service still waits for; raise both for packages near 2 GB. `0` = off | `900000` |
| `RENDERING2_LUMI_S3_POOL_STALL_MS` (Helm `config.s3.poolStallMs`) | lumi `AWS_S3_POOL_STALL_MS` — watchdog for the S3 connection pool: if the pool is full, requests wait and none settles for this long, its sockets are destroyed (leaked/never-answering sockets otherwise starve every request, and the SDK only warns once 512 requests are already queued). `0` = off | `120000` |
| `RENDERING2_LUMI_MAX_PACKAGE_SIZE` (Helm `config.files.maxPackageSize`) | lumi `H5P_MAX_TOTAL_SIZE` — maximum **unpacked** size of an H5P package (plain byte count or `4GB`-style size, binary units). Larger packages are rejected with `package-validation-failed:total-size-too-large`. The import streams from disk and is not bound by memory (a 2.3 GB package peaks at ~0.7 GB RSS), but `/tmp` needs room for the spooled upload plus the unpacked package | `4GB` |
| `RENDERING2_LUMI_CONTENT_WHITELIST_EXTRA` / `RENDERING2_LUMI_LIBRARY_WHITELIST_EXTRA` (Helm `config.files.contentWhitelistExtra` / `libraryWhitelistExtra`) | lumi `H5P_CONTENT_WHITELIST_EXTRA` / `H5P_LIBRARY_WHITELIST_EXTRA` — file extensions the package validator accepts **in addition** to its short defaults (content has no `svg`, `webp`, `mov`, `flac`; a package with such a file is rejected whole with 422 `not-in-whitelist`). Space or comma separated, case-insensitive, e.g. `svg webp`. Additive only, so a typo cannot shrink the defaults | empty |
| `RENDERING2_LUMI_MAX_PACKAGE_FILE_SIZE` (Helm `config.files.maxPackageFileSize`) | lumi `H5P_MAX_FILE_SIZE` — maximum size of a **single file** inside a package, same format. Not to be confused with `RENDERING2_LUMI_MAX_FILE_SIZE`, which is the S3 key length (`AWS_S3_MAX_FILE_LENGTH`) | `2GB` |
| Helm `persistence.data.temp.{create,spec}` (lumi chart; no compose variable) | Volume for lumi's `/tmp`, **opt-in, default off** - the same pattern as the service chart's `persistence.data.temp`. lumi spools every uploaded package to `/tmp` until its import is done, and `@lumieducation/h5p-server` unpacks it there too, so one import needs the size of the package plus its unpacked size (up to 2 GB + 4 GB with the default limits), plus a copy per waiting upload. Without the volume that is the node's ephemeral disk (no `ephemeral-storage` limit is set, so a backlog can push the node into disk pressure). Rendered as a second `volumeClaimTemplate` (`temp`, mounted at `/tmp`) next to `library-cache`; sized by `persistence.data.temp.spec` (default `20Gi`, merged with `global.cluster.storage.data.spec`). Compose needs nothing: `/tmp` lives in the container layer on the Docker host's disk | `create: false` |
| `RENDERING2_MOODLE_SHOW_PREVIEW_IFRAME` (Helm `config.home.registrations[0].modules`, entry `MOODLE`) | `app.repository.registration.id.<key>.module.MOODLE.credentials.showPreviewIframe` — display switch delivered to the rendering frontend in the job-info `additionalData` map (never sent to Moodle): embed the course in an iframe, or show its preview image instead. The "go to course" link is always shown either way. Only a literal `false` (any case) disables; blank or unset = enabled. When disabled the service also stops requesting the one-shot Moodle login token that only the iframe would have used. SCORM has its own copy of the whole credential bucket (`RENDERING2_SCORM_*` → `module.SCORM.credentials.*`), so `RENDERING2_SCORM_SHOW_PREVIEW_IFRAME` switches SCORM independently | empty = `true` |
| Robustness knobs of the service (one row per group; Helm values under `config.*`, compose `RENDERING2_SERVICE_*`; every Helm/compose default equals the code default, the keys are also listed in `service/.../application.properties`) | **Registration** `app.repository.registration.{repo-timeout-seconds,retry-interval-millis,retry-initial-delay-seconds,retry-max-delay-seconds}` (Helm `config.registration.*`); **binder** `app.module.binder.{sse-idle-timeout,max-file-size}` (`config.binder.*`); **reaper** `app.jobreaper.max-main-job-setup-time` (`config.jobreaper.maxMainJobSetupTime`); **repository SDK** `app.repository-http.{max-requests,max-requests-per-host,timeout.connect\|read\|write\|call}` (`config.repositoryHttp.*`); **scheduler** `app.scheduling.pool-size` (`config.scheduling.poolSize`); **MongoDB** `app.mongodb.timeout.{connect-timeout,socket-timeout,server-selection-timeout}` (`config.mongodb.timeout.*`); **Redis** `app.redis.client.*` and `app.redis.session.configure-action` (`config.redis.client.*`, `config.redis.session.configureAction`; `NO_OP` for managed Redis without `CONFIG`); **S3** `app.s3.client.*` (pool + timeouts) and `app.s3.health.{bucket,timeout}` (`config.s3.client.*`, `config.s3.health.*`; the health bucket need not exist); **RabbitMQ** `spring.rabbitmq.{connection-timeout,channel-rpc-timeout,requested-heartbeat,cache.channel.size,cache.channel.checkout-timeout}` (`config.rabbitmq.*`) - `cache.channel.size` is a hard cap, **raise it whenever any `app.queue.<x>.concurrency` is raised** (it must cover all consumer channels of a pod plus publishers) | see `application.properties` |
| Resilience layer and converter calls of the service (Helm `config.resilience.*`, `config.ddb.authMode`, `config.converter.call.*`; compose `RENDERING2_SERVICE_RESILIENCE_*`, `RENDERING2_SERVICE_DDB_AUTH_MODE`, `RENDERING2_SERVICE_CONVERTER_CALL_*`) | `app.resilience.{enabled,retry.{max-attempts,initial-backoff,max-backoff,jitter,max-retry-after,total-budget},circuit-breaker.{enabled,failure-threshold,open-duration,half-open-max-calls},bulkhead.{enabled,max-concurrent-calls,max-wait}}` for the remotes sodix/omega/omega-validation/ddb/github/lumi; per-remote overrides `app.resilience.modules.<name>.{max-attempts,failure-threshold,open-duration,max-concurrent-calls}` are not exposed as values - in Helm set them through `config.override` (`SPRING_APPLICATION_JSON`), in compose as plain dotted env keys. `app.module.ddb.auth-mode` (`QUERY`, or `HEADER` = unverified against the live API, opt-in only). `app.converter.call.{max-attempts,initial-backoff,max-backoff,attempt-timeout,total-budget,max-retry-after}` (retry budget for document/jupyter converter calls; `attempt-timeout` must stay above the converters' own limits, `total-budget` far below `app.jobreaper.default-max-process-time`) | `true` / `3`,`200ms`,`2s`,`0.5`,`5s`,`75s` / `true`,`5`,`30s`,`1` / `true`,`64`,`0s` / `QUERY` / `3`,`2s`,`20s`,`330s`,`15m`,`30s` |
| document-converter env (Helm `config.libreoffice.*` / `config.converter.*`, compose `RENDERING2_DOCUMENT_CONVERTER_*`) | `LIBREOFFICE_PROCESS_TIMEOUT` (`jodconverter.local.process-timeout`, ms), `LIBREOFFICE_PROCESS_RETRY_INTERVAL` (ms), `LIBREOFFICE_HEALTH_PROBE_ENABLED` / `_PROBE_TIMEOUT` / `_CACHE_TTL` / `_FAILURE_THRESHOLD` (`app.converter.health.*`, readiness only), `CONVERTER_WORK_DIR` (`app.converter.work-dir`, empty = tmpdir), `CONVERTER_STALE_FILE_AGE`, `CONVERTER_HTML_STYLE_SCAN_WINDOW`, `CONVERTER_MAX_HTML_DOM_SIZE` (`app.converter.{stale-file-age,html-style-scan-window,max-html-dom-bytes}`) | `120000` / `250` / `true` / `10s` / `30s` / `3` / empty / `6h` / `256KB` / `32MB` |
| jupyter-converter env (Helm `config.jupyter.*`; **no compose service exists** for it) | read by `main.py`, not Spring: `JUPYTER_CONVERSION_TIMEOUT` (s, 504), `JUPYTER_MAX_CONCURRENT_CONVERSIONS`, `JUPYTER_SLOT_WAIT_TIMEOUT` (s, 503), `JUPYTER_MAX_UPLOAD_BYTES` (413), `JUPYTER_UVICORN_WORKERS` / `_LIMIT_CONCURRENCY` (0 = unlimited) / `_TIMEOUT_KEEP_ALIVE`. `JUPYTER_HOST` / `JUPYTER_PORT` (defaults `0.0.0.0` / `8000`) are deliberately not in Helm: the container port and probes are fixed to 8000 | `300` / `4` / `30` / `52428800` / `1` / `0` / `5` |
| `BASE_HREF` / `ADMIN_API_BASE` (admin-frontend container) | static server, **not** Spring | `/rendering-admin/` / `/rendering` |

| `RENDERING2_LUMI_SHUTDOWN_TIMEOUT_MS` / `RENDERING2_LUMI_STOP_GRACE_PERIOD` (Helm `config.shutdown.timeoutMs`, empty = derived) | lumi `SHUTDOWN_TIMEOUT_MS` - how long a shutdown waits for a running import. Must stay below the container's stop grace (compose `stop_grace_period`, default 40s vs 30000 ms; Helm `terminationGracePeriod` - `preStopSleepSeconds` - 10 s) | `30000` (compose) |
| `RENDERING2_RESTART_POLICY` (compose only) | `restart:` of every service in `common`; `productive` overrides it with `unless-stopped` | `on-failure:5` |

**JVM flags:** the document-converter `ENTRYPOINT` forwards its container args (`"$@"`, e.g. the heap/JMX flags of compose `command:`) as JVM options; before, `sh -c` swallowed them as `$0`/`$1`. The service and document-converter Dockerfile `ENTRYPOINT`s start `java -XX:+ExitOnOutOfMemoryError ${JAVA_OPTS} ...` - the flag is baked into the image (not into `JAVA_OPTS`, which Helm/compose/dev override), so an `OutOfMemoryError` ends the JVM and k8s/compose restart the container instead of leaving a half-dead process. The `dev` compose overlay replaces the entrypoint and does not carry it. Helm's `javaOpts` helper only adds heap percentages, JMX and the debug agent.

Operational defaults worth knowing: every chart has a `preStopSleepSeconds` hook (5 s sleep before SIGTERM, so the endpoint removal reaches the ingress first), `securityContext.runAsNonRoot: true` (all images run as UID 1000 - the Dockerfiles' `worker`/`node` user matches Helm's `runAsUser`/`fsGroup` 1000), and a `PodDisruptionBudget` (`podDisruptionBudget.minAvailable|maxUnavailable`) that is only rendered when `global.cluster.pdb.enabled` is set **and** more than one replica runs (the HPA floor counts for the service roles). Compose healthchecks (mongo/redis/rabbitmq/rustfs/service/document-converter/lumi) gate `depends_on` via `service_healthy`.

(See `1_rendering2-common.yml` for the authoritative, complete list.)

## Build
This module builds with the system **`mvn`** (not the `./service/mvnw` wrapper):

```bash
mvn -Pdev -pl deploy/docker/build/service install   # build the service image
mvn -Pdev -pl deploy/docker/helm/service package    # package the service chart
```
Pushing images/charts happens on `deploy` and is normally driven by CI (`.gitlab-ci.yml`),
not run locally.
