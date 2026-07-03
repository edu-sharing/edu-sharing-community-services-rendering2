# Memory-Usage Audit — Rendering Service

_Audit date: 2026-07-01 · Scope: `service/` module (Spring Boot 4.0.6 / Kotlin 2.3 / Java 21)_

This report documents a meticulous, subsystem-by-subsystem review of heap and off-heap
memory behaviour in the rendering service, with concrete failure math and prioritized
remediations. It was produced by fanning out seven parallel analyses (storage/S3,
content-transfer/WebClient, image, audio/video, asset-serving, converter/proxy modules,
persistence/caching/config) and then verifying every headline claim directly against the
source.

All file:line references were confirmed against the working tree on branch
`maven/feature/10.0-omega`.

---

## 1. Executive summary

The **read/serve path is memory-safe** — asset download streams straight from S3 with HTTP
Range offloaded to S3, using ~8 KB of heap per client regardless of file size. The **write /
convert path is where the memory risk lives**: several conversion and upload paths
materialize an entire media object (or a whole converter response) into a single `ByteArray`
on the heap, with no size guard.

The single structural root cause that amplifies most findings:

> **`CacheObject.size` defaults to `-1` (`core/dto/CacheObject.kt:6`), and the S3 upload path
> falls back to `inputStream.readAllBytes()` whenever `size < 0`
> (`storage/S3StorageService.kt:203` and `:457`).** Any call site that uploads without setting
> a content length therefore buffers the whole object in heap — even when the bytes are
> already on disk in a temp file.

Fixing the size-propagation chain plus five specific call sites removes essentially all of
the critical heap exposure, mostly via one- or few-line changes.

### Severity tally

| Severity | Count | Headline items |
|---|---|---|
| **Critical** | 2 | Full-content `readAllBytes()` on the primary download→S3 path; unbounded per-session `Node`/permission lists in Redis |
| **High** | 5 | AV output `readBytes()` before upload; full-resolution `BufferedImage` decode; converter response double-buffer; Lumi proxy `ByteArray`; inert `@Cacheable` (perf/GC) |
| **Medium** | 6 | 1 GiB codec ceiling on metadata client; FluxInputStream lifecycle/scheduling; eduhtml zip-bomb/zip-slip; admin delete-by-type unbounded; GitHub file buffer; ImageIO disk cache |
| **Low** | several | see §5 |

### Top 6 fixes by leverage

1. **Propagate content length + stop `readAllBytes()`** on the S3 upload path (`S3StorageService.kt:197-205`, `:451-459`). Prefer `RequestBody.fromFile(...)` / streamed `fromInputStream(stream, length)`. Removes the Critical download-path buffer and the converter/eduhtml double-copies at once.
2. **AV upload**: replace `outputFile.readBytes().inputStream()` with a streamed file upload (`AvFileHelper.kt:37`). One line.
3. **Image decode**: read dimensions first and decode with subsampling; add a max-megapixel guard (`ImageConversionService.kt:74-75`).
4. **Bound the session node/permission lists** (`NodeSessionContextRepository.kt`, `NodePermissionSessionContextRepository.kt`).
5. **Stream the Lumi asset proxy** instead of `ResponseEntity<ByteArray>` (`LumiProxyController.kt`).
6. **Decide `@Cacheable` intent** — add `@EnableCaching` + a bounded Caffeine `CacheManager`, or remove the inert annotations.

---

## 2. Method & the shared write-path model

Content flows through the service as: repo `/content` download → (convert) → S3 upload →
later served to clients. The download and serve legs are streamed. The **upload leg** is
governed by `S3StorageService.putObjectInternal` / `putTempFile`:

```kotlin
// storage/S3StorageService.kt:451-459
val body =
    if (cacheObject.size >= 0) {
        RequestBody.fromInputStream(inputStream, cacheObject.size)   // ✅ streams, bounded heap
    } else {
        // AWS SDK v2 sync client needs a known content-length for InputStream.
        // Fallback: buffer in-memory to determine length.
        val bytes = inputStream.readAllBytes()                        // ❌ whole object → heap
        RequestBody.fromBytes(bytes)
    }
```

