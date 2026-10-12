# service — CLAUDE.md

Module-local guidance. The repo-root [`../CLAUDE.md`](../CLAUDE.md) covers build commands,
versioning, Jackson 3 / Kotlin conventions, and the Redis-prefix warnings — they apply here
and are not repeated.

## What this module is
The main rendering service: a **role-based, plugin-style** Spring Boot 4.1.1 app
(Kotlin 2.4, Java 21). It accepts signed render requests from edu-sharing repositories,
dispatches them to a content-type module (image, video, document, H5P, …), runs conversions
synchronously or as async jobs, stores results in S3, and serves the assets.

Package root: `org.edu_sharing.rendering` under `src/main/kotlin/org/edu_sharing/rendering/`.

## Package layout
| Package | Responsibility |
|---|---|
| `core` | Central request/response DTOs, exception handling (`ApiExceptionHandler`, `PublicApiException`, `ObjectTypeNotSupportedException`, …), ping. `core/annotation/` holds the role conditionals. |
| `modules` | The plugin system. `ModuleRegistry`, `ConversionModule`/`RenderModule` interfaces, `ModuleTypeMapper`/`ModuleTypeDefinition`, `ConverterWebServiceCaller`, plus one subpackage per content type: `image`, `av/{audio,video}`, `document`, `h5p`, `jupyter`, `binder`, `moodle`, `onyx`, `sodix`, `ddb`, `eduhtml`, `noConversion`. |
| `renderingJob` | Async jobs: Mongo `@Document` entities (`RenderingJob`, `SubJob`, statuses), repositories, and the RabbitMQ `queue/` config + receivers. Also `AdminJobController` (admin job stats/list/delete). |
| `edusharingRepo` | Repository registration, public-key fetch, metadata, content transfer, encryption; admin/tracking controllers (`AdminController`, `AdminStorageController`). |
| `storage` | S3 (AWS SDK v2) + bucket strategies (per-customer, per-media-type, external bucket — selected by `@ConditionalOn…` annotations). Health indicator. |
| `security` | JWT (`AuthTokenFilter`, `JwtUtils`), node-permission session repositories, `SecurityConfig` (dual filter chains for `/public/**` and `/admin/**`), CORS. |
| `asset` | `AssetController` (download/stream, HTTP Range support, CSP headers), `AssetService`; `AdminAssetController` (tracking-driven admin asset listing/deletion). |
| `cacheCleaner` | Scheduled threshold-based cleanup of cached renderings. A scope is a bucket **or** lumi's per-package H5P library cache (`StorageScopeKind`) — either exceeding its upper threshold triggers a cleanup, sized by `binarySize` or `librarySize` respectively. |
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
   `@Version`, and a TTL index (8 days); less-critical writes use a weaker write concern
   (`MongoConfig` `WriteConcernResolver`).

