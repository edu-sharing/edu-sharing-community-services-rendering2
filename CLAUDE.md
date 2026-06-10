# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Model usage
- **Planning / architecture / investigation → use Opus.** Anything that produces a plan, weighs trade-offs, or scopes a change.
- **Implementation / mechanical edits / following an agreed plan → use Sonnet.** Writing code, applying a reviewed plan, routine refactors, test fixes.

## Project overview
`edu_sharing-community-services-rendering2` — the edu-sharing rendering service: it renders/converts content (images, audio/video, documents, H5P, etc.) and serves it, integrating with edu-sharing repositories, MongoDB, Redis, RabbitMQ and S3-compatible storage.

Multi-module Maven build. Parent is the external `org.edu_sharing:edu_sharing-super-pom`; versions are derived from git by the `maven-git-versioning-extension` (the literal `<version>git</version>` is expected).

## Modules
| Module | Stack | Notes |
|---|---|---|
| `service` | **Spring Boot 4.0.6**, Kotlin 2.3.0, Java 21 | Main rendering service. Primary place you'll work. |
| `document-converter` | **Spring Boot 3.5.14**, Kotlin 2.3.0 | LibreOffice/jodconverter-based document → HTML/PDF. Deliberately on 3.5.x (jodconverter has no Boot-4 release yet). |
| `jupyter-converter` | Python | Not Spring/JVM. |
| `lumi` | Node.js | H5P frontend build artifact. Not Spring/JVM. |
| `deploy` | Docker / Helm / docker-compose | Container, Helm charts (`docker/helm/*`), and compose files (`docker/compose/*`). |

## Build & test
Use the Maven wrapper at `./service/mvnw` (Maven 3.9). Source lives in `src/main/kotlin`.

- **Local builds must always use the `dev` profile** (`-Pdev`). Append it to every local `mvnw` invocation below.

```bash
# build + test the main service (and the reactor deps it needs)
./service/mvnw -Pdev -pl service -am clean verify
# compile only (fast feedback)
./service/mvnw -Pdev -pl service clean compile
# single test class
./service/mvnw -Pdev -pl service test -Dtest=SomeTest
# both Spring modules
./service/mvnw -Pdev -pl service,document-converter clean verify
```

- **Dependencies** resolve from the edu-sharing Artifactory (`artifacts.edu-sharing.com`) via `.mvn/settings.xml`. Reads work anonymously; deploys need `MAVEN_REMOTE_*` / `MAVEN_DEPLOY_*` env vars.
- **Integration tests** (`service/src/test/.../integration/`) use **Testcontainers 2.0 and require a running Docker daemon** (they spin up Mongo, Redis, RabbitMQ, MinIO). Unit/`@WebMvcTest` slices do not.
- Build target is Java release **21**; Boot 4 supports JDKs up to 25 first-class (a newer local JDK compiling to release 21 is fine).

## Tech & conventions (service)
- Kotlin, with `-Xjsr305=strict` and JSpecify nullability enforced — Spring APIs are nullness-annotated, so match nullability exactly when overriding/calling.
- Spring MVC (`spring-boot-starter-webmvc`) for REST + WebFlux `WebClient` for outbound calls; Spring Security (JWT filter, `@EnableMethodSecurity`); Spring Data MongoDB; Spring Data Redis + Spring Session (Redis-indexed); Spring AMQP/RabbitMQ; Actuator; springdoc OpenAPI; AWS SDK v2 (S3).
- **Jackson 3** (`tools.jackson.*`, not `com.fasterxml.jackson.*`). Only the annotations (`com.fasterxml.jackson.annotation.*`, e.g. `@JsonProperty`) keep the old package. Mappers are immutable — configure via `JsonMapper.builder()`.
- Config: `application.properties` + profile files (`-docker`, `-debug`). Active profile in containers is `docker`.
- **`spring.redis.standalone.*` / `spring.redis.cluster.*` are this project's own `@ConfigurationProperties` prefixes**, not Spring Boot keys — don't "fix" them to `spring.data.redis.*`.
- MongoDB connection uses Boot 4's `spring.mongodb.*` (not the removed `spring.data.mongodb.*`); `spring.data.mongodb.auto-index-creation` stays under Spring Data.

## Deployment config
Helm/compose set Spring properties as dotted env keys (e.g. `spring.mongodb.uri`) and as env vars consumed by the profile properties (`MONGODB_HOST`, `REDIS_HOST`, …). When you rename a Spring property, update `deploy/docker/helm/service/.../templates/{configmap-env,secret-env}.yaml` and `deploy/docker/compose/src/main/compose/*.yml` to match. The `target/` copies under `deploy/` are generated — edit only `src/`.

## Conventions
- Commit/push only when asked; branch off `master` for PRs.
- Match the surrounding Kotlin style; keep changes minimal and verify with the relevant `mvnw` command before reporting done.