The AWS SDK v2 **synchronous** S3 client requires a known content length for an
`InputStream`. So whenever a caller has not set `cacheObject.size`, the entire object is read
into a `ByteArray`. `CacheObject.size` defaults to `-1`, and multiple hot call sites either
never set it or explicitly set it to `-1`. This is the mechanism behind Findings F1, F3, F8,
F9. It also means claims elsewhere in the codebase of "streamed to a temp file" are partly
defeated — even when the source is streamed to disk, the upload re-materializes it in heap.

Concurrency multiplier: heavy conversions run on RabbitMQ receivers with `prefetch=1` /
`concurrentConsumers=1` (`renderingJob/queue/QueueConfig.kt:22-23`), but that factory is
**shared across ~13 distinct `@RabbitListener`s** (image, av, document, h5p, jupyter, binder,
onyx, sodix, ddb, eduHtml, moodle, binderPreview, plus `JobReceiver`). Each queue runs one
consumer, but the queues run **in parallel** on a single JVM when `app.roles` is the default
`master,controller,converter,avconverter,job-manager`. Peak heap ≈ Σ over active heavy
receivers of (object size × in-flight copies). The user-facing controller paths
(asset-serving, Lumi proxy) are additionally bounded only by Tomcat's default
`max-threads=200`, not by the queue.

---

## 3. Critical findings

### F1 — Whole content buffered into heap on the primary download → S3 path
**Severity: Critical** · `edusharingRepo/services/ContentTransferService.kt:37,65` →
`storage/S3StorageService.kt:203,457` · driven by `renderingJob/queue/JobReceiver.kt:53,56`

`ContentTransferService.getAsInputStream()` sets `cacheObject.size` **only** on the `TEST_`
classpath branch (`:37`); on the real download it stays at the `-1` default
(`core/dto/CacheObject.kt:6`). When `JobReceiver` hands the streamed download to
`putObject`/`putTempFile`, the `size < 0` branch fires and `readAllBytes()` materializes the
entire downloaded object into one `ByteArray`.

**Impact:** The carefully streamed 64 KB pipe (see §"already good") is defeated at the S3
layer. A 500 MB original → ~500 MB heap `byte[]` per job; a >2 GiB object throws
`OutOfMemoryError: Requested array size exceeds VM limit` (Java arrays cap at ~2.1 GB). With N
parallel heavy receivers, heap ≈ N × object-size. This is the single highest-risk item.

**Fix:** Capture the real `Content-Length` from the `/content` response (or a prior HEAD) and
set `cacheObject.size` before the stream is consumed, so the streaming branch is taken.
Alternatively spool to a temp file in `ContentTransferService` and upload via
`RequestBody.fromFile(...)`, or use the S3 async/multipart client / `S3TransferManager` which
streams without a known total length.

### F2 — Unbounded per-session `Node` + permission lists in Redis, serialized with unsafe default typing
**Severity: Critical** · `security/NodeSessionContextRepository.kt:25-31`,
`security/NodePermissionSessionContextRepository.kt:73-79`; serializer
`config/SessionConfig.kt:39-64`

Each Spring Session holds two Redis-backed `MutableList`s — `"nodes"` (full edu-sharing
`Node` objects) and `"permissions"`. `saveNode` de-dupes by id but has **no cap**:

```kotlin
// NodeSessionContextRepository.kt:25-31  (verified)
fun saveNode(node: Node) {
    val session = getSession(true) ?: return
    val nodes = readNodesFromSession(session) ?: mutableListOf()
    nodes.removeAll { it.ref.id == node.ref.id }
    nodes.add(node)
    session.setAttribute(NODES, nodes)          // whole growing list re-serialized every call
}
```

Entries are removed only by TTL (`app.session.<module>.nodePermissionExpirationTime`; default
60 s, but audio/video override to 7200 s = 2 h), and expiry runs only when a request touches
that session.

**Impact:** A long-lived session (a viewer cycling through many assets, or a crawler reusing a
cookie) accumulates full `Node` blobs (realistically 2–20 KB each) in one Redis key. The
**entire list is re-serialized and re-written on every `setAttribute`** → O(n²) bytes over a
session's life. `GenericJacksonJsonRedisSerializer` with `enableUnsafeDefaultTyping()`
(`SessionConfig.kt`) tags every nested object with `@class`, inflating the payload and adding
a polymorphic-deserialization gadget surface. Deserialization happens on the Tomcat request
thread, so cost is multiplied by concurrent request threads (default 200).