### Robustness of the job flow (RENDER-178)
- **Publish failures** — `MainJobCreationService` publishes through `JobMessagePublisher.publishForJob`: a publish
  exception, a broker nack or an unroutable return (mapped back via `JobCorrelationData` in `QueueConfig`'s confirm
  callback) sets the job FAILED (version-free update), freeing the `activeJobPerNodeHash` dedup entry. `BinderService`
  publishes its upload message the same way (its jobs are not deduplicated, so the benefit there is that the client
  sees FAILED immediately instead of QUEUED until the reaper's timeout); its optional preview message stays best effort
  - a lost message only fails the preview sub-job, as `BinderMainJobLogic` finishes the job when only that one fails.
  Other modules still call `amqpTemplate.convertAndSend` directly and can adopt `publishForJob` incrementally.
- **`JobReceiver` failure path** never throws out of the listener: FAILED is saved, retried once via targeted updates;
  if that also fails the job stays PROCESSING without sub-jobs and `StaleJobReaper` pass 4 resolves it after
  `app.jobreaper.max-main-job-setup-time` (default PT1H, must exceed the longest source download).
- **Redelivery idempotency** — the standard receivers (image/av/document/jupyter via `AbstractReceiver`) skip terminal
  sub-jobs (and only re-resolve the main job via the idempotent `processMainJob`); a sub-job left PROCESSING by a dead
  delivery is deliberately restarted.

### Error messages of a failed sub-job
`SubJob.errorMessage` is not text for the user but a **translation key of the repository UI**
(`ErrorStrings`, `RENDERING.ERROR.*`); most modules set `GENERIC_CONVERSION_ERROR`. A new key therefore needs
its translations in the **repository frontend** (`Frontend/src/assets/i18n/common/{de,en}.json`, section
`RENDERING.ERROR`) - this repo does not contain them. The frontend library
(`rendering2-frontend`) holds no texts and is deliberately left alone: its error module shows the heading plus
`translate(<message>)`, so a key without a text appears raw (`RENDERING.ERROR.SOMETHING`), while the plain-text
messages that Moodle, Sodix and Omega pass through are shown as they are. Do not "fix" that by hiding untranslated
messages - it would hide those too.

A failure is shown from the **main job's** `errorMessage` (`JobInfoReply.userMessage`): a failed sub-job is left out
of the job info and `MainJobLogic` only aggregates the status. A module that wants the user to see its message has
to set `jobEntry.errorMessage` too, as Moodle and the H5P import do. The H5P import is the one module that tells the user more (`H5pImportFailure`): lumi answers a package it
rejects with a status that says whose fault it is, and the service maps it -

| lumi status | key | meaning |
|---|---|---|
| 413 | `H5P_PACKAGE_TOO_LARGE` | the package exceeds a size limit (unpacked size or a single file) |
| 400, 422 | `H5P_PACKAGE_INVALID` | not a zip, a file type that is not allowed, a broken `h5p.json`, ... |
| anything else (500/503/504/507, timeout, connection error) | `GENERIC_CONVERSION_ERROR` | operational: the user cannot act on it, the details go to the log |

A rejected package is logged at WARN without a stack trace (it is not an error of the system); everything else
at ERROR as before. The statuses are lumi's - see its guide, *Why an import is rejected*.

### Quota metrics and alarms
All quotas are **soft**: nothing refuses a request because a bucket, a repository or lumi's H5P library cache is over
its quota. The `CacheCleaner` frees them on a schedule - and when it cannot keep up (it does not run, it fails, the
quota is too small for what is rendered) the only symptom would be a volume that runs full. So exceeding a quota is
**logged and exported**, and meant to be alarmed on (`StorageQuotaMetrics`, master only):

- **Log** (each cleaner run): `WARN Quota exceeded for <repo>/<bucket>: <size> of <quota> (<n>%)` above 100 %, and
  `WARN Cleanup of <scope> freed only X of the Y needed ... still over the quota` when a cleanup does not get a scope
  below the lower threshold. The `INFO` line with the usage of every scope is unchanged.
- **Gauges** per scope (tags `repo`, `bucket` - `*` for a repo-wide scope - and `kind` = `bucket` | `library_cache`):
  `rendering_storage_used_bytes`, `rendering_storage_quota_bytes` (0 = none), `rendering_storage_usage_ratio`,
  `rendering_storage_over_quota` (1 while used > quota) and `rendering_storage_over_threshold` (1 while above the
  cleaner's upper threshold - staying at 1 across runs means the cleaner does not keep up).
- **Cleaner**: counters `rendering_cache_cleaner_deleted_entries_total`, `..._freed_bytes_total` and `..._failures_total`
  (tag `kind`), and the gauge `rendering_cache_cleaner_last_run_timestamp_seconds`.

The values are those of the last run (the only time the sizes are collected); after a cleanup they are estimated from
what was freed. lumi exports the same for its own library cache (`lumi_library_cache_*`, see its guide). Suggested alarms:
`rendering_storage_over_quota == 1`, `rendering_storage_over_threshold == 1` for longer than two cleaner intervals,
`time() - rendering_cache_cleaner_last_run_timestamp_seconds` above a few intervals, `increase(..._failures_total[1h]) > 0`.
Not covered: the temp bucket's quota is informational only (never part of the cleaner's scopes).

## Admin API (`/admin/**`)
Consumed by the [`admin-frontend`](../admin-frontend/CLAUDE.md) SPA. All admin controllers are
`@ConditionalOnMaster` + `@SecurityRequirement("basicAuth")` and live in the
`privateAPIFilterChain` (HTTP-Basic, single `ROLE_ADMIN` user `app.security.adminPassword`,
CSRF off). springdoc groups them under the `administration` OpenAPI group (`/admin/**`); each
controller carries a `@Tag` (`repository`/`jobs`/`assets`/`storage`) that names the generated
`admin-frontend` client service. That client is generated from a committed snapshot of this
group's spec — `AdminOpenApiContractTest` regenerates/verifies it (see Testing); keep the two
in sync (the CI `verify` stage fails on drift). `SpringDocConfig` registers a Kotlin-aware
swagger `ModelResolver` (Jackson 2 `jackson-module-kotlin`) so non-null Kotlin DTO properties
are emitted as `required` — without it every generated TS field would be optional.
**Every fachlich endpoint is scoped to one `repoId`** (query param) — the UI shows only one
repo at a time. Controllers:
- `AdminStorageController` — `GET /admin/storage/usage` (per-repo bucket usage + quota %; plus one row of `kind` `LIBRARY_CACHE` for lumi's H5P library cache - a volume, not a bucket, so no exact measurement and not part of `totalSize`; present only while lumi runs the per-package cache with a quota and a known size), `POST /admin/storage/cleanup` (runs the `CacheCleaner` for one repo on demand, unlocked).
- `AdminJobController` — `GET /admin/jobs(/stats)`, `DELETE /admin/jobs/{id}` (deletes sub-jobs
  too; the queue self-heals as receivers drop messages without a DB entry).
- `AdminController` — repo registration (existing) + `GET /admin/repository/details`.
- `AdminAssetController` — **tracking-driven** (the `Tracking` collection has `repoId`; S3 keys
  don't in `byType` mode): list grouped by nodeId, versions, delete single/by-type/all.
  Deletions remove the S3 object **and** the `TrackingEntry` together.

## Config gotchas
- `spring.redis.standalone.*` (→ `RedisStandaloneConfigurationProperties`) and
  `spring.redis.cluster.*` (→ `RedisClusterConfigurationProperties`) are **this module's own**
  `@ConfigurationProperties` — not Spring Boot keys. `RedisConfig` picks cluster vs.
  standalone by whether the cluster props bean exists.
- `app.*` binds to `AppInfo` (`@ConfigurationProperties("app")`).
- MongoDB uses Boot 4 `spring.mongodb.*`. Outbound converter hosts:
  `app.lumi.host` (3000), `app.documentConverter.host` (8081), `app.jupyterConverter.host` (9120).
- **Resilience config (typed, defaults in code, all overridable):** `app.mongodb.timeout.*` (`MongoTimeoutProperties`),
  `app.redis.client.*` (`RedisClientProperties`, shared by the standalone and cluster Lettuce client),
  `app.redis.session.configure-action=NO_OP` (managed Redis without `CONFIG`), `app.s3.client.*` / `app.s3.health.*`
  (`S3ClientProperties`/`S3HealthProperties`), `app.scheduling.pool-size` (min 4), and in `application.properties` the
  Rabbit timeouts, the health groups and graceful shutdown. **Health groups:** `liveness` = `livenessState` only (must
  never depend on a subsystem - a restart does not fix Mongo/Redis); `readiness` = `readinessState,mongo,redis`
  (override `management.endpoint.health.group.readiness.include` to drop one). Helm's startup probe therefore also uses
  the liveness path. **Shutdown:** `spring.lifecycle.timeout-per-shutdown-phase` (36m) must stay above
  `QueueConfig`'s 35 min container `shutdownTimeout` and below Helm's `terminationGracePeriod` (2400 s). With
  `spring.rabbitmq.cache.channel.checkout-timeout` set, `cache.channel.size` is a hard cap - keep it above the sum of
  the queue concurrencies. All `MongoTemplate` writes are ACKNOWLEDGED.
- **Repository SDK (OkHttp) timeouts:** `app.repository-http.timeout.{connect,read,write,call}` (`RepositoryHttpTimeoutProperties`,
  defaults 10 s / 30 s / 30 s / 60 s; `0` = none) are applied by `RestClientProvider` and `UserBasedRestClientProvider`
  to every `ApiClient`; the generated client otherwise has no call timeout. A new SDK client factory must call
  `timeouts.applyTo(builder)` too. `CorsSyncService.syncAllowedOriginsWithAllRepositories` isolates failures per
  repository (sanitized WARN log, counter `cors.sync.failures`) so one unreachable repository no longer stalls the
  sync of the others. `ContentTransferService` maps fetch errors through `LogSanitizer` (the signed `authToken` is in
  the request URI that WebClient exceptions embed) - that wrapper drops the original exception type and cause chain.
- **Resilience layer (`core/resilience`, `app.resilience.*`, `ResilienceProperties`):** guards calls to sodix, omega,
  omega-validation, ddb, github and lumi. Retry with jittered backoff only for idempotent GET/HEAD (a 429 with a
  `Retry-After` above `retry.max-retry-after` is not retried; `retry.total-budget` caps one call incl. all attempts and
  must stay below the queue/reaper timeouts). The circuit breaker counts **consecutive** infrastructure failures (5xx,
  connect/reset, timeout - not 4xx) per remote **and pod** (state is not shared across pods); the bulkhead caps in-flight
  calls per remote and pod (keep >= the queue concurrency). Metrics are named `rendering.remote.*`. `enabled=false` makes
  the layer a pass-through; per-remote overrides via `app.resilience.modules.<name>.*`. `app.module.ddb.auth-mode=HEADER`
  keeps the DDB key out of URIs but is unverified against the live API (default `QUERY`).
- **Converter calls (`ConverterWebServiceCaller`, `app.converter.call.*`, `ConverterCallProperties`):** only transient
  status classes are retried (502/503/504, connection errors, timeouts; a 503 honours `Retry-After` up to
  `max-retry-after`); other 4xx/5xx fail at once. One attempt is bounded by `attempt-timeout` (330 s, above the
  converters' own limits), all attempts plus pauses by `total-budget` (15 min, far below the reaper's 30 min).
- The Welle-1 properties above are all mirrored (with their defaults) in `application.properties`, Helm
  (`configmap-env.yaml` / `values.yaml`) and compose; the mapping is in `../deploy/CLAUDE.md`.
- Per-module node-permission session caching expiry: `app.session.<module>.nodePermissionExpirationTime`.
- **RabbitMQ listener concurrency is not just `app.queue.<x>.concurrency`.** `DirectMessageListenerContainer`
  only uses `QueueConfig.rabbitConsumerExecutor` to bootstrap consumer registration; each consumer's message
  handling runs on whichever thread the RabbitMQ Java client's own, **connection-wide** `ConsumerWorkService`
  hands it — by default sized from `Runtime.availableProcessors()`, which rounds up to 1 on this app's
  cgroup-limited pods, serializing *every* queue's messages regardless of module. Boot's documented
  `ConnectionFactoryCustomizer` hook looks like the fix but has **no effect**: Spring's own
  `AbstractConnectionFactory.executorService` always overrides it when opening the connection. The actual
  fix — `QueueConfig.rabbitConnectionFactoryExecutorPostProcessor`, a `BeanPostProcessor` calling
  `AbstractConnectionFactory.setExecutor` directly — is validated with the k6 load test (see
  `../loadtest/README.md`): `rendering_queue_consumers_active` summed across queues, capped at 1 before the
  fix, reaches the sum of each queue's configured `concurrency` after it.

## Registration cache
`RegistrationCacheConfig` (`edusharingRepo/cache/`) enables `@EnableCaching` with a bounded, expiring Caffeine
`CacheManager` (`app.registration-cache.*`) for `registrations`, `repositoryKeys` and `privateKey`. It is
**pod-local**: `RepositoryRegistrationStorageService.storeRegistration/removeRegistration` publish a
`RegistrationChangedMessage` on the fanout exchange `app.queue.registrationBroadcastExchange`, and
`RegistrationCacheReceiver` (every role, anonymous queue) evicts the entry on all *other* pods (own `origin` is
skipped). The TTL only covers a missed broadcast. Read-modify-write paths must use
`getRegistrationByRepoIdFresh` (never mutate the shared cached instance and save it back). A missing registration
is deliberately not cached. Keep `RegistrationCacheTest` and `RegistrationCacheBroadcastIntegrationTest` green.

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
- **Admin OpenAPI contract**: `AdminOpenApiContractTest` boots the context, fetches the
  `administration` group spec, normalizes volatile fields (`servers`, `info.version`) and
  compares to the committed `admin-frontend/src/main/frontend/openapi/admin-api.json`. Run with
  `-Dopenapi.spec.update=true` to (re)write that file. (The springdoc group config lives in
  `src/main/resources/application.properties`; the test mirrors it in the test
  `application.properties` since the latter shadows main on the test classpath.)
- **Slices**: `@WebMvcTest` controllers exclude `SecurityAutoConfiguration` and use `MockMvc`.
- **Mocking**: `@MockkBean` (springmockk) for Kotlin beans; OkHttp `mockwebserver` for
  outbound HTTP; `spring-rabbit-test` for AMQP.

```bash
./service/mvnw -Pdev -pl service clean compile          # fast feedback
./service/mvnw -Pdev -pl service -am clean verify        # full build + tests
./service/mvnw -Pdev -pl service test -Dtest=ModuleRegistryTest
```
