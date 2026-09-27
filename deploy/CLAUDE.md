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

## Env-var → Spring-property mapping (key pairs)
Two styles are used: bare env vars consumed by the `docker` profile properties, and direct
dotted Spring keys set as env entries.

| Env / key (compose) | Spring property | Default |
|---|---|---|
| `S3_HOST` / `S3_PORT` | `app.s3.host` / `app.s3.port` | `rendering2-rustfs-storage` / `9000` |
| `app.s3.accessKeyId` / `app.s3.secretAccessKey` | (direct) | `rendering2` / `rendering2` |
| `app.s3.bucket.mode` | (direct) | `byType` |
| `MONGODB_HOST` / `MONGODB_PORT` | `spring.mongodb.host` / `.port` | `mongo-database` / `27017` |
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
| `RENDERING2_LUMI_LIBRARY_CACHE` (Helm `config.libraryCache.mode`, lumi chart) | lumi `H5P_LIBRARY_CACHE` — `global` = one shared H5P library store, `package` = an isolated per-package cache holding each package's exact library versions. In `package` mode the cache directory (`H5P_LIBRARY_CACHE_DIR`, Helm `config.libraryCache.dir`) is authoritative and needs a volume. The chart follows the mode: the `volumeClaimTemplate` and its mount are rendered **only** for `package`, sized by `persistence.data.libraryCache.spec` — there is no separate create flag, so `package` without storage cannot be configured. Compose has no conditionals, so `rendering2-lumi-volume-library-cache` is always declared; in `global` mode it simply stays an empty, unused directory | `global` |
| `RENDERING2_LUMI_LIBRARY_CACHE_QUOTA` (Helm `config.libraryCache.quota`, lumi chart) | lumi `H5P_LIBRARY_CACHE_QUOTA` — size limit for the per-package library cache (plain byte count or `10GB`-style size, binary units); `0` = no limit. On reaching it imports are rejected with HTTP 507 (the CacheCleaner breathes on the volume below that, see the service guide). Measured in **allocated blocks**, so it matches `du` on a PVC and `df` on a memory-backed volume — keep it below the volume size. Reported via `GET /edusharing/buckets` (`libraryCache`) | `0` |
| `RENDERING2_MOODLE_SHOW_PREVIEW_IFRAME` (Helm `config.home.registrations[0].modules`, entry `MOODLE`) | `app.repository.registration.id.<key>.module.MOODLE.credentials.showPreviewIframe` — display switch delivered to the rendering frontend in the job-info `additionalData` map (never sent to Moodle): embed the course in an iframe, or show its preview image instead. The "go to course" link is always shown either way. Only a literal `false` (any case) disables; blank or unset = enabled. When disabled the service also stops requesting the one-shot Moodle login token that only the iframe would have used. SCORM has its own copy of the whole credential bucket (`RENDERING2_SCORM_*` → `module.SCORM.credentials.*`), so `RENDERING2_SCORM_SHOW_PREVIEW_IFRAME` switches SCORM independently | empty = `true` |
| `BASE_HREF` / `ADMIN_API_BASE` (admin-frontend container) | static server, **not** Spring | `/rendering-admin/` / `/rendering` |

(See `1_rendering2-common.yml` for the authoritative, complete list.)

## Build
This module builds with the system **`mvn`** (not the `./service/mvnw` wrapper):

```bash
mvn -Pdev -pl deploy/docker/build/service install   # build the service image
mvn -Pdev -pl deploy/docker/helm/service package    # package the service chart
```
Pushing images/charts happens on `deploy` and is normally driven by CI (`.gitlab-ci.yml`),
not run locally.