**Fix:** Cap both lists (LRU with a hard max, e.g. 50; evict oldest on overflow). Store only
the minimal fields dispatch actually needs (`mediatype`/`mimeType`/`resourcetype`, `ref.id`)
rather than the full `Node`. Run `validateSessionPermissions()` before each append so expired
entries can't accumulate between reads. Replace `allowIfSubType { _, _ -> true }` with a
`BasicPolymorphicTypeValidator` allow-list.

---

## 4. High findings

### F3 — AV: entire converted media file read into heap before upload
**Severity: High** · `modules/av/AvFileHelper.kt:37`

```kotlin
storageImplementation.putObject(cacheObject, outputFile.readBytes().inputStream(), metaData)
```

The transcoded output already sits on disk as a temp file, yet `readBytes()` slurps it whole
into a `ByteArray`. Callers set `cacheObject.size = outputFile.length()` beforehand
(`video/VideoConversionService.kt:80`, `audio/AudioConversionService.kt:56`), so the S3
streaming branch is used — but the heap copy has already happened, and `readBytes()` grows
buffers so peak transient allocation is up to ~2× file size.

**Impact:** 1080p output routinely 100–500 MB, longer lectures ~1 GB+; a single large video
can OOM a converter sized for typical workloads. AV encoding is serialized (1 consumer), so
the multiplier inside AV is 1, but it stacks on whatever other parallel receivers hold.
Fixtures `bbb1080.mp4`, `earthRotating.mp4`, `video720.mp4` confirm large-video scenarios.

**Fix:** `putObject(cacheObject, outputFile.inputStream(), metaData)` (size already set → streams),
or add a `putObject(cacheObject, file)` overload using `RequestBody.fromFile(outputFile.toPath())`.
Zero heap buffering; temp file lifecycle unchanged (deleted in `close()`).

### F4 — Image: full-resolution `BufferedImage` decoded regardless of thumbnail target
**Severity: High (Critical for unbounded inputs)** · `modules/image/ImageConversionService.kt:74-75`,
`:43-49`, `:110-166`

`fetchSourceImage` reads the whole source into `bytes` (`:74`), then `ImageIO.read` (`:75`)
decodes it to a `BufferedImage` at **native resolution** — independent of file size, since a
JPEG is ~10:1 compressed. Decoded size = `width × height × 4 bytes`.

**Impact (concrete):**
- 8000×6000 image → **192 MB** on a single `int[]`, from a ~5–15 MB JPEG.
- 12000×9000 (108 MP) → **~432 MB**.
- Portrait photos with EXIF orientation allocate a **second** full-res buffer during rotate
  (`:160-163`) → transient ~384 MB for the 8000×6000 case.
- **No max-dimension/megapixel guard** — a 30000×30000 input = 30000·30000·4 ≈ **3.6 GB**,
  instant OOM regardless of concurrency.

The scaling target (≤1920 px) is irrelevant: the full-res buffer is always allocated first,
and `convert` re-scales the source once per target size (800/1280/1920). `getScaledInstance`
(`:43`) is the deprecated, memory-heavy path and holds source + lazy-scaled + target buffers
simultaneously; the scaling `Graphics` at `:49` is never `dispose()`d (the rotate one at `:164`
is).

**Fix (highest leverage):** Use `ImageReader.getWidth/getHeight` to read dimensions cheaply,
compute a subsampling factor for the largest target, and decode via
`ImageReadParam.setSourceSubsampling(...)` so the decoded buffer is capped near target
resolution (1920² × 4 ≈ 15 MB). Add a configurable max-megapixel guard that rejects oversized
inputs before decode. Replace `getScaledInstance` with `Graphics2D.drawImage` +
`VALUE_INTERPOLATION_BILINEAR` (or imgscalr/Thumbnailator) and dispose every `Graphics2D`.
This makes the module O(target) instead of O(source) and moots F4's sub-issues.

### F5 — Converter response double-buffered (`bodyToMono<ByteArray>` then `readAllBytes`)
**Severity: High** · `modules/ConverterWebServiceCaller.kt:48-62`

