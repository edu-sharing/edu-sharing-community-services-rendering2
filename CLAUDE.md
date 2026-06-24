# CLAUDE.md

Guidance for Claude Code when working in this repository. This is the **root** guide;
each module has its own `CLAUDE.md` with deeper, module-local detail — see the table below.

## Model usage
- **Planning / architecture / investigation → use Opus.** Anything that produces a plan, weighs trade-offs, or scopes a change.
- **Implementation / mechanical edits / following an agreed plan → use Sonnet.** Writing code, applying a reviewed plan, routine refactors, test fixes.

## Project overview
`edu_sharing-community-services-rendering2` — the edu-sharing rendering service: it
renders/converts content (images, audio/video, documents, H5P, Jupyter notebooks, etc.)
and serves it, integrating with edu-sharing repositories, MongoDB, Redis, RabbitMQ and
S3-compatible storage.

Multi-module Maven build. Parent is the external `org.edu_sharing:edu_sharing-super-pom`;
versions are derived from git by the `maven-git-versioning-extension` (the literal
`<version>git</version>` is expected — see **Versioning** below).

## Modules
| Module | Stack | Local guide | Notes |
|---|---|---|---|
| `service` | **Spring Boot 4.0.6**, Kotlin 2.3.0, Java 21 | [`service/CLAUDE.md`](service/CLAUDE.md) | Main rendering service. Primary place you'll work. |
| `document-converter` | **Spring Boot 3.5.14**, Kotlin 2.3.0 | [`document-converter/CLAUDE.md`](document-converter/CLAUDE.md) | LibreOffice/jodconverter document → HTML/PDF. Deliberately on 3.5.x. |
| `jupyter-converter` | Python (FastAPI) | — | `.ipynb` → HTML via nbconvert. Single-file service; not Spring/JVM. |
| `lumi` | Node.js / TypeScript | [`lumi/CLAUDE.md`](lumi/CLAUDE.md) | H5P editor/player frontend build artifact. Not Spring/JVM. |
| `admin-frontend` | **Angular 21** (standalone, Material) | [`admin-frontend/CLAUDE.md`](admin-frontend/CLAUDE.md) | Admin SPA (storage/jobs/repos/assets). Consumes the service's `/admin` API. Own container. Not Spring/JVM. |
| `deploy` | Docker / Helm / docker-compose | [`deploy/CLAUDE.md`](deploy/CLAUDE.md) | Dockerfiles, Helm charts (`docker/helm/*`), compose files (`docker/compose/*`). |

## Build & test
The Maven wrapper lives under `service/`, so the **`service`** module is built with
**`./service/mvnw`** (Maven 3.9). The other modules — **`document-converter`**, **`lumi`**,
**`admin-frontend`**, and **`deploy`** — are built with the system **`mvn`** (their build
commands live in their module guides). Source for JVM modules lives in `src/main/kotlin`;
the `admin-frontend` Angular workspace lives in `admin-frontend/src/main/frontend` and is
driven by the `frontend-maven-plugin` (Node pinned to a version available on the Artifactory
raw mirror).

- **Local builds must always use the `dev` profile** (`-Pdev`). Append it to every local `mvnw` invocation below.

```bash
# build + test the main service (and the reactor deps it needs)
./service/mvnw -Pdev -pl service -am clean verify
# compile only (fast feedback)
./service/mvnw -Pdev -pl service clean compile
# single test class
./service/mvnw -Pdev -pl service test -Dtest=SomeTest
# the other modules use the system mvn, e.g. the document-converter
mvn -Pdev -pl document-converter clean verify
```

- The **`dev` profile** is defined in the root `pom.xml`, activated by the `DEV=true` env
  var, and sets the enterprise BOM/repository prefix (`enterprise-${project.version}`).
  Without it local dependency resolution against the enterprise BOM fails.
- **Dependencies** resolve from the edu-sharing Artifactory (`artifacts.edu-sharing.com`)
  via `.mvn/settings.xml`. Reads work anonymously; deploys need `MAVEN_REMOTE_*` /
  `MAVEN_DEPLOY_*` env vars (the server credentials in `settings.xml` read from them).
- **Integration tests** (`service/src/test/.../integration/`) use **Testcontainers 2.0 and
  require a running Docker daemon** (they spin up Mongo, Redis, RabbitMQ, MinIO).
  Unit/`@WebMvcTest` slices do not.
