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
  extraction, non-root `worker` user, entrypoint sets `-Dspring.profiles.active=docker`.
  The document-converter image additionally installs **LibreOffice + fonts**.
- **lumi**: `node:21-alpine`, non-root `node` user, runs `dist/index.js`, exposes 3000.
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
| `RENDERING2_QUEUE_<KEY>_CONSUMERS` (Helm `config.queue.consumersPerQueue.<key>`) | `app.queue.<key>.consumersPerQueue` — fixed number of `DirectMessageListenerContainer` consumers registered per queue (all consume from the shared virtual-thread executor; no auto-scale, idle consumers hold no thread) | `1` (code); per-queue starting values in the chart |
| `RENDERING2_QUEUE_PREFETCH` (Helm `config.queue.prefetch`) | `app.queue.prefetch` — unacked messages buffered per consumer, one global value for all queues | `1` |
| `app.public.{protocol,host,port,path}` | (direct) | `http` / nip.io host / `80` / `/rendering` |
| `server.servlet.context-path` | (direct) | `/rendering` |
| `app.session.<module>.nodePermissionExpirationTime` | (direct) | empty = not cached |
| `RENDERING2_LUMI_DATABASE_{NAME,USER}` | lumi Mongo db/user | `lumi` |
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