The source is streamed to a temp file (good, `:24-31`), but the **converted** HTML/PDF result
is pulled fully into heap via `.bodyToMono<ByteArray>().block()` (`:48`), then uploaded with
`convertedCacheObject.size = -1` (`:56`) → S3 `readAllBytes()` makes a **second** full copy.

**Impact:** Per document/jupyter conversion, heap holds ~1× the artifact (capped at the 20 MB
codec limit — a >20 MB result now *fails* rather than streams), transiently ~2× during the
PUT. N parallel conversions ≈ N × up to ~40 MB.

**Fix:** Stream the response to a temp file (`bodyToFlux<DataBuffer>()` +
`DataBufferUtils.write(flux, path)`), then upload with `cacheObject.size = file.length()` so
the streaming branch is taken. Delete the temp file in `finally`. Removes both copies.

### F6 — Lumi asset proxy buffers whole H5P assets as `ByteArray`
**Severity: High (highest concurrency risk)** · `modules/h5p/lumi/LumiProxyController.kt:53,70`;
buffering in `modules/h5p/lumi/LumiProxyService.kt:88-90` (`.toEntity(responseType).block()`)

`getContentAssets` / `getH5PCoreAssets` proxy arbitrary H5P assets (JS bundles, images, media)
and return `ResponseEntity<ByteArray>` — the whole body on heap, on a synchronous MVC endpoint
hit directly by browsers, with no cache in front. H5P player pages fire many parallel asset
requests; H5P packages routinely embed video/large media.

**Impact:** Each request holds the full asset (≤20 MB codec cap; larger 500s). This is bounded
only by Tomcat's 200 threads, not by the job queue — a handful of users on media-heavy H5P
content → hundreds of MB of transient `ByteArray`s + GC pressure.

**Fix:** Relay the upstream body as `Flux<DataBuffer>` / `StreamingResponseBody` straight to
the servlet output stream (copy status + headers, then pipe). Never hold the whole asset. Where
the asset is already cached in S3, redirect / support Range instead of proxying.

### F7 — `@Cacheable` annotations are inert → repeated DB/crypto work per request
**Severity: High (perf/GC, not a leak)** · `RepositoryRegistrationStorageService.kt:27,47,53`,
`RepositoryRegistrationService.kt:128,142,153`, `MetadataService.kt:43,58`,
`LumiContentManagementService.kt:75`

**Verified:** there is no `@EnableCaching`, no `CacheManager` bean, and no
`spring-boot-starter-cache`/Caffeine dependency anywhere in the module. Spring installs the
caching proxy only when one of those exists — so all `@Cacheable`/`@CachePut`/`@CacheEvict`
above are **no-ops**. Every render request re-hits Mongo for the registration and **rebuilds
the X509/PKCS8 keys** (the "Cache miss … loading from database" log fires every time).

**Impact:** Per-request CPU + allocation churn (key parsing, Mongo round-trips) that the design
intended to amortize. Also a latent trap: adding `@EnableCaching` *without* a bounded provider
gives an **unbounded** `ConcurrentMapCache` (fine here since repo count is small, but no
eviction).

**Fix:** Decide intent. If caching is wanted, add `@EnableCaching` + a bounded Caffeine
`CacheManager` (max-size + TTL per cache, especially `registrations`/`repositoryKeys` keyed by
`repoId`). If not, remove the misleading annotations. Add an integration test asserting a
second call doesn't re-hit Mongo.

---

## 5. Medium & Low findings

### F8 — 1 GiB codec ceiling on the repo-registration/metadata WebClient
**Severity: Medium** · `edusharingRepo/services/RepositoryRegistrationService.kt:59`
(`maxInMemorySize(1024 * 1024 * 1024)`)

`getWebClient(url)` overrides the shared 20 MB codec limit to **1 GiB**. This applies to
*aggregating* decoders (`bodyToMono<ByteArray>`/`<String>`/JSON), **not** to
`bodyToFlux<DataBuffer>` — so it does not directly buffer the content download. The real
exposure is `createRegistration`'s `bodyToMono<String>()` (`:74`) on the metadata endpoint: a
hostile/misconfigured repo `/metadata` could be buffered up to 1 GiB into a `String` (UTF-16
→ up to ~2 GiB char data) instead of being rejected at the sane default. Metadata is a small
`Properties` XML; the 1 GiB ceiling is unjustified.

