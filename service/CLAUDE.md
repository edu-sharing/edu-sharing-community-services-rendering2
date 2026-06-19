# service — CLAUDE.md

Module-local guidance. The repo-root [`../CLAUDE.md`](../CLAUDE.md) covers build commands,
versioning, Jackson 3 / Kotlin conventions, and the Redis-prefix warnings — they apply here
and are not repeated.

## What this module is
The main rendering service: a **role-based, plugin-style** Spring Boot 4.0.6 app
(Kotlin 2.3, Java 21). It accepts signed render requests from edu-sharing repositories,
dispatches them to a content-type module (image, video, document, H5P, …), runs conversions
synchronously or as async jobs, stores results in S3, and serves the assets.

Package root: `org.edu_sharing.rendering` under `src/main/kotlin/org/edu_sharing/rendering/`.

## Package layout
| Package | Responsibility |
|---|---|
| `core` | Central request/response DTOs, exception handling (`ApiExceptionHandler`, `PublicApiException`, `ObjectTypeNotSupportedException`, …), ping. `core/annotation/` holds the role conditionals. |
| `modules` | The plugin system. `ModuleRegistry`, `ConversionModule`/`RenderModule` interfaces, `ModuleTypeMapper`/`ModuleTypeDefinition`, `ConverterWebServiceCaller`, plus one subpackage per content type: `image`, `av/{audio,video}`, `document`, `h5p`, `jupyter`, `binder`, `moodle`, `onyx`, `sodix`, `ddb`, `eduhtml`, `noConversion`. |
| `renderingJob` | Async jobs: Mongo `@Document` entities (`RenderingJob`, `SubJob`, statuses), repositories, and the RabbitMQ `queue/` config + receivers. |
| `edusharingRepo` | Repository registration, public-key fetch, metadata, content transfer, encryption; admin/tracking controllers. |
| `storage` | S3 (AWS SDK v2) + bucket strategies (per-customer, per-media-type, external bucket — selected by `@ConditionalOn…` annotations). Health indicator. |
| `security` | JWT (`AuthTokenFilter`, `JwtUtils`), node-permission session repositories, `SecurityConfig` (dual filter chains for `/public/**` and `/admin/**`), CORS. |
| `asset` | `AssetController` (download/stream, HTTP Range support, CSP headers), `AssetService`. |
| `cacheCleaner` | Scheduled threshold-based cleanup of cached renderings. |
| `config` | `AppInfo`, `RedisConfig` + the two Redis property classes, `MongoConfig`, `SessionConfig`, `SchedulingConfig`, `SpringDocConfig`, `SpringConfig`. |

## Module plugin architecture
`ModuleRegistry` indexes every registered module's `ModuleTypeDefinition` and resolves the
handler for a node in this **fallback order** (`getRenderModule`):

```
modulesByType[type]                              // node.mediatype
  ?: modulesByRemoteRepositoryType[…]            // node.remote.repository.repositoryType
  ?: modulesByReplicationSource[…]               // ccm:replicationsource
  ?: modulesByResourceType[…]                    // ccm:ccressourcetype
  ?: moduleByMimeType[mimeType]                   // exact "prefix/suffix"
  ?: modulesByMimeTypePrefix[mimeType.before("/")]// e.g. "image", "video"
  ?: throw ObjectTypeNotSupportedException()
```

To add a content type: implement `RenderModule` (and `ConversionModule` if it converts),
declare its `ModuleTypeDefinition` (set the discriminator field that matches the dispatch
key you want), and gate it with the right role conditional. Match the structure of an
existing module subpackage (e.g. `modules/image`).

## Role-based conditionals
`app.roles` (default `master,controller,converter,avconverter,job-manager`) controls which
beans are wired. The annotations in `core/annotation/` drive this via SpEL on `app.roles`:
`@ConditionalOnMaster`, `@ConditionalOnController`, `@ConditionalOnConverter`,
`@ConditionalOnJobManager`, `@ConditionalOnMasterOrController`,
`@ConditionalOnMasterAndRegistration`. A deployment can run as a subset of roles; beans for
inactive roles never load. The role tests under `src/test` exercise each role's bean set —
when you add a role-gated bean, extend the matching role test.

## Request / job flow
1. `POST /public/renderdata` — body carries base64 node data signed by the repository.
2. Signature verified against the repository's public key (`edusharingRepo`).
3. `ModuleRegistry` resolves the module; the module returns either **immediate links** or a
   **job id**.
4. Async path: a message goes onto a RabbitMQ queue → `JobReceiver` → the module's sub-job
   receiver runs the conversion → result lands in S3 (`storage`).
5. `RenderingJob`/`SubJob` persist in MongoDB with `WriteConcern.ACKNOWLEDGED`, optimistic
   `@Version`, and a TTL index; less-critical writes use a weaker write concern
   (`MongoConfig` `WriteConcernResolver`).

## Config gotchas
- `spring.redis.standalone.*` (→ `RedisStandaloneConfigurationProperties`) and
  `spring.redis.cluster.*` (→ `RedisClusterConfigurationProperties`) are **this module's own**
  `@ConfigurationProperties` — not Spring Boot keys. `RedisConfig` picks cluster vs.
  standalone by whether the cluster props bean exists.
- `app.*` binds to `AppInfo` (`@ConfigurationProperties("app")`).
- MongoDB uses Boot 4 `spring.mongodb.*`. Outbound converter hosts:
  `app.lumi.host` (3000), `app.documentConverter.host` (8081), `app.jupyterConverter.host` (9120).
- Per-module node-permission session caching expiry: `app.session.<module>.nodePermissionExpirationTime`.

## Session / Redis serialization
`SessionConfig` stores Spring Sessions in Redis using a Jackson 3 (`tools.jackson.*`)
`GenericJacksonJsonRedisSerializer` with Kotlin + Spring Security modules and unsafe default
typing enabled — this is what makes `SecurityContext`, `Duration`, `Long`, etc. round-trip.
Node permissions are cached via `NodeSessionContextRepository`. If you touch session
serialization, keep `SessionConfigTest` green (it asserts the Kotlin/Security round-trip).

## Kotlin / Jackson specifics
- `-Xjsr305=strict` + JSpecify: nullability is enforced against Spring's annotated APIs.
- The kotlin-allopen plugin opens `@Document` classes (MongoDB needs a no-arg/open class).
- Jackson 3 mappers are immutable — build via `JsonMapper.builder()`; never mutate a mapper.

## Testing
- **Integration**: extend `AbstractIntegrationTest` — Testcontainers 2.0 spins up Mongo,
  Redis, RabbitMQ, and MinIO and registers their endpoints via `@DynamicPropertyRegistry`.
  **Requires a running Docker daemon.**
- **Slices**: `@WebMvcTest` controllers exclude `SecurityAutoConfiguration` and use `MockMvc`.
- **Mocking**: `@MockkBean` (springmockk) for Kotlin beans; OkHttp `mockwebserver` for
  outbound HTTP; `spring-rabbit-test` for AMQP.

```bash
./service/mvnw -Pdev -pl service clean compile          # fast feedback
./service/mvnw -Pdev -pl service -am clean verify        # full build + tests
./service/mvnw -Pdev -pl service test -Dtest=ModuleRegistryTest
```
