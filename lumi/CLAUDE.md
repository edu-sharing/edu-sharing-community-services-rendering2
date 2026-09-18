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
| `src/traceContext.ts` | Adopts the incoming b3/W3C trace id and prefixes every `debug` log line with it. Binds `req.emit`/`res.emit` into the `AsyncLocalStorage` context — **do not remove**: without it every request with a body (the package import above all) logs untraced, because body parsers resume the chain from stream events that fire in the socket's async context. |
| `src/eduSharingPlayer.ts` | Custom H5P player/renderer. Also injects the core styles the bundled `playerAssetList.json` omits, and seeds a preloaded empty `contentUserData` so H5P core skips the `contentUserData` AJAX call that h5p-express answers with 403 while the feature is off (`contentUserStateSaveInterval: false`). |
| `src/EduSharingModel.ts` | Mongo node↔content mapping model. |
| `src/User.ts` | Dummy user for H5P context. |
| `src/h5p.settings.ts` | H5P version constants. |
| `dist/` | `tsc` output (built, not committed source). |
| `h5p/` | Downloaded H5P core + editor (via `download-core.sh`). |

## Routes (`router.ts`)
- `GET  /edusharing/nodeid/:nodeId` — content id for an edu-sharing node id
- `GET  /edusharing/contentid/:contentId` — node id for a content id
- `POST /edusharing` — upload an H5P package, map it to a node id
- `GET  /:contentId` — render the H5P player (HTML)
- `DELETE /edusharing/:nodeHash` — delete content + mapping
- `GET  /edusharing/buckets` — S3 bucket config
- `GET  /edusharing/ping` — health check
- `GET  /package-libraries/:packageId/:uberName/:file` — library files of one package (only mounted when the per-package cache is on)

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
  `TEMPORARY_AWS_S3_BUCKET`, `LIBRARY_AWS_S3_BUCKET`.
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
- **Server**: `PORT` (default 3000), `BASE_URL=/public/h5p`, `CACHE=in-memory`.
- **Per-package library cache**: `H5P_LIBRARY_CACHE` (`global` | `package`, default `global`),
  `H5P_LIBRARY_CACHE_DIR` (default `/application/library-cache`), `H5P_LIBRARY_CACHE_QUOTA`
  (byte count or `10GB`-style size, binary units via `parseDataSize`; empty/`0` = no limit —
  an unparsable value logs a warning and means *no limit*, so check the startup log).

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
`H5P.MathDisplay` now only applies to a package that actually ships it.

### Quota

`H5P_LIBRARY_CACHE_QUOTA` caps the cache. On reaching it an import is **rejected with HTTP 507**
(`CacheQuotaExceededError`); `.retrieve()` on the service's WebClient turns that into a failed import
sub-job. Nothing is evicted, and that is deliberate — this cache is *not* regenerable:

- `H5pLookupReceiver` decides "already imported" from the Mongo `edusharing` mapping alone, so a
  package whose libraries were deleted is never re-imported;
- `H5PPlayer.getMetadataRecursive` *silently ignores* libraries it cannot find.

On their own those two would mean evicting a package's libraries yields a blank player and no error —
which is why eviction must delete the content **and** its mapping so the lookup misses and the package
re-imports, i.e. what `DELETE /edusharing/:nodeHash` does, and what the `libraryScope` repair above does
for libraries that vanish by accident.

Accounting: **allocated blocks** (`stat.blocks * 512`), not file sizes. A package is ~1800 mostly
sub-block files, so block rounding adds ~45% on a 4K filesystem — sizing by file size would let the
volume fill long before the quota was reached. This matches `du` on a PVC and `df` on a memory-backed
volume (`emptyDir: {medium: Memory}` / tmpfs), which is what its `sizeLimit` enforces and what counts
against the pod's memory. The size is walked once at startup and then maintained incrementally by
`promote`/`remove` (a re-import of the same package only counts the difference). `GET /edusharing/buckets` reports
`libraryCache: {usedBytes, quota, evictable: false}` when the feature is on, and `POST /edusharing`
returns `libraryBytes` for the package it just imported.

`evictable: false` refers to the libraries *alone*. The rendering service's **CacheCleaner** does
breathe on this volume — over the upper threshold it deletes least-recently-accessed H5P content down
to the lower one, exactly as it does for a bucket. That is safe because it deletes through
`DELETE /edusharing/:nodeHash`, which removes the content, the libraries **and** the node mapping
together, so the next request re-imports the package. The service sizes those candidates by the
`libraryBytes` it recorded at import (`TrackingEntry.librarySize`), and skips entries with none —
content from before the feature, which would free nothing. The 507 stays as a backstop for bursts
between cleaner runs.

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