**Fix:** Remove the blanket override. Keep `/metadata` at a modest ceiling (a few MB) on a
dedicated builder; scope any genuinely-large in-memory need to the specific call.

### F9 — `FluxInputStream` lifecycle & scheduling (not whole-object buffering)
**Severity: Medium** · `utils/FluxInputStream.kt:14,23-42`

Contrary to first suspicion, this does **not** buffer the whole stream — it bridges
`Flux<DataBuffer>` to a blocking `InputStream` via a bounded 64 KB `PipedInputStream`/
`PipedOutputStream` (genuinely streaming). But: (1) `.publishOn(boundedElastic())` is placed
*after* `DataBufferUtils.write(...)`, so the blocking pipe write still runs on the
WebClient/Netty event-loop thread — a slow consumer that fills the 64 KB pipe **blocks an
event-loop thread**, stalling other reactive I/O. (2) There is no linkage from
`inStream.close()` back to cancelling the flux, so a client disconnect leaks the subscription
and its pooled (off-heap) Netty `DataBuffer`s until GC.

**Fix:** Move blocking work to the producer with `.subscribeOn(boundedElastic())`; capture the
`Disposable` and dispose it when the returned `InputStream` is closed (wrap
`PipedInputStream.close()`), so a disconnect tears down the flux and releases buffers.

### F10 — eduhtml zip: per-entry heap buffer + no zip-bomb / zip-slip caps
**Severity: Medium (OOM + security)** · `modules/eduhtml/EduHtmlConversionService.kt:52-68`
(entries with `size = -1` at `:58`)

The zip is streamed to a temp file and unzipped in two streaming `ZipInputStream` passes
(good — the archive is never fully in memory). But each entry is uploaded with `size = -1` →
S3 `readAllBytes()` buffers the whole *uncompressed* entry. There is **no per-entry cap, no
total-uncompressed cap, and no entry-count cap** — a zip-bomb entry OOMs the converter. Also
`substringAfter(zipRoot)` used as the S3 key (`:63`) is not sanitized against `../`
(zip-slip).

**Fix:** Stream each entry to S3 with `ZipEntry.getSize()` as the length when ≥0 (else spool to
a temp file first). Enforce per-entry + cumulative + count caps, throwing `ConversionException`
on breach. Sanitize entry names against path traversal before using them as keys.

### F11 — Admin delete-by-type / list-versions load full match sets into heap
**Severity: Medium** · `asset/AdminAssetController.kt:111-120,160-169`;
`storage/S3StorageService.kt:109-138`

`deleteAssetsByType` → `findAllByRepoIdAndType(...)` loads every matching `TrackingEntry` into
one `List`, then `deleteEntries` maps it to another full list before deletion. `listAssetVersions`
loads all versions then `.sortedByDescending{}.map{}` (3 in-memory copies). `removeObjects`
`flatMap`s every matching S3 key into one list. `deleteAllAssets` (`:171-186`) already does the
right thing — a `DELETE_BATCH_SIZE=1000` "drain-first-page" loop.

**Impact:** A type with ~500k entries × ~200 B × 2 lists ≈ 200 MB+ transient spike per call.

**Fix:** Apply the `deleteAllAssets` batching pattern to `deleteAssetsByType` and the hash-less
`deleteAsset` branch; push the version sort into Mongo (`Sort.by(DESC, "lastAccessed")`) and
paginate; consume the S3 key paginator lazily (flush every 1000).

### F12 — GitHub file download buffered as `ByteArray`
**Severity: Medium** · `modules/binder/git/GitHubService.kt:31-38`
(`.bodyToMono(ByteArray).map { it.inputStream() }.block()`)

`.ipynb` files (with embedded base64 plot/image outputs can be large) are pulled whole into a
`ByteArray`, then handed to `ConverterWebServiceCaller` which copies to a temp file — the file
lives on heap once as the array and again as the temp-file source. Bounded by the inherited
20 MB codec cap (a larger notebook fails). Minor: a fresh `ObjectMapper()` is allocated per
call (`:59`).

**Fix:** Change `GitService.getFile` to stream to a temp `Path` (`bodyToFlux<DataBuffer>` +
`DataBufferUtils.write`) and pass that file downstream. Inject the shared Jackson mapper.

