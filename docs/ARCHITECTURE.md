# edu-sharing Rendering Service — Architecture

> **Status:** Living document · **Audience:** customers (Part A) and internal/engineering (Part B)
> **Format:** Markdown with embedded [Mermaid](https://mermaid.js.org/) diagrams.
> In **Confluence Cloud** the `mermaid` code blocks render natively; on **Server/Data Center**
> install the *Mermaid Diagrams for Confluence* app (see [§12 Confluence import notes](#12-confluence-import-notes)).

---

# Part A — Overview (for customers)

## 1. What is the Rendering Service?

The **edu-sharing Rendering Service** turns content stored in an edu-sharing repository into a form that
can be displayed safely and quickly in a web browser. When a user opens a learning object, the repository
asks the Rendering Service for a *preview* or *playable* representation. The service:

- **converts** the source file into a web-friendly format when needed (for example an Office document into
  a PDF or HTML preview, or a Jupyter notebook into an HTML page),
- **caches** the result so the same conversion is not repeated, and
- **serves** the rendered content back to the browser (with streaming support for audio and video).

It supports a broad range of content: **images**, **audio & video**, **Office documents & spreadsheets**,
**PDF** and **HTML**, **H5P** interactive content, **Jupyter notebooks**, **Moodle / SCORM** activities,
and external OER sources such as **Sodix** and the **Deutsche Digitale Bibliothek (DDB)**.

The service is **multi-tenant**: several edu-sharing repositories can register with one Rendering Service,
each with its own access keys, storage quota, and set of enabled content types.

## 2. System context

```mermaid
flowchart LR
    user["End user<br/>(web browser)"]
    repo["edu-sharing<br/>repository"]
    rs["Rendering Service"]

    subgraph infra["Supporting infrastructure"]
        store[("Object storage<br/>rendered files")]
        db[("Database<br/>job tracking")]
        cache[("Cache<br/>sessions & permissions")]
        queue{{"Job queue<br/>conversions"}}
        additional{{"Additional<br/>services"}}
    end

    user -- "1. opens content" --> repo
    repo -- "2. render info (signed request)" --> user
    user -- "3. loads preview / stream" --> rs
    rs -- "4. fetches the source file<br/>(signed request)" --> repo

    rs --- store
    rs --- db
    rs --- cache
    rs --- queue
    rs --- additional
```

**How to read it:** the Rendering Service and the user both talk to the Repository. Every request between
the repository and the service is cryptographically signed, so neither side trusts unverified input. The
service keeps rendered files in object storage, tracks long-running conversions in a database, caches
sessions and access permissions, and uses a queue to process heavy conversions in the background.

## 3. Supported content types

| Content type | Source examples | Rendered as |
|---|---|---|
| **Images** | JPEG, PNG, TIFF, … | Multiple resolutions/qualities for fast display |
| **Audio / Video** | MP3, MP4, … | Streamed directly (HTTP range / partial content) |
| **Office documents** | DOC(X), PPT(X), ODT, ODP, RTF, TXT | PDF or HTML preview (via LibreOffice) |
| **Spreadsheets** | XLS(X), ODS, CSV | PDF or HTML preview |
| **PDF / HTML** | PDF, HTML | Served directly, no conversion |
| **H5P** | `.h5p` packages | Interactive H5P player |
| **Jupyter notebooks** | `.ipynb` | Interactive HTML page |
| **Moodle / SCORM** | Moodle activities, SCORM packages | Embedded Moodle / SCORM player |
| **OER sources** | Sodix, DDB, EduHTML, Onyx, Binder | Embedded or linked representation |

## 4. How a rendering request works

There are two paths. If the content has already been rendered, the answer is instant. If not, the service
converts it in the background and the client picks up the result when it is ready.

**Fast path — already cached:**

```mermaid
flowchart LR
    A["Repository requests<br/>a preview"] --> B["Rendering Service<br/>verifies the request"]
    B --> C{"Already<br/>rendered?"}
    C -- "yes" --> D["Return a direct link<br/>to the cached file"]
    D --> E["Browser loads<br/>the preview"]
```

**Conversion path — first time / not cached:**

```mermaid
flowchart LR
    A["Repository requests<br/>a preview"] --> B["Rendering Service<br/>verifies the request"]
    B --> C{"Already<br/>rendered?"}
    C -- "no" --> F["Create a job<br/>and return a job id"]
    F --> G["Background worker<br/>downloads the source"]
    G --> H["Converter produces<br/>the preview"]
    H --> I["Store result in<br/>object storage"]
    I --> J["Client polls the job,<br/>gets the file link"]
    J --> K["Browser loads<br/>the preview"]
```

For some types the work is split into parallel pieces — e.g. an image is rendered into several resolutions
at once, and the job is "finished" only when all pieces are done.

---

# Part B — Technical appendix (internal)

## 5. Component architecture

Four runtime services plus shared infrastructure. The main `service` orchestrates; the three converters are
stateless workers reached over HTTP.

```mermaid
flowchart TB
    repo["edu-sharing repository<br/>(external)"]

    subgraph apps["Application services"]
        svc["service<br/>Spring Boot 4, Kotlin/Java 21<br/>API + orchestration + job workers"]
        doc["document-converter<br/>Spring Boot 3.5, LibreOffice/jodconverter<br/>Office → PDF/HTML"]
        jup["jupyter-converter<br/>Python FastAPI + nbconvert<br/>.ipynb → HTML"]
        lumi["lumi<br/>Node.js / TypeScript + H5P<br/>H5P editor / player"]
    end

    subgraph infra["Infrastructure"]
        mongo[("MongoDB<br/>jobs · H5P mappings")]
        redis[("Redis<br/>sessions · permission cache")]
        rabbit{{"RabbitMQ<br/>conversion job queue"}}
        s3[("S3-compatible storage<br/>MinIO / RustFS<br/>rendered assets")]
    end

    repo <-- "signed render / content<br/>(HTTPS)" --> svc

    svc -- "HTTP multipart" --> doc
    svc -- "HTTP multipart (async)" --> jup
    svc -- "reverse proxy / HTTP" --> lumi

    svc --- mongo
    svc --- redis
    svc --- rabbit
    svc --- s3

    lumi --- mongo
    lumi --- s3
```

| Service | Stack | Default port | Talks to |
|---|---|---|---|
| `service` | Spring Boot 4.0.x, Kotlin 2.3, Java 21 | 8080 (mgmt 9080) | MongoDB, Redis, RabbitMQ, S3, the 3 converters, edu-sharing repo |
| `document-converter` | Spring Boot 3.5.x + LibreOffice (jodconverter) | 8081 | (stateless; reads/writes S3 via the service) |
| `jupyter-converter` | Python 3.12+, FastAPI, nbconvert | 9120 / 8000 | (stateless) |
| `lumi` | Node.js 20+, TypeScript, `@lumieducation/h5p-*` | 3000 | MongoDB (`lumi` DB), S3 |

> `document-converter` is **deliberately** pinned to Spring Boot 3.5.x — jodconverter has no Boot 4 release yet.

## 6. Service-internal architecture

```mermaid
flowchart TB
    subgraph rest["REST layer (role: controller)"]
        rc["RenderController<br/>/public/renderdata"]
        ac["AssetController<br/>/public/asset (+ /static)"]
        mi["ModuleInfoController<br/>/public/modules"]
        ji["JobInfoController<br/>/public/job"]
        et["EduTrackingController<br/>/public/tracking"]
        adm["AdminController<br/>/admin/repository, /admin/cache"]
        lp["LumiProxyController<br/>/public/h5p"]
    end

    reg["ModuleRegistry<br/>dispatch by media/resource type"]

    subgraph mods["Plugin modules"]
        direction LR
        m1["RenderModule (base)"]
        m2["ConversionModule (async)"]
        m3["ThirdPartyModule (ext. creds)"]
        impl["Image · Document · Spreadsheet · Audio · Video<br/>H5p · Jupyter · Binder · Moodle · Scorm<br/>Onyx · Sodix · Ddb · EduHtml · Pdf · Html"]
    end

    subgraph back["Backends"]
        storage["StorageService<br/>S3StorageService + bucket strategies"]
        queue["QueueConfig + JobReceiver<br/>RabbitMQ"]
        session["Spring Session<br/>Redis"]
        esrepo["edusharingRepo services<br/>registration · content transfer · signing"]
    end

    rc --> reg --> mods
    ac --> storage
    lp --> lumiext["lumi (proxied)"]
    mods --> storage
    mods --> queue
    rc --- session
    ac --- session
    rc --- esrepo
    queue --- esrepo
```

**Module dispatch.** `ModuleRegistry` indexes every module by MIME type, resource type, replication source,
and remote-repository type. Resolution order: `node.mediatype` → `repository.repositoryType` →
`ccm:replicationsource` → `ccm:ccresourcetype` → MIME-type prefix; an unmatched node raises
`ObjectTypeNotSupportedException`. Modules implement `RenderModule` (synchronous), and optionally
`ConversionModule` (produces background sub-jobs) and/or `ThirdPartyModule` (external credentials, e.g. H5P).

**Role-gated beans.** Which of these beans actually start depends on the service's **role** — see the next
section.

## 7. Service roles & deployment modes

The `service` module ships as **one artifact**, but its beans are switched on or off by the `app.roles`
property (a comma-separated list). Each role activates a slice of functionality through `@ConditionalOn…`
annotations. This lets the *same* container image run as a self-contained monolith **or** be split into
independently scalable instances — e.g. many converter workers behind one master and one API front-end.

```mermaid
flowchart TB
    art["service artifact<br/>app.roles = …"]

    subgraph roles["Roles selected via app.roles"]
        ctrl["controller<br/>public REST API<br/>(Render, Asset, JobInfo,<br/>ModuleInfo, EduTracking, LumiProxy)"]
        conv["converter<br/>document · image · jupyter ·<br/>eduhtml · h5p conversion + receivers"]
        avc["avconverter<br/>audio / video conversion<br/>(resource-heavy)"]
        jm["job-manager<br/>JobReceiver: orchestrates the main job,<br/>downloads source, creates sub-jobs"]
        mst["master<br/>AdminController · CacheCleaner ·<br/>CorsSyncScheduler · registration"]
    end

    infra[("Shared infrastructure<br/>MongoDB · Redis · RabbitMQ · S3")]

    art --> ctrl
    art --> conv
    art --> avc
    art --> jm
    art --> mst

    ctrl -.-> infra
    conv -.-> infra
    avc -.-> infra
    jm -.-> infra
    mst -.-> infra
```

| Role token | Activated beans (examples) | Responsibility | Active by default\* |
|---|---|---|---|
| `controller` | `RenderController`, `AssetController`, `JobInfoController`, `ModuleInfoController`, `EduTrackingController`, `LumiProxyController` | Public-facing REST API + asset serving | yes |
| `converter` | Document / Image / Jupyter / EduHtml / H5p `ConversionService` + their RabbitMQ receivers | General content conversion workers | yes |
| `avconverter` | `AvReceiver`, `Audio`/`VideoConversionService` | Audio & video conversion (separated because it is CPU/IO-heavy) | yes |
| `job-manager` | `JobReceiver` | Consumes the main job message: downloads the source and fans out sub-jobs | yes |
| `master` | `AdminController`, `CacheCleaner`, `CorsSyncScheduler`, repository registration | Administration & housekeeping (cache eviction, CORS sync, repo registration) | no — must be listed |

\* When `app.roles` is **empty/unset**, `controller`, `converter`, `avconverter` and `job-manager` are
active but **`master` is not** (it requires the token explicitly). The shipped default lists all five:
`app.roles=master,controller,converter,avconverter,job-manager`.

Roles are orthogonal — combine any subset. Common deployment modes (same image, different `app.roles`):

| Mode | `app.roles` | Runs |
|---|---|---|
| All-in-one (default) | `master,controller,converter,avconverter,job-manager` | Everything in one instance |
| API front-end | `controller` | Public endpoints only; conversions handled elsewhere |
| General worker | `converter,job-manager` | Job orchestration + non-AV conversion, no public API |
| A/V worker | `avconverter,job-manager` | Dedicated, separately scaled audio/video transcoding |
| Master / housekeeping | `master` | Cache cleaner, CORS sync, repository registration |

> Splitting `avconverter` onto its own instances is the typical way to scale media transcoding
> independently from document/image work.

### Runtime interaction between roles

At runtime the roles form a pipeline around **RabbitMQ**. `controller` instances accept requests at the
top and enqueue work; `job-manager` picks up each main job, splits it, and re-queues the pieces;
`converter` / `avconverter` instances drain those queues and write results to S3. `master` runs to the
side as a **single instance**, doing scheduled and administrative work that must not run in parallel.

```mermaid
flowchart TB
    ext["edu-sharing repository / browser"]

    subgraph scal["Horizontally scalable (N instances each)"]
        direction TB
        ctrl["controller<br/>accepts requests · verifies signatures<br/>serves cached assets · creates main jobs"]
        rabbit{{"RabbitMQ<br/>main-job queue + per-type sub-job queues"}}
        jm["job-manager<br/>consumes main job · downloads source<br/>creates &amp; distributes sub-jobs"]
        conv["converter<br/>document · image · jupyter · eduhtml · h5p"]
        avc["avconverter<br/>audio / video"]
    end

    mst["master &nbsp;(single instance)<br/>scheduler · repository registration<br/>cache cleaner · administration"]
    s3[("S3 storage")]

    ext -->|"1 render request"| ctrl
    ctrl -->|"2 publish main job"| rabbit
    rabbit -->|"3 deliver main job"| jm
    jm -->|"4 publish sub-jobs"| rabbit
    rabbit -->|"5 deliver sub-jobs"| conv
    rabbit -->|"5 deliver A/V sub-jobs"| avc

    jm --> s3
    conv -->|"6 write rendered asset"| s3
    avc -->|"6 write rendered asset"| s3
    ctrl -->|"7 serve cached asset"| ext
    ctrl -.-> s3

    mst -.->|"evict on quota · housekeeping"| s3
```

- **controller** is the only role exposed to the outside; it never converts, it only routes and serves.
- **RabbitMQ** decouples the tiers — adding more `converter`/`avconverter` instances increases throughput
  without touching the controllers.
- **job-manager** is the orchestrator: one main job in, many specialised sub-jobs out.
- **master** is deliberately run **once** (scheduler, cache eviction, registration, admin) so housekeeping
  is not duplicated.

## 8. End-to-end job flow

The **frontend** (the edu-sharing web application in the user's browser) is the client that drives the
whole flow. It first obtains the render data and metadata from the repository over a **secured endpoint**;
the repository returns a **signed payload**. The frontend then talks directly to the rendering service's
controllers — `RenderController` to request the rendering, `JobInfoController` to poll an async job, and
`AssetController` to load the result. The rendering service itself only calls back to the repository to
**fetch the source content** for a conversion.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend (browser app)
    participant Repo as edu-sharing repo
    participant RC as RenderController
    participant Mod as RenderModule
    participant DB as MongoDB
    participant MQ as RabbitMQ
    participant JR as JobReceiver (worker)
    participant CT as ContentTransferService
    participant Conv as External converter
    participant S3 as S3 storage
    participant JI as JobInfoController
    participant AC as AssetController

    FE->>Repo: request render data / metadata (secured endpoint)
    Repo-->>FE: signed node payload + signature
    FE->>RC: POST /public/renderdata (signed node + signature)
    RC->>RC: verify signature (repo public key)
    RC->>Mod: handle(node)
    alt already cached
        Mod->>S3: lookup rendered asset
        Mod-->>RC: ObjectLink(s)
        RC-->>FE: links (fast path)
    else needs conversion
        Mod->>DB: create RenderingJob (QUEUED)
        Mod->>MQ: publish RenderingJobMessage
        Mod-->>RC: jobId
        RC-->>FE: jobId
        MQ->>JR: deliver message
        JR->>DB: mark PROCESSING
        JR->>CT: download source (signed)
        CT->>Repo: GET /content?nodeId&signature&timestamp
        Repo-->>CT: source stream
        CT->>S3: store in temp bucket
        JR->>MQ: publish SubJob(s) (e.g. per image quality)
        MQ->>Conv: deliver sub-job
        Conv->>S3: read temp, write rendered asset
        Conv->>DB: mark SubJob FINISHED
        DB->>DB: all sub-jobs done → RenderingJob FINISHED
    end

    FE->>JI: GET /public/job/{jobId} (poll)
    JI-->>FE: FINISHED + ObjectLink(s)
    FE->>AC: GET /public/asset?params=…
    AC->>S3: stream asset
    AC-->>FE: 200/206 + CSP header
```

**Sub-job fan-out.** A `ConversionModule` may split one job into several `SubJob`s — the canonical example
is `ImageRenderModule`, which renders multiple resolutions in parallel. `MainJobLogic` flips the parent
`RenderingJob` to `FINISHED` only once every `SubJob` completes. `RenderingJob` documents carry a **TTL
index (~6h)** so completed/stale jobs expire automatically.

## 9. Deployment topology

```mermaid
flowchart TB
    client["End user / browser"]
    repo["edu-sharing repository<br/>(external)"]

    subgraph api["API tier"]
        svc["service :8080"]
        doc["document-converter :8081"]
        jup["jupyter-converter"]
        lumi["lumi :3000"]
    end

    subgraph data["Data tier"]
        mongo[("MongoDB :27017<br/>DBs: rendering, lumi")]
        redis[("Redis :6379")]
        rabbit{{"RabbitMQ :5672 / mgmt :15672"}}
    end

    subgraph store["Storage tier"]
        s3[("S3-compatible :9000<br/>RustFS / MinIO")]
    end

    client --> svc
    repo <--> svc
    svc --> doc
    svc --> jup
    svc --> lumi
    svc --- mongo
    svc --- redis
    svc --- rabbit
    svc --- s3
    lumi --- mongo
    lumi --- s3
```

- **Docker Compose** (dev/test): one stack; standalone Redis, standalone MongoDB, **RustFS** as the
  S3-compatible store. Files: `deploy/docker/compose/src/main/compose/*.yml`. Edit only `src/`; `target/` is generated.
- **Kubernetes / Helm** (production): per-component charts under `deploy/docker/helm/*`. Adds **Redis Cluster**,
  optional **Istio** (VirtualService/DestinationRule/Gateway), an optional **Varnish** caching sidecar,
  **HPA**, and a choice of **MinIO or RustFS** for object storage. External traffic enters only via `service`.

## 10. Security model

- **Request signing.** Every `POST /public/renderdata` carries a Base64 node payload + RSA signature. The
  service verifies it against the registered repository's **public key** (`RepositoryPublicKeyService`,
  cached). Outbound content fetches are signed with the service's **private key** (`EncryptionService`),
  so the repository can verify the caller.
- **Authentication & sessions.** A JWT filter authenticates `/public/**`; sessions (incl. the Spring
  Security context) are stored in **Redis** via Spring Session, serialized as JSON (Jackson 3).
- **Filter chains.** `/public/**` → JWT; `/admin/**` → HTTP Basic (admin user). Method-level
  `@PreAuthorize`/`@PostAuthorize` enforce node read/download permissions; permissions are cached per
  session with a per-module TTL (e.g. `app.session.image.nodePermissionExpirationTime`).
- **Content-Security-Policy.** Each module supplies its own CSP header (configurable per repository,
  e.g. H5P `frame-ancestors`), applied on every `AssetController` response for safe iframe embedding.

## 11. External integrations & data stores

| System | Role | Notes |
|---|---|---|
| **MongoDB** | Job tracking (`RenderingJob`, `SubJob`), H5P node↔content mappings | TTL index on jobs (~6h); per-module DBs `rendering` and `lumi` |
| **Redis** | HTTP sessions + security context; node-permission cache; repository public-key cache | Standalone (`spring.redis.standalone.*`) or cluster (`spring.redis.cluster.*`) — project-specific prefixes |
| **RabbitMQ** | Async conversion job queue (topic exchange, per-module routing keys) | `JobReceiver` consumes; module-specific receivers handle sub-jobs |
| **S3 (MinIO/RustFS)** | Cached rendered assets + temp conversion buckets | Bucket strategies: per-customer, per-media-type, external-customer-bucket; `CacheCleaner` evicts on quota |
| **edu-sharing repository** | Source of content; registers with the service | Registration via `/admin/repository/register`; signed `GET /content` for source files |

## 12. Confluence import notes

- **Confluence Cloud:** paste this file's content (or each `mermaid` block) into a page — Mermaid renders
  natively. Markdown can be brought in via *Insert → Markdown* or the *Markdown* macro.
- **Confluence Server / Data Center:** install the **Mermaid Diagrams for Confluence** app, then put each
  diagram inside its Mermaid macro. Alternatively export diagrams to SVG/PNG
  (e.g. `npx @mermaid-js/mermaid-cli -i ARCHITECTURE.md -o architecture.svg`) and attach the images.
- **Two-audience split:** **Part A** is self-contained and free of internal class names — copy it to a
  customer-facing page on its own. Keep **Part B** on an internal page.
