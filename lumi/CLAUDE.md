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
| `src/createH5PEditor.ts` | Builds the H5P editor with S3 + Mongo storage (incl. the S3 connection pool). |
| `src/s3Streams.ts` | Binds S3 body streams to the request; without it aborted downloads leak pool sockets. |
| `src/traceContext.ts` | Adopts the incoming b3/W3C trace id and prefixes every `debug` log line with it. |
| `src/eduSharingPlayer.ts` | Custom H5P player/renderer. |
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
  H5P page is its own S3 `GetObject`), `AWS_S3_CONNECTION_TIMEOUT_MS` (default 5000),
  `AWS_S3_REQUEST_TIMEOUT_MS` (default 0 = off; it is a *socket inactivity* timeout, and backpressure
  from a slow client stalls the S3 socket, so enabling it can truncate large downloads).
- **Server**: `PORT` (default 3000), `BASE_URL=/public/h5p`, `CACHE=in-memory`.

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
