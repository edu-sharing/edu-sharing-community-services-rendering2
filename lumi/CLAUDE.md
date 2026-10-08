# lumi — CLAUDE.md

Module-local guidance. This is a **Node.js / TypeScript** module, not Spring/JVM — the
Kotlin/Jackson conventions in the repo-root [`../CLAUDE.md`](../CLAUDE.md) do not apply here.

## What this module is
An Express (4.x) server on **Node 20.9** that provides an H5P editor/player for edu-sharing,
backed by **MongoDB + S3/MinIO** via the `@lumieducation/h5p-*` libraries. It is built into a
zip artifact by Maven and the main `service` reaches it at `app.lumi.host` (default
`http://localhost:3000`); its public base path is `/public/h5p`.

## Layout
| Path | Responsibility |
|---|---|
| `src/index.ts` | Entry point; Express init, graceful shutdown (SIGINT/SIGTERM). |
| `src/router.ts` | edu-sharing-specific routes (below). |
| `src/createH5PEditor.ts` | Builds the H5P editor with S3 + Mongo storage (incl. the S3 connection pool); returns it as `H5PDeps` together with the shared storages/options. |
| `src/packageLibraries/` | Per-package isolated H5P library caches (below). Self-contained; off by default. |
| `src/s3Streams.ts` | Binds S3 body streams to the request; without it aborted downloads leak pool sockets. |
| `src/importQueue.ts` | Runs package imports one at a time, with a deadline (below). Replaces the former `serializeUpload` promise chain in `router.ts`. |
| `src/importScope.ts` | `AsyncLocalStorage` marker for "inside a package import"; lets the shared S3 client throttle only the import's requests. |
| `src/s3Throttle.ts` | S3 client middleware: caps an import's concurrent S3 requests and reports every settled request to the pool watchdog. |
| `src/s3Pool.ts` | Watchdog for the S3 connection pool (below). |
| `src/metrics.ts` | Prometheus gauges for the import queue and the S3 pool. |
| `src/traceContext.ts` | Adopts the incoming b3/W3C trace id and prefixes every `debug` log line with it. Binds `req.emit`/`res.emit` into the `AsyncLocalStorage` context — **do not remove**: without it every request with a body (the package import above all) logs untraced, because body parsers resume the chain from stream events that fire in the socket's async context. |
| `src/eduSharingPlayer.ts` | Custom H5P player/renderer. Also injects the core styles the bundled `playerAssetList.json` omits, and seeds a preloaded empty `contentUserData` so H5P core skips the `contentUserData` AJAX call that h5p-express answers with 403 while the feature is off (`contentUserStateSaveInterval: false`). |
| `src/mathDisplay.ts` | LaTeX for every H5P page: serves MathJax 4 (npm `mathjax` + its font) and builds the `<head>` snippet `eduSharingPlayer` injects (config/observer ported from the upstream `H5P.MathDisplay` addon). Only injected when the content parameters contain LaTeX and the package does not ship `H5P.MathDisplay` itself. |
| `src/EduSharingModel.ts` | Mongo node↔content mapping model. |
| `src/User.ts` | Dummy user for H5P context. |
| `src/h5p.settings.ts` | H5P version constants. |
| `dist/` | `tsc` output (built, not committed source). |
| `h5p/` | Downloaded H5P core + editor (via `download-core.sh`). |

## Routes (`router.ts`)
- `GET  /edusharing/nodeid/:nodeId` — content id for an edu-sharing node id
- `GET  /edusharing/contentid/:contentId` — node id for a content id
- `POST /edusharing` — upload an H5P package, map it to a node id. Failures carry a meaningful status instead of a blanket 500 - see *Why an import is rejected* below
- `GET  /:contentId` — render the H5P player (HTML)
- `DELETE /edusharing/:nodeHash` — delete content + mapping
- `GET  /edusharing/buckets` — S3 bucket config and quotas; `libraryCache` (size + soft quota) only once the cache size is known
- `GET  /edusharing/ping` — liveness only: answers 200 whenever the process does. Unchanged on purpose, because the Helm chart's probes restart the pod on failure.
- `GET  /edusharing/health` — import queue + S3 pool state as JSON; **503** while an import is past its deadline or the S3 pool is stuck. Not wired to any probe; hooking it up is an operator's decision.
- `GET  /package-libraries/:packageId/:uberName/:file` — library files of one package (only mounted when the per-package cache is on)
- `GET  /mathjax/<mathjax>-<font>/...` — MathJax and its font from `node_modules` (`mathDisplay.ts`, mounted in `index.ts`); both versions are in the path, so it is cached as immutable