### F13 — ImageIO disk cache enabled (tmpdir churn)
**Severity: Medium** · no `ImageIO.setUseCache(false)` anywhere; `ImageConversionService.kt:52,75`

JVM default `useCache=true` lets ImageIO spill to disk-cache temp files in `java.io.tmpdir`
(often a memory-backed `tmpfs` in containers), adding hidden memory/FS pressure + fsync
latency per conversion. **Fix:** call `ImageIO.setUseCache(false)` at startup (the code is
already fully in-memory).

### Low-severity notes
- **F14 — Image output double-copy** (`ImageConversionService.kt:50-64`): `ByteArrayOutputStream`
  → `.toByteArray()` copies the encoded thumbnail; small (≤ few MB) but needless per sub-job.
- **F15 — Metadata `bodyToMono<String>` + re-encode** (`RepositoryRegistrationService.kt:74-86`):
  two in-memory copies (UTF-16 String + UTF-8 bytes); admin-only, low frequency; only dangerous
  combined with F8's 1 GiB ceiling.
- **F16 — S3 stream close discipline** (`asset/AssetService.kt`, `S3StorageService.kt:140-159`):
  `ResponseInputStream` holds an open S3/HTTP connection; ensure the controller closes it on all
  paths (incl. client abort) and bound the Apache HTTP client pool so leaks can't exhaust FDs.
- **F17 — `MongoTemplate` default `UNACKNOWLEDGED`** for non-job writes incl. `TrackingEntry`
  (`config/MongoConfig.kt:24-35`): durability, not memory; lost tracking entries skew cache-cleaner
  size accounting.
- **F18 — Repeated `new ObjectMapper()`** in DDB/Lumi/GitHub/Moodle callers: allocation/CPU
  churn, inconsistent with the shared Jackson-3 bean convention; inject the bean.
- **F19 — `findAll()` on registrations** (`CorsSyncService.kt:37,112`, every 5 min) and per-put
  `getDirectorySize` LIST (`S3StorageService.kt:462`): bounded by repo count / a latency+API-cost
  concern, not a heap leak.
- **F20 — No JVM heap or thread-pool tuning**: no `server.tomcat.threads.max` override (Boot
  default **200**), no `-Xmx`/`-XX:MaxRAMPercentage` in code/properties. 200 request threads each
  carry per-request buffers and run session deserialization (F2) on-thread — set explicit heap %
  and consider lowering max-threads to realistic concurrency.

---

## 6. What is already done well

- **Streaming download primitive.** `ContentTransferService.getAsInputStream` uses
  `bodyToFlux<DataBuffer>()` (the non-aggregating body type) bridged by `FluxInputStream`'s
  bounded 64 KB pipe — backpressured, constant-memory (value is lost downstream, see F1/F9).
- **Serve path is fully streaming.** `AssetController` returns `InputStreamResource` over the
  S3 `ResponseInputStream` with an explicit `Content-Length` (from a cheap S3 HEAD), copied out
  in ~8 KB chunks. A 1 GB video streams to any number of clients at O(8 KB) heap each.
- **HTTP Range offloaded to S3.** Ranges become `GetObjectRequest.range("bytes=start-end")`
  with a 2 MB default window (`AssetService.kt:22`) — no in-memory slicing.
- **Known-length uploads stream.** The `size >= 0` branch uses
  `RequestBody.fromInputStream(stream, size)` — the fix for F1/F3/F5/F9 is just to make that the
  branch always taken.
- **Source content streamed to temp files** before conversion, with `use{}`/`finally` cleanup:
  `ConverterWebServiceCaller`, `H5pUploadService`, `OnyxUploadService`, `EduHtmlConversionService`,
  `AvFileHelper.fetchOriginalTempFile`. Multipart uploads use `FileSystemResource` (streamed from
  disk).
- **ffmpeg/jave operate on disk files**, not heap; the progress listener stores only an int.
- **RabbitMQ messages carry only ids** (not payloads), with `prefetch=1`/`concurrentConsumers=1`
  — negligible broker/unacked memory.
- **Mongo documents stay small.** `RenderingJob`/`SubJob` hold metadata only (results in S3);
  `subJobs` is `@DocumentReference(lazy=true)`; 8-day TTL index.