- Build target is Java release **21**; Boot 4 supports JDKs up to 25 first-class (a newer
  local JDK compiling to release 21 is fine).

## Versioning
Versions are **not** hand-edited — the `me.qoomon:maven-git-versioning-extension` (9.11.0,
loaded via `.mvn/extensions.xml`) derives them from git. The literal `<version>git</version>`
in every POM is intentional; do not "fix" it. Rules (`.mvn/maven-git-versioning-extension.xml`):

- **tags** → `${ref.slug}` (e.g. `10.0.0`)
- **feature branches** matching `maven/feature/<version>-*` → `maven-fixes-<version>-SNAPSHOT`
- **other branches** → `<branch-slug>-SNAPSHOT`
- **detached HEAD** → `<commit.short>` (+ `-SNAPSHOT` if dirty)

CI uses a separate `.mvn/maven-git-versioning-extension-ci.xml` (no feature-branch special-casing).

## CI/CD
`.gitlab-ci.yml`: image `maven:3.9.9-eclipse-temurin-21`, Docker-in-Docker, two stages run
in order:
- **`verify`** — `verify:admin-api-contract` runs the `AdminOpenApiContractTest` (service
  module) and **fails the pipeline if the committed admin OpenAPI spec drifts** from what the
  current backend code produces. The `admin-frontend` client is generated from that spec
  (`admin-frontend/src/main/frontend/openapi/admin-api.json`), so this guards against a stale
  contract. Runs before `deploy`, blocking it on drift.
- **`deploy`** — runs `mvn $MAVEN_CLI -s .mvn/settings.xml deploy`. This builds and
  pushes the per-module Docker images (`docker-maven-plugin`) and Helm charts
  (`helm-maven-plugin`).

`$MAVEN_CLI` injects the Docker/Helm registry URLs, deploy repos,
and `-Dversioning.configFile=maven-git-versioning-extension-ci.xml`. surefire-reports are
retained as artifacts on failure.

## Tech & conventions (service)
- Kotlin, with `-Xjsr305=strict` and JSpecify nullability enforced — Spring APIs are
  nullness-annotated, so match nullability exactly when overriding/calling.
- Spring MVC (`spring-boot-starter-webmvc`) for REST + WebFlux `WebClient` for outbound
  calls; Spring Security (JWT filter, `@EnableMethodSecurity`); Spring Data MongoDB;
  Spring Data Redis + Spring Session (Redis-indexed); Spring AMQP/RabbitMQ; Actuator;
  springdoc OpenAPI; AWS SDK v2 (S3).
- **Jackson 3** (`tools.jackson.*`, not `com.fasterxml.jackson.*`). Only the annotations
  (`com.fasterxml.jackson.annotation.*`, e.g. `@JsonProperty`) keep the old package.
  Mappers are immutable — configure via `JsonMapper.builder()`.
- Config: `application.properties` + profile files (`-docker`, `-debug`). Active profile in
  containers is `docker`.
- **`spring.redis.standalone.*` / `spring.redis.cluster.*` are this project's own
  `@ConfigurationProperties` prefixes**, not Spring Boot keys — don't "fix" them to
  `spring.data.redis.*`.
- MongoDB connection uses Boot 4's `spring.mongodb.*` (not the removed
  `spring.data.mongodb.*`); `spring.data.mongodb.auto-index-creation` stays under Spring Data.

See [`service/CLAUDE.md`](service/CLAUDE.md) for the module plugin architecture, role-based
conditionals, the job flow, and the session/Redis serialization details.

## Deployment config
Helm/compose set Spring properties as dotted env keys (e.g. `spring.mongodb.uri`) and as
env vars consumed by the profile properties (`MONGODB_HOST`, `REDIS_HOST`, …). When you
rename a Spring property, update
`deploy/docker/helm/service/.../templates/{configmap-env,secret-env}.yaml` and
`deploy/docker/compose/src/main/compose/*.yml` to match. The `target/` copies under
`deploy/` are generated — **edit only `src/`**. The full env-var → property mapping lives in
[`deploy/CLAUDE.md`](deploy/CLAUDE.md).

## Conventions
- Commit/push only when asked; branch off `master` for PRs.
- Match the surrounding Kotlin style; keep changes minimal and verify with the relevant
  `mvnw` command before reporting done.