## npm scripts (`package.json`)
- `npm run setup` → `rm -rf ./h5p && ./download-core.sh 1.28.0 1.25` — downloads H5P core
  (1.28.0) and editor (1.25); **versions are the script args**, change them there.
- `npm run build` → setup + `rm -rf ./dist && tsc`.
- `npm run start` → `ts-node src/index.ts` with debug logging.

## Config (`.env`)
- **MongoDB**: `MONGODB_URL`, `MONGODB_DB`, `MONGODB_USER`, `MONGODB_PASSWORD`, plus
  `CONTENT_MONGO_COLLECTION`, `LIBRARY_MONGO_COLLECTION`, `EDUSHARING_MONGO_COLLECTION`.
- **S3 / MinIO**: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_ENDPOINT`,
  `AWS_S3_REGION`, `AWS_S3_TRUST_ALL_CERTIFICATES`; buckets `CONTENT_AWS_S3_BUCKET`,
  `TEMPORARY_AWS_S3_BUCKET`, `LIBRARY_AWS_S3_BUCKET`. `TEMPORARY_AWS_S3_BUCKET_EXPIRATION_DAYS`
  (default 1) sets the temp bucket's lifecycle expiration; applied on every start by
  `ensureTempBucketExpiration`. The bucket rule is the only thing that deletes temp files
  (h5p-mongos3's `listFiles()` is empty, so `cleanUp()` is a no-op), and `temporaryFileLifetime`
  has no effect on S3 besides the lib's own, mis-converting lifecycle helper that we do not call.
- **S3 connection pool** (`createH5PEditor.ts`): `AWS_S3_MAX_SOCKETS` (default 256 — the AWS SDK
  default of 50 is a hard ceiling on parallel file serving, since every library/content file of every
  H5P page is its own S3 `GetObject`). **Both S3 timeouts default to 0 = off, and should stay off**:
  - `AWS_S3_CONNECTION_TIMEOUT_MS` is not a TCP-connect timeout — `@smithy/node-http-handler` starts
    the timer at request creation and clears it only once a socket is connected, so it also bounds the
    wait for a free pool socket. A package import queues >1500 uploads (unbounded `Promise.all` over
    every file of every library), so a short value kills queued uploads, and `LibraryManager` reacts by
    deleting the libraries it was copying — the whole import then fails (`s3-upload-error`).
  - `AWS_S3_REQUEST_TIMEOUT_MS` is a *socket inactivity* timeout; backpressure from a slow client
    stalls reads on the S3 socket, so enabling it can truncate large downloads.
- **Package size limits**: `H5P_MAX_TOTAL_SIZE` (max *unpacked* size of a package, default 1000 MB in `config.json`, 4GB in compose/Helm) and `H5P_MAX_FILE_SIZE` (max single file, 1000 MB / 2GB), plain byte count or `2GB`-style size; an unparsable value logs a warning and keeps the default. Larger packages fail with `package-validation-failed:total-size-too-large`. The import works on the spooled **file** (`uploadPackage(path)`), so neither limit is bound by memory and a package may exceed 2 GiB: a 2.3 GB package imports with ~0.7 GB RSS. Handing the library a `Buffer` instead would fail above 2 GiB (`fs.readFile`) and keep the package in RAM plus a second copy in `/tmp` - do not go back to that. Time is the remaining limit: the rendering service's per-repository `timeout` credential (default 300 s) and `H5P_IMPORT_TIMEOUT_MS` have to cover an import of that size on your storage.
- **Server**: `PORT` (default 3000), `BASE_URL=/public/h5p`, `CACHE=in-memory`.
- **Per-package library cache**: `H5P_LIBRARY_CACHE` (`global` | `package`, default `global`),
  `H5P_LIBRARY_CACHE_DIR` (default `/application/library-cache`), `H5P_LIBRARY_CACHE_QUOTA`
  (**soft** limit, see *Quota*; byte count or `10GB`-style size, binary units via `parseDataSize`;
  empty/`0` = no limit — an unparsable value logs a warning and means *no limit*, so check the startup
  log), `H5P_LIBRARY_CACHE_RESCAN_HOURS` (default 24, `0` = measure at every start only; see *Startup*).

## Package imports (`importQueue.ts`, `s3Pool.ts`, `s3Throttle.ts`)

**What went wrong before.** Imports were chained on one promise with no bound. An S3 request that never
returned (a socket the pool never got back; there are no S3 timeouts, see above) froze that chain for
good: every later import logged `Starting H5P package upload` and then nothing, the rendering service
timed out one retry after the other (roughly every 300 s), and only a restart of lumi helped. The only
trace was `@smithy/node-http-handler:WARN - socket usage at capacity` - and the SDK emits that only once
**2 × `AWS_S3_MAX_SOCKETS` requests are queued**, i.e. long after the outage began.

What is in place now:

- **Spooled to disk.** `POST /edusharing` uses `multer.diskStorage` (`H5P_UPLOAD_DIR`, default the OS temp
  dir); the import reads the file in place (never into a Buffer) and it is always removed afterwards. A
  backlog of waiting uploads no longer holds a package each in RAM. **It does take disk instead**, and the
  library unpacks every package into `/tmp` as well (package size + unpacked size per import), so give
  `/tmp` room: Helm `persistence.data.temp` (opt-in PVC, like the service chart); compose uses the
  container layer.
- **Deadline** `H5P_IMPORT_TIMEOUT_MS` (default 900000 = 15 min, `0` = off; keep it above the rendering service's H5P `timeout` credential, default 300 s). Past it the request gets **504**, the
  S3 pool's sockets are destroyed (what a stuck import waits on is almost always one dead socket - the
  pool is nowhere near full then, so the watchdog's stall check would never see it) and, **in `package`
  mode only**, the queue moves on. In `global` mode imports share one library storage and corrupt it when
  they overlap, so the queue keeps waiting until the task settles; destroying the sockets is what makes
  that wait finite.
- **Import throttle** `H5P_IMPORT_S3_CONCURRENCY` (default 64): an import no longer fans out over the whole
  pool (one package is >1500 requests). Only requests inside the import scope wait; player requests never
  do. They queue in lumi *before* the SDK creates the request, so the SDK's connection timer (which also
  covers the wait for a free socket, see above) is not running while they wait.
- **Pool watchdog** `AWS_S3_POOL_STALL_MS` (default 120000, `0` = off): pool full (per origin, as the SDK
  judges it), requests waiting and no request settled for that long -> destroy all sockets. In-flight
  requests fail and are retried by the SDK. Judged per agent: the HTTP and HTTPS agents each have their own
  limit and only one is used, so summing them would make a full pool look half empty.
- **Observability**: `/edusharing/health` and the gauges `lumi_import_waiting`, `lumi_import_running_seconds`,
  `lumi_import_deadline_exceeded`, `lumi_s3_pool_in_use`, `lumi_s3_pool_queued`,
  `lumi_s3_pool_stalled_seconds`, `lumi_s3_pool_recoveries`. Alert on `lumi_import_running_seconds` and
  `lumi_s3_pool_stalled_seconds` instead of waiting for the SDK warning. The library cache has its own, see
  *Quota*.

An import that blew its deadline is abandoned, not cancelled: it may still finish in the background (its
staging directory is then published without a mapping and counts towards the cache size until the
package is imported again). It cannot be stopped from outside `@lumieducation/h5p-server`.

## Why an import is rejected

| Status | Cause | Configurable |
|---|---|---|
| **400** | not a zip (`unable-to-unzip`), or a status the library chose itself: an S3 key longer than 1024 characters (`mongo-s3-content-storage:filename-too-long`), a filename with a relative or absolute path (`illegal-filename`) | no |
| **413** | the package exceeds a size limit: `total-size-too-large` (`H5P_MAX_TOTAL_SIZE`, unpacked) or `file-size-too-large` (`H5P_MAX_FILE_SIZE`, a single file) | yes |
| **422** | not a valid H5P package: a file with a disallowed extension (`not-in-whitelist`), a broken `h5p.json`, an unsupported API version, ... | partly (see below) |
| **503** | a library installation held its lock too long (`INSTALL_LIBRARY_LOCK_*_MS`, 60 s / 120 s): temporary, worth another attempt | yes |
| **504** | the import did not finish within `H5P_IMPORT_TIMEOUT_MS` | yes |
| **507** | the disk is full (`ENOSPC`) - the cache volume or `/tmp` | no |
| 500 | anything else | |

`importErrorStatus` (`src/importErrors.ts`) does the mapping. Before it every failure was a 500 - the
validator's own status (400) was ignored - so the rendering service could not tell a bad package, which no
retry will fix, from a broken lumi. There is **no** status for the library cache being full: that quota
is soft (below).

**Finding: the file extension whitelist is fixed, and shorter than one would expect.** The validator only
accepts these extensions inside `content/`: `json png jpg jpeg gif bmp tif tiff eot ttf woff woff2 otf webm
mp4 ogg mp3 m4a wav txt pdf rtf doc docx xls xlsx ppt pptx odt ods odp xml csv diff patch swf md textile vtt
webvtt gltf glb` - and for libraries only `js css svg`. **`svg`, `webp`, `mov` and `flac` are not in the
content list**, so a package with such a file is rejected as a whole (422, `not-in-whitelist`; the log line
`checking allowed file extension: … - allowed extensions: …` shows the list in force). The lists are the defaults
of `H5PConfig` (`contentWhitelist`, `libraryWhitelist`). They can be **extended** (not replaced) with
`H5P_CONTENT_WHITELIST_EXTRA` / `H5P_LIBRARY_WHITELIST_EXTRA` (space or comma separated, e.g. `svg webp`; see
`whitelist.ts`), read in `index.ts` next to the size limits; the effective lists are logged at startup
(`Allowed content extensions: …`). Additive on purpose: a typo can never reject packages that import today.

Other hard limits that are not ours to configure: a MongoDB document is at most 16 MiB and `content.json` is
stored as one (`mongo-add-update-error`, 500; derived from the code, not tried). `AWS_S3_MAX_FILE_LENGTH` is
not a limit that rejects anything: it is the length `generalizedSanitizeFilename` rewrites names to (it also
replaces characters outside `A-Za-z0-9-._!()@/`); only a key that is still over 1024 characters is rejected.
`CONTENT_AWS_S3_BUCKET_QUOTA` is **not** enforced by lumi at all: it is only reported on `/edusharing/buckets`.

## Tests
`npm test` (`node --test` with ts-node, `test/*.test.ts`) covers the queue, the watchdog, the throttle and
the size parsing. There is no integration test; the paths with real S3 were checked by hand against the
compose stack with a TCP proxy in front of RustFS that swallows traffic on demand.

## Per-package library cache (`src/packageLibraries/`)

Off by default. With `H5P_LIBRARY_CACHE=package`, each imported H5P package gets its **own** library
store holding exactly the versions (major, minor **and patch**) that package shipped, and only those
are served when it is played.

Why it cannot be done with the stock storage: `@lumieducation/h5p-server` keys libraries by the
ubername `machineName-major.minor` **only** — the patch lives inside the metadata, and upstream
states plainly that "there can only be one installed patch version of a library at a time". So in a
shared store `LibraryManager.installFromDirectory` either **overwrites** the library for every other
package (package ships a newer patch) or **silently skips** the package's own version (equal/older
patch, `type: 'none'`). There is no per-content scoping anywhere upstream.

The whole feature is built from public extension points — **no fork or patch of any
`@lumieducation/*` package**:

| Piece | How |
|---|---|
| The cache itself | One stock `fsImplementations.FileLibraryStorage` per package directory. Isolation falls out of the storage being empty at import: every library installs as `new`, at its exact patch. Also gives us its filename validation, which the H5P HTTP layer deliberately omits. |
| Import | A second `H5PEditor` mirroring `H5PDeps`, but with the package's storage. Installs into `.staging/<uuid>`, then `fs.rename`s to `<contentId>` once `saveOrUpdateContent` has produced the id (it only exists *after* the libraries must already be installed). |
| Playout | A per-request `H5PPlayer` (its constructor does no I/O) with `PackageUrlGenerator`. |
| Static asset URLs | `PackageUrlGenerator` overrides `UrlGenerator.libraryFile`. **`libraryFile` is an arrow _property_, not a prototype method** — a subclass method of that name is shadowed by the base constructor and never runs; it must be replaced on the instance. |
| Runtime asset URLs | `IIntegration.urlLibraries`, passed as `integrationObjectDefaults`. H5P core's `H5P.getLibraryPath()` uses it when set, else `${url}/libraries` — without it, content types that build their own URLs escape the scope. |
| Serving files | Our own route; the stock `/libraries/:uberName/*` always reads the **editor's** storage. It stays enabled so content imported before the feature still plays. |

**Fallback**: a contentId with no directory under the cache root renders through the global player and
the stock route. That is how pre-existing content keeps working — but it is only correct for content
the global storage actually holds, so the mapping records **which** it is: `EduSharingModel.libraryScope`
is `'package'` for an import made in per-package mode, absent otherwise.

**Losing the volume is therefore detected, not silent.** Without that marker a wiped cache would give a
blank page and HTTP 200 — the lookup treats "a mapping exists" as "already imported", and the player
silently ignores libraries it cannot find. With it:

- `GET /edusharing/nodeid/:nodeId` — a `'package'` mapping whose cache directory is gone is **discarded**
  (content, libraries and mapping, as `DELETE` does) and answered **404**, so the service's import stage
  re-imports the package. Self-healing, and no orphaned content left behind.
- `GET /:contentId` — the same case answers **404** rather than rendering an empty page.
- A `'package'` mapping while lumi has been switched back to `global` counts as missing too: the global
  store never received those libraries either.

The cache directory is still authoritative and needs its volume (the chart creates it automatically in
`package` mode; a named volume in compose).

**Behaviour change to be aware of**: `listAddons()` scans *installed* libraries, so an addon such as
`H5P.MathDisplay` now only applies to a package that actually ships it. LaTeX does not depend on that:
`mathDisplay.ts` injects lumi's own MathJax into every page with formulas that lacks the addon.

### Quota

`H5P_LIBRARY_CACHE_QUOTA` is a **soft limit**, like the quota of the content bucket: lumi reports it
(`GET /edusharing/buckets` -> `libraryCache: {usedBytes, quota, evictable: false}`) and never fails an import
because of it. An import over the quota simply succeeds. It used to be a hard limit (HTTP 507 on reaching it),
which meant the exact size had to be known before every import - and therefore measured at every start.

What happens when the cache is over the quota is the rendering service's job: its **CacheCleaner** polls the
size and, over the upper threshold, deletes least-recently-used H5P content down to the lower one - exactly as
for a bucket. That is safe although the library cache is *not* regenerable on its own:

- `H5pLookupReceiver` decides "already imported" from the Mongo `edusharing` mapping alone, so a package whose
  libraries were deleted would never be re-imported;
- `H5PPlayer.getMetadataRecursive` *silently ignores* libraries it cannot find (a blank player, no error).

The CacheCleaner avoids both by deleting through `DELETE /edusharing/:nodeHash`, which removes the content, the
libraries **and** the node mapping together, so the next request imports the package again (the `libraryScope`
repair above does the same for libraries that vanish by accident). It sizes its candidates by the `libraryBytes`
it recorded at import (`TrackingEntry.librarySize`) and skips entries with none - content from before the
feature, which would free nothing.

**Exceeding it is logged and exported, because nothing else will tell anyone.** Every import and every
measurement that finds the cache over the quota logs `WARN Library cache is over its soft quota: X of Y bytes (n %)`
(and `INFO ... back under ...` once when it falls back), and these gauges exist (only in `package` mode):
`lumi_library_cache_used_bytes`, `lumi_library_cache_quota_bytes` (0 = none), `lumi_library_cache_over_quota` (1 while
over), `lumi_library_cache_size_known` (0 until the first measurement ended), `lumi_library_cache_measuring` and
`lumi_library_cache_last_measured_timestamp_seconds` (alert when it gets old: the daily rescan is not running).
`/edusharing/health` shows the same as `libraryCache.overQuota`. The rendering service exports the quotas of its own
scopes - buckets, repositories and this cache as the cleaner sees it - the same way (`rendering_storage_*`, see its
guide). Alarm on `lumi_library_cache_over_quota == 1` for longer than a few cleaner intervals.

Because nothing is rejected, **the volume is the only hard limit**: keep the quota well below its size, and size
the cleaner's schedule and thresholds so it keeps ahead of the imports. A full volume fails imports with 507
(`ENOSPC`).

Accounting: **allocated blocks** (`stat.blocks * 512`), not file sizes. A package is ~1800 mostly sub-block
files, so block rounding adds ~45% on a 4K filesystem. This matches `du` on a PVC and `df` on a memory-backed
volume (`emptyDir: {medium: Memory}` / tmpfs), which is what its `sizeLimit` enforces and what counts against the
pod's memory. The size is maintained incrementally by `promote`/`remove` (a re-import of the same package only
adds the difference); `POST /edusharing` returns `libraryBytes` for the package it just imported.

**Startup.** Measuring means a `stat` of every file: ~3.5 min for 23 GB in production, ~13 s per 200k files
locally. It used to run before the server started listening, so every restart left lumi unreachable - lookups
and players included. Since the quota is soft, nothing depends on the exact figure any more and nothing waits
for it:

- `FsPackageLibraryStore.create` returns as soon as the root is ready (listening after ~2 s).
- The size of the last run is remembered in `<cache dir>/.usage.json` (size and `measuredAt`; written 5 s after a
  change and on SIGTERM via `flush()`) and taken over immediately.
- The cache is only **measured again** when that measurement is older than `H5P_LIBRARY_CACHE_RESCAN_HOURS`
  (default 24): at start if it is, and then every that many hours while running. Younger -> no walk at all. The
  rescan corrects what incremental accounting cannot see (files removed outside lumi, a crash between two writes);
  changes during a walk are added on top of its result. `0` = measure at every start, never while running.
- `GET /edusharing/buckets` leaves `libraryCache` out while the size is unknown - a first start, or a start
  after a crash before any walk finished - so the CacheCleaner skips the cache instead of seeing a made-up 0.
  `GET /edusharing/health` shows `libraryCache.{known,reconciling}`; informational, never `degraded`.
- **The walk must stay depth first with a bounded fan-out** (`directorySize`, chunks of 16 entries per
  directory). The first version created a task for every entry of a directory up front, so it ran breadth first
  and held one pending task per file in memory: with ~5 million files in production that exhausted the 4 GB heap
  (`FATAL ERROR: Reached heap limit`) minutes after the start. Measured on 2 million files with the heap capped at
  1 GB: the old walk crashed (and needed over 10 minutes with 8 GB), the bounded one finishes in 12 s at ~250 MB.
  A crash before a walk ends writes no `.usage.json`, so the next start walks again.
- `.usage.json` and its `.tmp` are excluded from the measurement and, like `.staging`, can never be addressed as
  a package id.

## Build & packaging (Maven)
`pom.xml` uses `frontend-maven-plugin` to install Node 20.9 / npm and run `npm run build`
(`process-sources` → install, `compile` → build), then `maven-assembly-plugin` (`bin.xml`)
packages `dist/`, `h5p/`, `node_modules/`, and `config.json` into
`...-lumi-<version>-bin.zip`.

This module builds with the system **`mvn`** (not the `./service/mvnw` wrapper):

```bash
mvn -Pdev -pl lumi clean package   # full Maven build (downloads Node + H5P core)
# inside lumi/ for local dev:
npm run build && npm run start
```