- **Cache cleaner streams.** `TrackingService.TrackingIterator` pages 100 at a time and
  `CacheCleaner` stops via `asSequence().takeUntil(...)` once the freed target is met.
- **Admin listings aggregate server-side** in Mongo (`$group`/`$skip`/`$limit`) and job listing
  is paginated. `deleteAllAssets` batches correctly (the pattern F11 should adopt).
- **Bounded scheduler pool** (poolSize=2) and a central WebClient codec cap (20 MB) with
  connect/response timeouts.

---

## 7. Prioritized remediation roadmap

| # | Finding | Effort | Payoff |
|---|---|---|---|
| 1 | **F1** — propagate `Content-Length`; make streaming the only upload branch (or `fromFile`) | S–M | Removes the Critical download-path buffer + moots F3/F5/F10 double-copies |
| 2 | **F3** — AV `readBytes()` → streamed file upload | XS | One line; removes largest single heap array |
| 3 | **F4** — image subsampled decode + max-megapixel guard | M | Module O(target) not O(source); closes 3.6 GB OOM |
| 4 | **F2** — bound session node/permission lists; drop full `Node`; tighten typing | M | Closes unbounded Redis + on-thread deser growth |
| 5 | **F6** — stream the Lumi asset proxy | M | Closes the highest user-facing concurrency risk |
| 6 | **F7** — resolve `@Cacheable` (enable+bound Caffeine, or remove) | S | Removes per-request DB/crypto churn |
| 7 | **F10** — eduhtml per-entry stream + zip-bomb/zip-slip caps | M | OOM + path-traversal safety |
| 8 | **F8** — remove 1 GiB codec ceiling | XS | Closes metadata-endpoint OOM vector |
| 9 | **F11** — batch admin delete-by-type / paginate versions | S | Bounds admin spikes |
| 10 | **F9, F12, F13, F5** — FluxInputStream lifecycle, GitHub stream, ImageIO cache, converter temp-file | S–M | Robustness + steady-state footprint |
| 11 | **F20** — set explicit `-XX:MaxRAMPercentage` and right-size `max-threads` | XS | Safety net until 1–5 land |

**One structural change unlocks most of the list:** make `S3StorageService` never call
`readAllBytes()` — add a `File`-based `putObject` overload (`RequestBody.fromFile`) and set
`cacheObject.size` from the temp-file length at every call site that currently leaves it `-1`.
That alone neutralizes F1, F3, F5, and F10's second heap copy.

---

## Appendix — verified anchor references

- `service/src/main/kotlin/org/edu_sharing/rendering/core/dto/CacheObject.kt:6` — `size = -1`
- `service/src/main/kotlin/org/edu_sharing/rendering/storage/S3StorageService.kt:197-205, 451-459` — `readAllBytes()` fallback
- `service/src/main/kotlin/org/edu_sharing/rendering/modules/av/AvFileHelper.kt:37` — `readBytes()`
- `service/src/main/kotlin/org/edu_sharing/rendering/modules/image/ImageConversionService.kt:43-49, 74-75, 110-166`
- `service/src/main/kotlin/org/edu_sharing/rendering/modules/ConverterWebServiceCaller.kt:48,56,60`
- `service/src/main/kotlin/org/edu_sharing/rendering/modules/h5p/lumi/LumiProxyController.kt:53,70`
- `service/src/main/kotlin/org/edu_sharing/rendering/security/NodeSessionContextRepository.kt:25-31`
- `service/src/main/kotlin/org/edu_sharing/rendering/config/SessionConfig.kt:39-64`
- `service/src/main/kotlin/org/edu_sharing/rendering/edusharingRepo/services/RepositoryRegistrationService.kt:59,74`
- `service/src/main/resources/application.properties:157` — `spring.http.codecs.max-in-memory-size=20MB`
- `service/src/main/kotlin/org/edu_sharing/rendering/utils/FluxInputStream.kt:14,23-42`
- `service/src/main/kotlin/org/edu_sharing/rendering/modules/eduhtml/EduHtmlConversionService.kt:52-68`
- `service/src/main/kotlin/org/edu_sharing/rendering/renderingJob/queue/QueueConfig.kt:22-23`