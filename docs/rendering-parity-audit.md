# Rendering Service — PHP → Kotlin/Spring Re-implementation Parity Audit

**Date:** 2026-06-29
**Old service (source of truth):** `/home/marian/testCheckouts/edu-sharing-community-services-rendering/` — a **PHP** application (server-side HTML renderer, local-disk cache, PostgreSQL/MySQL tracking, embedded `h5p-core`).
**New service:** `/home/marian/development/rendering2/services-rendering-service2/` — **Spring Boot 4 / Kotlin** main service + standalone **document-converter** (LibreOffice/jodconverter), **jupyter-converter** (Python/nbconvert), **lumi** (Node `@lumieducation/h5p-server`), **admin-frontend** (Angular). Async job/JSON API, S3 cache, MongoDB tracking.

This audit compared the new modules against their old equivalents to find **crucial, especially opaque, logic that may have been lost or silently changed** in the rewrite. Six areas were audited in parallel; the detailed per-area findings (with file/line citations and exact ffmpeg/OAuth/threshold parameters) are in the sections that follow this summary.

> **Important framing.** The architecture deliberately changed: the old service produced HTML and did conversions in-process; the new one returns links/job-ids and converts out-of-process, with the *frontend/repository* doing presentation. So a large amount of old **template/embed/display** logic is **intentionally relocated** and is **not** flagged as a regression. The findings below isolate behaviour that was **security-, correctness-, or dispatch-relevant** — not presentation.

---

## How to read this report

Each finding has a severity. Many "High" items are tagged **"verify intent"** — they are behaviours present in the old code with no new equivalent, where we cannot tell from the code alone whether the loss is deliberate (moved to the frontend/repo) or accidental. Those need a product/architecture decision, not necessarily a code fix.

- **Critical** — security regression or data-correctness/loss issue; action recommended before cutover.
- **High** — meaningful lost capability or a clear bug; needs an explicit port-or-drop decision.
- **Medium / Low / Info** — behavioural diffs, hardening gaps, or confirmed-intentional simplifications recorded for completeness.

---

## Module mapping (old → new)

| Old PHP | New | Status |
|---|---|---|
| `func/classes.new/ESRender/*`, `ESObject`, `index.php` (dispatch/sig/jobs) | `core/`, `modules/ModuleRegistry`, `renderingJob/`, `security/`, `edusharingRepo/` | Re-architected; **signature & dispatch gaps** (§1) |
| `modules/picture` | `modules/image` | Covered; **EXIF/alpha/tiers** gaps (§2) |
| `modules/audio`, `modules/video`, `ESRender/Module/AudioVideo/*` | `modules/av/{audio,video}` | Covered; **codec/webm/timeout** gaps (§2) |
| `modules/doc`, `office`, `office_spreadsheet` | `modules/document` + `document-converter` | Covered; **HTML sanitization, output formats** (§3) |
| `modules/qti`, `modules/qti21` | `modules/onyx` | Covered; **uniqueId isolation, language, cred bug** (§3) |
| *(none — new)* | `modules/jupyter` + `jupyter-converter` | New; **unsanitized HTML / CSP** (§3) |
| `modules/h5p` (+ embedded `h5p-core`) | `modules/h5p` + `lumi` | Re-architected; **version bug, size guard** (§4) |
| `modules/moodle`, `modules/scorm` | `modules/moodle` (+ `ScormRenderModule`) | Covered; **WS contract, courseId cache** (§5) |
| `modules/html` | `modules/eduhtml`, `modules/noConversion` | Covered; **Storyline `story.html` / custom entry** (§5) |
| `modules/url` (YouTube/Vimeo/Pixabay/Prezi/LTI-1.3) | — | **DROPPED** (§5 C1) |
| `modules/lti` (LTI-1.1 + OAuth-1.0, Etherpad, Vanilla) | — | **DROPPED** (§5 C2) |
| `modules/learningapps` | — | **DROPPED** (§5 H1) |
| `modules/collection`, `directory`, `saved_search` | — | **DROPPED** (§5 M1–M3) |
| `modules/scenario` | — | **DROPPED** (legacy/dead — safe) |
| *(none)* | `modules/sodix`, `modules/ddb`, `modules/binder` | New integrations (not migrations) |
| `admin/*`, `CacheCleanerClass`, deploy cron | `edusharingRepo` admin controllers, `cacheCleaner`, `admin-frontend`, helm/compose | Re-architected; **eviction policy & negative helm thresholds** (§6) |

---

## Top priorities (the short list)

If only a handful of items are actioned before cutover, these are the ones with real blast radius:

### Security
1. **§1 C1 — Signature replay protection lost.** The old service signed `repoId+nodeId+timestamp` and enforced a freshness window (`message_offset_ms`); the new `RenderController.verifySignedNode` verifies the raw node bytes only — **no timestamp, no expiry**. Captured `(node, signature)` pairs are replayable forever.
2. **§1 C2 — Attacker-controlled signature algorithm.** The new code passes `body.signatureAlgorithm` from the request straight into `Signature.getInstance(...)`, with a literal `//@TODO: check if signatureAlgorithm is allowed` still in place. The old code hard-coded `sha1WithRSAEncryption`. Resolve the algorithm from the repo registration instead.
3. **§3 C2 / H4 — Lost HTML sanitization.** The old `doc` module ran **HTMLPurifier** over HTML content; the new document/spreadsheet/Jupyter HTML paths do **no** server-side sanitization and rely solely on a per-repo CSP (Jupyter doesn't even override `getCspHeader`). Treat LibreOffice/nbconvert HTML as untrusted.
4. **§7 (helm) — Negative cache-cleaner thresholds.** `deploy/.../helm/service/.../values.yaml` sets `threshold.lower: -0.7`, `upper: -0.9`. Once any repo quota is set under helm, the cleaner would **wipe the entire repo cache every run**. (Compose is fine — it uses bash `:-` defaults.)

### Correctness / data
5. **§3 C3 — Onyx assessment isolation lost.** `uniqueId` changed from `repo+app+course+user` to `nodeId_hash` — different students can collide in the same Onyx session. Plus language is hard-coded `"de"` (§3 H3) and `requiredCredentialKeys` is a malformed one-element set (§3 M2, a clear bug).
6. **§4 #1 — Lumi H5P version bug.** `lumi/src/index.ts` computes `coreApiVersion` with **swapped** major/minor (→ {28,1}) and `h5pVersion` as `"1.1.0"` (should be {1,28} / "1.28.0"). Affects library-compatibility checks. Easy fix.
7. **§2 — Media regressions:** MP4 audio changed AAC→MP3 (poor iOS/Safari support); EXIF auto-rotation dropped (portrait phone photos render sideways); image transparency lost (forced JPEG/RGB); no ffmpeg encode timeout or stuck-job requeue.

### Dispatch / lost capability (verify intent, then port or document)
8. **§1 H1 — Dispatch fallback removed.** The old `setModule()` fell back to `doc`/download for unknown types; the new `ModuleRegistry` **throws `ObjectTypeNotSupportedException`**. Combined with the dropped modules below, several content classes now error instead of rendering.
9. **§5 C1/C2 — `url` and `lti` modules dropped.** YouTube-nocookie/short-link parsing, Vimeo privacy-hash reconstruction, Pixabay/Prezi/LearningApps embedding, **LTI-1.3** launch (url module) **and** **LTI-1.1 OAuth-1.0 HMAC-SHA1 signing** + Etherpad/Vanilla (lti module) have no backend equivalent. The OAuth signing is exactly the kind of opaque logic that must not be re-written from memory if still needed.
10. **§1 H2 — License gating (`hasContentLicense`) removed.** The old "you may see the node but not its licensed content → downgrade to neutral doc viewer + warning" behaviour (with `collection_io_reference`/`accessEffective`/`originalRestrictedAccess` rules) is gone; access now rests entirely on the JWT `ReadAll` claim. Confirm the repo computes that claim with the same semantics.
11. **§5 H2 — Articulate Storyline `story.html`** entry-point and `ccm:ccressourcemainentity` custom entry override lost in `eduhtml` (only `index.html` is found) — Storyline packages will fail.

### Operational
12. **§6 — Cache-cleaner policy fundamentally changed** (disk-ratio global LRU @80% → per-repo quota-ratio LRU @25%) and is a **no-op by default** (quota defaults to 0), so the S3 cache grows unbounded out of the box. The out-of-process CronJob + in-container cron + `useDiskSize` disk-protection backstop are all gone. Cron format also changed 5-field → 6-field.
13. **§6 #15 / §2 / §3 — Dropped converter limits:** no ffmpeg exec timeout, no jodconverter/WebClient response timeout, a 20 MB WebClient buffer ceiling vs 128 MB upload limit, outbound HTTP proxy support dropped, and an audio-bitrate unit mismatch (`160000` vs `160`).

---

## Things that improved (recorded so they aren't "fixed" back)

- **CORS** hardened from `Access-Control-Allow-Origin: *` to per-repo synced origins (§1 L5).
- **Admin auth** moved from MD5(DB-password) login to HTTP Basic (§6 #10); admin UI scope **expanded** (Angular SPA) (§6 #11).
- **H5P** framework/validator/sanitizer hand-rolled glue (~1900 lines) replaced by the maintained `@lumieducation` stack (§4 #11).
- **Presentations**: PPT (previously unrenderable) and PPTX/ODP now convert to PDF (§3 C1 — verify the frontend expects PDF).
- **S3 health probe** arguably better than the old DB-only check; content-fetch now sends explicit `signedAlg` and handles empty responses (§1 M5).

---

## Caveats on method

These findings come from reading the two codebases statically; they were **not** runtime-verified. Items tagged "verify" could not be fully resolved from the in-scope files — notably: the repo-side computation of the JWT `ReadAll`/permission claim, per-module S3 cache-hit/self-heal behaviour, `RegistrationRunner` keypair bootstrap, WebClient trace-header propagation, and whether the deployed Moodle plugin matches the new WS contract. Each is flagged in place. The old PHP was treated as the behavioural source of truth.

---

# Detailed findings by area

The sections below are the full per-area audits. Severity ladders and recommendations are inline.

---

# §1 — Core framework, dispatch, jobs, security & repo integration


PHP→Kotlin parity audit of the edu-sharing rendering service. Source of truth = OLD PHP.

- OLD base: `/home/marian/testCheckouts/edu-sharing-community-services-rendering/service/src/main/php/`
- NEW base: `/home/marian/development/rendering2/services-rendering-service2/service/src/main/kotlin/org/edu_sharing/rendering/`

> **Important architectural context.** The OLD service is a server-side HTML renderer: `index.php` resolves a module, fetches/caches content, then *renders HTML/JS templates* (license footer, metadata, sequence, data-protection dialog, error pages) that are streamed back. The NEW service is a JSON API: `POST /public/renderdata` returns either `objectLinks` or a `jobId`, and the **frontend/repository** does the rendering. So a lot of OLD template/display logic is *intentionally* relocated to the consumer. Where that's the case I mark it **Intentionally dropped (legacy)** — but several pieces that were *security- or dispatch-relevant* (not just presentation) appear to have been lost and are flagged Critical/High.

---

### CRITICAL findings

#### C1. Signature verification no longer binds a timestamp — replay protection lost
- **Severity:** Critical
- **OLD:** `application/esmain/validate_signature.php` + `application/esmain/index.php` (lines ~208–237).
  - Signed string is `sigString . $ts` where `sigString = repo.id + node.id` (or `sig_token` if no node). `openssl_verify($sigString . $ts, $signature, $pubkey, 'sha1WithRSAEncryption')`.
  - **After** signature passes, `index.php` enforces a window: rejects if `now + message_send_offset_ms < ts` ("Timestamp sent bigger than current timestamp") and if `now - ts > message_offset_ms` ("Token expired"). Defaults 10000 ms, overridable per-repo via `message_send_offset_ms` / `message_offset_ms`.
- **NEW:** `core/RenderController.kt` `verifySignedNode()`. Verifies `signature` over the **raw node bytes only** (`verify.update(nodeData)`), against the repo public key. **No timestamp is part of the signed payload, and there is no freshness/expiry/future-skew check at all.**
- **Impact:** A captured `(securedNode, signature)` pair is valid **forever** and can be replayed indefinitely. The OLD design specifically signed-in `ts` and bounded its age to defeat replay. This is a genuine security regression, not a presentation concern.
- **Recommendation:** Include a repository-provided timestamp (and ideally nonce) in the signed payload and re-introduce the `message_offset_ms` / `message_send_offset_ms` window (with per-repo override). At minimum verify a freshness claim. Verify whether the JWT (see C2) is meant to carry this — if the JWT is the freshness mechanism, document it explicitly, because the node-signature path is reachable independently.

MZ: This is fine!

#### C2. Signature algorithm is attacker-controlled in the node-signature path
- **Severity:** Critical
- **OLD:** algorithm is **hard-coded** to `sha1WithRSAEncryption` in `validate_signature.php` (line 35). The client cannot choose it.
- **NEW:** `core/RenderController.kt`: `val signatureAlgorithm = body.signatureAlgorithm` taken **straight from the request body** (`RenderDataRequest.signatureAlgorithm`, `core/dto/RenderDataRequest.kt`) and passed to `Signature.getInstance(signatureAlgorithm)`. There is even a literal `//@TODO: check if signatureAlgorithm is allowed` left in the code (line 49).
- **Impact:** The verifier lets the *caller* pick the JCA algorithm string. This is exactly the JWT "alg confusion" class of bug applied to a raw signature: a caller can select a weaker/mismatched algorithm, or feed an unexpected algorithm name. Combined with C1 (no freshness), the trust boundary here is weak.
- **Note:** elsewhere the algorithm is server-derived and sensible — `EncryptionService.getSigningAlg()` reads `registration.signingAlgorithm`, and `RepositoryRegistrationService.getSigningAlgorithm()` defaults to `SHA1withRSA` from the repo `/about` endpoint. So the fix is to validate `body.signatureAlgorithm` against (or simply replace it with) the registered repo algorithm.
- **Recommendation:** Resolve the algorithm from the repository registration (as the content-fetch path already does) and reject any client-supplied value that doesn't match an allow-list. Remove the TODO by implementing it.

MZ: Fixed in Commit 072e61627a5ad49be9e4be4b9e1387a53f8fb529.

#### C3. Concurrent-render de-duplication / locking semantics changed (possible duplicate work & races)
- **Severity:** Critical (correctness/robustness)
- **OLD:** `index.php` + `ESRender/Module/Base.php`. Heavy machinery: `instanceExists()`, `instanceLock()`, `instanceUnlock()`, an `ESOBJECT_LOCK` table, a **busy-wait loop** (poll every 200 ms up to 60 s) when another request holds the lock, and a `register_shutdown_function` that on fatal error unlocks the instance and deletes the DB row. `instanceExists()` also self-heals: if the DB row exists but the cache file is missing, it deletes the DB row and returns false (cache-invalidation by file presence). Cache key = `rep_id + content_hash + object_id` (version deliberately *not* part of existence check since 5.1 — same content across versions reuses cache).
- **NEW:** `renderingJob/MainJobCreationService.kt` `getExistingJobId()` reuses an existing job only if it is *not* FINISHED/FAILED/PARTIALLY_FAILED **and** `esHash == cacheObject.hash`. There is **no lock + wait**; two simultaneous requests for the same uncached node can both find "no unfinished job" and both create a job/enqueue conversion. There is no shutdown-hook cleanup of a stuck in-progress entry; `MainJobLogic` instead relies on sub-job status aggregation, and the queue "self-heals" by dropping messages with no DB entry (`JobReceiver` returns on `findByIdOrNull(...) ?: return`).
- **Impact:** Different model, mostly defensible for an async-job design, but (a) the *content-hash-based, version-agnostic* cache-reuse rule from OLD is only partially reproduced, and (b) there's no mutual exclusion, so duplicate conversions for the same node under load are possible. Confirm this is acceptable / idempotent at the storage layer.
- **Recommendation:** Verify the storage/job layer is idempotent for concurrent identical jobs; if not, add a uniqueness guard (e.g. unique index on nodeId+hash+module for in-flight jobs). Confirm the OLD "missing cache file ⇒ invalidate DB row" self-healing is covered by S3 existence checks in the modules (this audit didn't trace every module's cache-hit check — needs verification per module).

MZ: Opened ticket.

---

### HIGH findings

#### H1. Module-resolution dispatch order and many special-case routes are LOST
- **Severity:** High
- **OLD:** `ESObject::setModule()` (`func/classes.new/ESObject.php` lines 395–539) is a long, *ordered* decision tree. In order:
  1. `hasContentLicense === false` ⇒ **module `doc`** (forces a neutral viewer when the user lacks a content license).
  2. `ccm:revoked` aspect ⇒ **module `doc`**.
  3. `isDirectory` + `ccm:collection` aspect ⇒ **`collection`**; else `isDirectory` ⇒ **`directory`**.
  4. `type == ccm:saved_search` ⇒ **`saved_search`**.
  5. `ccm:tool_instance_key` set ⇒ **`lti`**.
  6. `remote.repository.repositoryType` == `YOUTUBE` / `PIXABAY` / `LEARNINGAPPS` ⇒ **`url`** / **`learningapps`**.
  7. `ccm:replicationsource == oai:dmglib.org` ⇒ **`url`**.
  8. `isLti13ToolObject()` (`ccm:ltitool_node` aspect) ⇒ **`url`**.
  9. `ccm:replicationsource == DE.FWU` && `ccm:wwwurl` ⇒ **`url`**.
  10. `ccm:wwwurl` present ⇒ **`url`**; else `application/zip|vnd.moodle.backup` ⇒ resource-type dispatch; else `audio/mp4` ⇒ **`audio`**; else by mimetype.
  11. Final pass: `scandir(modules)` + each module's static `canProcess($esObject)` can override.
  Plus `ESModule::setModuleByResource()` (resource-type/version → h5p, lti, scorm/html, qti21/doc, moodle, scenario, eduhtml) and `setModuleByMimetype()` with a DB table `REL_ESMODULE_MIMETYPE` and a VIEWER_JS gate that downgrades `office`/`spreadsheet`/`pdf` to `doc`.
- **NEW:** `modules/ModuleRegistry.kt` `getRenderModule(node)` resolves in a **fixed precedence**: `type(mediatype) → remoteRepositoryType → replicationSource → resourceType → mimeType(exact) → mimeTypePrefix → throw ObjectTypeNotSupportedException`. Registered keys (from the `*ModuleTypeMapper`s): `type=file-h5p`, `remoteRepositoryType=DDB`, `replicationSource=SODIX`, `resourceType ∈ {eduhtml, moodle, git-binder, ADL SCORM, IMS Common Cartridge}`, exact mimetypes for office docs/spreadsheets + `application/pdf`, `text/html`, and prefix `image`/`audio`/`video`. (`modules/*/...ModuleTypeMapper.kt`.)
- **What's missing / changed (verified by grep — no hits in `*.kt`):**
  - **No license-gated `doc` fallback** (`hasContentLicense` is gone entirely — see H2).
  - **No `ccm:revoked` handling** ⇒ revoked nodes are not forced to a neutral viewer.
  - **No `saved_search`, `directory`, `collection`** routing (`type == ccm:saved_search`, `isDirectory`, `ccm:collection`).
  - **No `lti` routing** for `ccm:tool_instance_key` or `ccm:ltitool_node`.
  - **No `url`-module routing** for `YOUTUBE`/`PIXABAY`/`LEARNINGAPPS` remote types, `oai:dmglib.org`, `DE.FWU`+`wwwurl`, or generic `ccm:wwwurl`. (Mapper reads `ccm:wwwurl` into `CacheObject.externalUrl` but nothing dispatches on it.)
  - **No `audio/mp4 ⇒ audio`** special-case (in NEW it would resolve by mime prefix `audio` — probably equivalent, verify).
  - **No `imsqti ⇒ application/zip` mimetype rewrite** and **no qti/qti21/scenario modules** at all.
  - **No generic `canProcess()` last-resort scan.**
  - **No final fallback to `doc`/download** when nothing matches — NEW *throws* `ObjectTypeNotSupportedException`. OLD almost always fell back to `doc` and downloaded. This changes behavior for any unrecognized mimetype from "served as generic doc/download" to "error".
- **Impact:** Several whole content classes (saved searches, directories/collections, LTI tools, YouTube/Pixabay/LearningApps/dmglib/FWU external URLs, QTI, scenario) that OLD rendered now hit `ObjectTypeNotSupportedException`. Some of these may be deliberately out-of-scope for v2 / handled by the repo frontend, but that needs to be confirmed item-by-item; at least the *generic-doc fallback* difference (error vs. download) is a real regression for long-tail mimetypes.
- **Recommendation:** Produce an explicit decision per OLD route (port vs. intentionally drop). Re-introduce a generic fallback (doc/download) instead of throwing for unknown types, or document why an error is now correct. Confirm `audio/mp4` and the office→doc VIEWER_JS downgrade are intentionally simplified.

MZ: Check this! revoked, dmglib. Prio 1

#### H2. License / access gating (`hasContentLicense`, collection-reference access logic) is LOST
- **Severity:** High
- **OLD:** `index.php` lines 143–162 computes `Config::set('hasContentLicense', …)` with subtle, version-dependent rules:
  - For `ccm:collection_io_reference` nodes: if `originalRestrictedAccess` ⇒ require `ReadAll` in `accessEffective`; else legacy branch on `accessOriginal` Read ⇒ require `ReadAll` in `accessEffective`; else require `ReadAll` in `access`.
  - For regular nodes: `ReadAll` in `accessEffective` OR `access`.
  - `ESObject::hasPermission()` (`ESObject.php` 936–945) mirrors this for collection references.
  - When false ⇒ forces `doc` module (H1.1) and the license footer renders a "no content license" warning (`Module/Base.php prepareRenderData`, `ESRender/License.php`).
- **NEW:** Permission gating is `@PreAuthorize("hasPermission(#request.nodeId, 'ReadAll')")` on `RenderDataService.getRenderModule` (`core/RenderDataService.kt`), backed by `NodePermissionSessionContextEvaluator`, where the permission set comes from the **JWT** (`security/jwt/JwtUtils.kt` `permissions` claim) — not from `node.access` / `accessEffective` / `accessOriginal`. There is **no `hasContentLicense` concept**, no collection-reference `originalRestrictedAccess`/`accessOriginal` branching, and no "force doc + show no-license warning" behavior. Grep confirms `accessEffective`, `accessOriginal`, `originalRestrictedAccess`, `collection_io_reference`, `hasContentLicense` have **zero hits** in `*.kt`.
- **Impact:** The nuanced "you may *see* the node but not its licensed content, so we downgrade to a neutral doc viewer + warning" behavior is gone. If the JWT `ReadAll` is computed by the repository with the same collection/license semantics, the *access decision* may be preserved, but the **downgrade-to-doc + license-warning UX** is not, and the repo-side computation needs verification.
- **Recommendation:** Confirm the repository's JWT `permissions=ReadAll` is computed with the exact collection_io_reference/originalRestrictedAccess/accessEffective rules; if not, port them. Decide where the "no content license ⇒ neutral viewer + warning" behavior now lives (frontend?) and document it.

MZ: This is fine, checked with TS.

#### H3. DataProtection / GDPR handler not ported (but OLD was already disabled)
- **Severity:** High (verify intent) — likely **Intentionally dropped**, but worth confirming
- **OLD:** `ESRender/DataProtectionRegulation/DataProtectionHandler.php` + `Handler.php`. `index.php` constructs `DataProtectionHandler` and calls `handle()`; if it returns content, rendering stops and a consent dialog wraps the embed. Logic: `RemoteObjectType` classifies the node (YouTube/Vimeo/Prezi/H5P/video/iframe/audio/image/generic), `getHandlerDetails()` returns provider name + privacy URL for matching providers, gated by `DATAPROTECTIONREGULATION_CONFIG['enabled']` (default **false**) and an optional module allow-list and URL regex map. **However** `Handler::getApplyDataProtectionRegulationsDialog()` has an unconditional `return '';` at the very top (line 15), and `getConfig()` defaults `enabled=false`, so in practice the dialog content was empty/disabled in this checkout.
- **NEW:** No equivalent. Grep for data-protection/consent: none. CSP handling exists (`getCspHeader`, `RepositoryRegistrationService.setCspHeader`) but that is a different concern.
- **Impact:** If any deployment enabled `DATAPROTECTIONREGULATION_CONFIG`, the consent gate before embedding 3rd-party content (YouTube/Vimeo/etc.) is gone. Given the OLD code path was effectively disabled, this is probably an intentional drop, but the `RemoteObjectType` provider-classification (YouTube/Vimeo/Prezi/H5P detection by URL) was also the basis for routing and is not reproduced.
- **Recommendation:** Confirm GDPR consent is now a frontend responsibility. If 3rd-party-embed consent is still required by product/legal, re-implement.

MZ: This is fine.

#### H4. Sequence (children) handling not ported
- **Severity:** High (verify intent)
- **OLD:** `ESRender/Sequence/Handler.php` + `ESObject` `sequenceHandler`. `isSequence()` true when `data.children` non-empty; renders a child navigation strip (iconURL/name/NodeID per child) into the footer (`Module/Base.php prepareRenderData`).
- **NEW:** No `children`/sequence concept. `RenderDataRequest`/`Node` handling never inspects `children`. Grep: no `sequence`/`children` dispatch.
- **Impact:** Multi-part "sequence" objects lose their child-navigation. Likely now a frontend concern, but the service no longer surfaces children at all.
- **Recommendation:** Confirm the frontend gets children from the repo directly; otherwise port.

MZ: Im Dokument.

#### H5. Object tracking on every render replaced by event-conditional async tracking
- **Severity:** High → Medium (behavioral)
- **OLD:** `ESRender/Application.php trackObject()` is called **at the end of every successful render** (`index.php` line 431) and writes an `ESTRACK` DB row keyed by the internal ESOBJECT id. Always runs (best-effort, swallows errors).
- **NEW:** `core/RenderController.kt` calls `trackingService.trackObject(objectId, event, repoId)` **before** dispatch. `EduTrackingService.trackObject` (`edusharingRepo/EduTrackingService.kt`) **skips entirely** when `event == "PRERENDER"` **or** `!securityEnabled`, and otherwise fires an **async** `trackEventAsync` to the *repository* (not a local DB). Default event is `VIEW_MATERIAL`.
- **Impact:** (a) Tracking now happens *before* the render succeeds (OLD tracked only on success). (b) With `app.security.enabled=false` (the test/dev profile) **nothing is tracked**. (c) Tracking moved from a local table to a repo callback. Probably intended (the repo owns analytics now), but the "track only on success" and "always track" semantics changed.
- **Recommendation:** Confirm tracking-before-dispatch is acceptable (a failed render now still records a view). Document the `securityEnabled=false ⇒ no tracking` behavior.

MZ: Check this! When do we track the event? Prio 2.

---

### MEDIUM findings

#### M1. Display-mode (`download`/`inline`/`dynamic`/`embed`/`prerender`/`locked`) dispatch collapsed
- **Severity:** Medium
- **OLD:** `ESRender/Module/Base.php process()` switches on `display` request param across six modes; modules implement `download()/inline()/dynamic()/embed()/prerender()/locked()`. NonContentNode modules alias download/inline→dynamic. `display=null` ⇒ prerender exit; `display` drives session `display_kind`.
- **NEW:** No display-mode at all in the render API; `RenderModule.handle(node)` returns links/jobId regardless. There is a vestigial `PRERENDER` event (only affects tracking, M H5). The OLD `DISPLAY_MODE_LOCKED` (`Config::set('locked', true)`, only in audio/video) and `embed` variants have no analog.
- **Impact:** The consumer presumably chooses presentation from the returned links, so most modes are moot. But `prerender` (warm-the-cache without rendering) and `locked` (content still converting) were *functional* signals. NEW conveys "still converting" via `jobId` + job status, which is a reasonable substitute — verify the prerender warm-up path exists (a render request that only triggers caching).
- **Recommendation:** Confirm there's a way to "prerender"/warm cache without a full client render, matching OLD `display=null`.

MZ: Display modes have been deprecated.

#### M2. Display/render request flags (`showMetadata`, `showDownloadButton`, `showDownloadAdvice`, `forcePreview`, `width`, `height`, `backLink`, `base64Preview`) are gone
- **Severity:** Medium
- **OLD:** `index.php` lines 127–141 read these into `Config`; `Module/Base.php` / `ContentNode/Abstract.php` use them to control footer/metadata/preview rendering and dimensions; `ESObject::getPreviewUrl()` honors `Config::get('base64Preview')`.
- **NEW:** None present (grep: zero hits). `RenderDataRequest` carries only `nodeId, repoId, securedNode, signature, signatureAlgorithm, eventType`.
- **Impact:** Presentation flags — almost certainly **intentionally dropped** (frontend decides). Listed for completeness; low real risk but confirm the frontend no longer expects the service to honor `forcePreview`/`showMetadata`.

MZ: This is now part of the web component.

#### M3. Metadata handler reduced to passthrough — fine, but note `metadataHTML` source
- **Severity:** Medium → Info
- **OLD:** `ESRender/Metadata/Handler.php render()` renders templates using `data.metadataHTML` (server-supplied HTML), title, previewUrl. The metadata HTML is produced by the *repository* and passed through.
- **NEW:** No metadata rendering in the service; presumably the frontend uses repo metadata directly. `application/esmain/metadata.php` (the app **registration** metadata XML, different thing) **is** reproduced by `edusharingRepo/services/MetadataService.generateMetadataFile()` — that part is well covered (appid/type/public_key/host/port/contenturl/trustedclient, plus added `host_allow_internal_ip`, `webappname`, `allow_admin_login`). OLD `type` = (from config) vs NEW hardcodes `RENDERINGSERVICE_2` — intended version bump.
- **Recommendation:** None beyond confirming the frontend renders metadata. Good parity on the registration-metadata file.

MZ: This is now part of the web component.

#### M4. Outbound tracing-header propagation differs
- **Severity:** Medium
- **OLD:** `Helper/GuzzleHelper.php addTracing()` copies inbound `X-B3-*`, `X-OT-*`, `X-Request-Id`, `X-Client-Trace-Id` headers onto every outbound content fetch (manual B3 propagation), plus per-request proxy selection (`ProxyHelper`).
- **NEW:** Tracing is via Micrometer/OTel (see user memory `boot4-tracing-wiring.md`) and `edusharingRepo/TracePropagatingInterceptor.kt`. Verify it propagates the same span context to the **content-fetch WebClient** (`ContentTransferService` uses `repoRegistrationService.getWebClientByRepoId`). The CLAUDE memory notes "no WebClient autoconfig" — so the trace interceptor must be wired onto that builder explicitly.
- **Recommendation:** Confirm `TracePropagatingInterceptor` (or the OTel WebClient instrumentation) is attached to the `WebClient.Builder` used by `RepositoryRegistrationService.getWebClient()` and the per-user client; otherwise outbound spans/trace headers to the repo are lost (regression vs OLD manual B3). Also confirm proxy config parity (`ProxyHelper`).

MZ: Tracing is now handled differently.

#### M5. Content-fetch signed payload parity — OK, but algorithm source differs
- **Severity:** Medium → Info (parity mostly OK)
- **OLD:** `ESRender/Module/ContentNode/Abstract.php createInstance()`: signs `objectID + timestamp` with the **home app private key** (`openssl_sign`, default SHA1), URL-encodes base64, GETs `<repo>/content?repId&appId&nodeId&timeStamp&authToken[&version]`. `version` omitted when `ccm:revoked`.
- **NEW:** `edusharingRepo/services/ContentTransferService.getAsInputStream()`: signs `nodeId + timeStamp` via `EncryptionService.sign(sigData, repoId)`, adds `repId, appId, nodeId, timeStamp, authToken(url-encoded base64), signedAlg, version`. Adds `signedAlg` query param (good — explicit). **Does not** omit `version` for revoked nodes (NEW has no revoked handling at all — see H1). Empty-response handling added (good).
- **Impact:** Mostly equivalent and arguably improved. The revoked-version omission is the only concrete behavioral gap (tied to H1).
- **Recommendation:** Decide whether `version` should still be dropped for revoked nodes; otherwise revoked-content fetch URL differs from OLD.

MZ: This is fine.

#### M6. Error handling / i18n fallback simplified
- **Severity:** Medium → Low
- **OLD:** `index.php` has a rich catch ladder mapping each exception type to a localized template (`/error/default`) with an `i18nName` (`invalid_parameters`, `encryption`, `internal`, …). `init-language.php` picks locale from `language` param, **falls back to DE** if the locale file is missing (note: index.php fallback is DE, not EN, even though default param is `en`).
- **NEW:** `core/exception/ApiExceptionHandler.kt` (+ `ErrorStrings.kt`, `PublicApiException`, etc.) returns JSON error bodies. There is no per-language localization of error text in the service; i18n is the frontend's job.
- **Impact:** Expected for a JSON API. Confirm the frontend maps the new error codes/exceptions to the OLD `i18nName` keys so localized messages survive. The OLD DE-fallback quirk is irrelevant now.
- **Recommendation:** Ensure `ObjectTypeNotSupportedException`, signature failures, etc. carry stable machine-readable codes the frontend can localize (the OLD `i18nName` set: `invalid_parameters`, `encryption`, `internal`).

MZ: This is fine. Handled in FE.

---

### LOW / INFO findings

#### L1. Auto-generation of app SSL keypair on first metadata request
- **Severity:** Low/Info — parity OK
- **OLD:** `application/esmain/metadata.php` + `Helper/AppPropertyHelper.php`: if `public_key` empty, generate an OpenSSL keypair and persist into `homeApplication.properties.xml`.
- **NEW:** `edusharingRepo/services/MetadataService.generateApplicationKeyPair()` (RSA 2048) + `hasKeyPair()`; stored in Mongo `RendererKeyConfig`. Triggered via registration flow. Parity good; keypair now 2048-bit RSA explicitly (OLD used OpenSSL defaults). Confirm a startup/registration path calls `generateApplicationKeyPair()` when `!hasKeyPair()` (analogous to OLD's lazy generation) — see `RegistrationRunner.kt`.

MZ: This is fine.

#### L2. Signature `sig_token` path (no-node signing) not reproduced
- **Severity:** Low
- **OLD:** `validate_signature.php` supports signing a standalone `sig_token` (≥32 chars) when no node is present (used by `version.php` and other non-node endpoints).
- **NEW:** The render API always has a node. The `version.php`/licenses endpoint (third-party dependency listing) has no NEW analog beyond actuator/`/ping`. Likely intentional; the OLD version endpoint required a valid signature to expose dependency licenses — confirm the NEW info endpoints don't need equivalent gating.

MZ: This is fine.

#### L3. Validators (ObjectId/SessionId/etc. regexes) — relaxed
- **Severity:** Low
- **OLD:** `ESRender/Validator/*` enforce regexes on `display`, object-id, session-id, resource-id, application-id (e.g. object-id `^[a-z0-9\-]+$`). `DisplayMode` validator even has a copy-paste bug (lists DYNAMIC twice, omits EMBED/PRERENDER/LOCKED — so those modes would *fail* validation if it were applied; in practice it isn't wired into index.php).
- **NEW:** `RenderDataRequest` fields are `@NotNull` only — no format validation on `nodeId`/`repoId`. Spring Security + signature verification are the real guards. Low risk (IDs flow into parameterized Mongo/HTTP, not SQL), but stricter input validation was present OLD-side.
- **Recommendation:** Optionally add `@Pattern` validation on `nodeId`/`repoId` to match OLD constraints (defense in depth).

MZ: Check this! Might be a useful safeguard! Prio 2.

#### L4. Plugin lifecycle hooks (pre/post LoadRepository, SslVerification, RetrieveObjectProperties, Instanciate, Process, TrackObject) — dropped
- **Severity:** Low/Info — likely intentional
- **OLD:** `ESRender/Plugin/Interface.php` + `Abstract.php` define ~16 lifecycle hooks; `index.php` invokes them around each stage; concrete plugins (`Sodix`, `DDB`, `Serlo`, `Omega`, `Arix`, `NetMath`, `UniTube`, `SetDefaultUsername`, `Edunex`) customized rendering per integration. Loaded from `conf/plugins.conf.php`.
- **NEW:** No plugin hook system. Per-integration behavior is instead a first-class *module* (`sodix`, `ddb`, plus `onyx`, `binder`, `eduhtml`, `jupyter`, `thirdParty`). This is a cleaner design; the extensibility surface changed from "hooks" to "modules". Confirm every OLD plugin's behavior that mattered (e.g. Sodix/DDB request rewriting, SetDefaultUsername, Omega/Arix/NetMath/UniTube/Serlo/Edunex remote-object handling) is either ported into a module or deliberately dropped. **Several OLD plugins (Serlo, Omega, Arix, NetMath, UniTube, Edunex, SetDefaultUsername) have no obvious NEW counterpart** — needs an explicit port/drop decision.

MZ: This is fine. Plugins are now modules.

#### L5. CORS hardening (improvement, note for parity)
- **Severity:** Info
- **OLD:** `application/esmain/index.php` sends `Access-Control-Allow-Origin: *` + `Allow-Methods/Headers: *` unconditionally (wide open).
- **NEW:** `security/cors/*` + `edusharingRepo/cors/*` sync allowed origins per registered repository (`CorsSyncService`, `CorsAllowedOriginsReceiver`). This is a hardening improvement, not a regression. Confirm legitimate repo origins are correctly synced so embeds don't break.

MZ: This is fine.

---

### Summary of what to action first
1. **C1 + C2 (signature):** re-bind a timestamp/nonce into the signed node payload, re-add freshness window, and stop trusting client-supplied `signatureAlgorithm` (resolve from registration). These are real security regressions.
2. **H1 + H2 (dispatch + license gating):** port or explicitly drop each OLD `setModule()` route (revoked→doc, collection/directory, saved_search, lti, url/youtube/pixabay/learningapps/dmglib/FWU, qti, scenario) and re-add a generic doc/download fallback instead of throwing; confirm the JWT `ReadAll` reproduces the collection_io_reference/accessEffective license rules.
3. **C3 (job dedup/locking):** confirm idempotency under concurrent identical requests and that missing-cache self-healing exists per module.
4. **H3/H4/H5/M-series:** confirm these are intentional relocations to the frontend/repo, and document them — especially tracking-when-security-disabled (H5) and outbound trace propagation on the content-fetch WebClient (M4).
5. **L4 (plugins):** decide port-vs-drop for OLD plugins with no NEW module (Serlo/Omega/Arix/NetMath/UniTube/Edunex/SetDefaultUsername).

> Items marked "verify"/"needs verification" were not fully traceable from the files in scope (per-module S3 cache-hit logic, repo-side JWT permission computation, RegistrationRunner keypair bootstrap, WebClient trace wiring). They are flagged rather than asserted.

---

# §2 — Image & Audio/Video modules


Audit of the PHP→Kotlin rewrite for the **image** and **audio/video** rendering modules.
Old tree: `/home/marian/testCheckouts/edu-sharing-community-services-rendering/service/src/main/php/`
New tree: `/home/marian/development/rendering2/services-rendering-service2/service/src/main/kotlin/org/edu_sharing/rendering/modules/`

Key old parameter sources:
- `func/classes.new/ESRender/Module/AudioVideo/Converter.php` — the ffmpeg command lines.
- `func/classes.new/ESRender/Module/AudioVideo/Helper.php` — `checkResolution`, conversion-progress parsing.
- `func/classes.new/ESRender/Module/AudioVideo/Abstract.php` — queue/process orchestration.
- `install/_tmpl/conf/audio-video.conf.php` — `AUDIO_FORMATS=['mp3']`, `VIDEO_FORMATS=['mp4','webm']`, `VIDEO_RESOLUTIONS=['240','720','1080']`, `VIDEO_DEFAULT_RESOLUTION='720'`, `FFMPEG_THREADS=1`, `FFMPEG_EXEC_TIMEOUT=3600`.
- `modules/picture/mod_picture.php` — image resize/convert.

New parameter source: `service/src/main/resources/application.properties` lines 119-136.

---

### IMAGE

#### [Critical] WebM video output format dropped entirely
- **OLD**: `Converter.php:142-153` + `audio-video.conf.php` `VIDEO_FORMATS = ['mp4','webm']`. The old service produced **both** an H.264/AAC `.mp4` **and** a VP8/Vorbis `.webm` per resolution (libvpx, `-crf 40 -b:v 0 -deadline realtime -cpu-used 8 ... -c:a libvorbis -b:a 128k`). `getVideoFormatByRequestingDevice()` (Abstract.php:29) selected mp4 vs webm by the `videoFormat` request param, and the `<video>` template offered both `<source>`s as fallbacks.
- **NEW**: `application.properties:125` `app.converter.video.format=mp4` (single scalar). `VideoConversionService` only ever encodes `video/$videoFormat`. No webm path, no libvpx, no per-device format selection.
- **Impact**: Browsers that only play VP8/WebM lose a fallback. The comment in the old conf ("select mp4 & webm OR only mp4") shows webm was an intentional, configurable second output.
- **Recommendation**: Confirm with product whether webm is still required (modern browsers all play H.264, so this may be a deliberate simplification). If dropped intentionally, note it; otherwise the libvpx parameter set above must be reproduced.

MZ: Checked. All modern browsers are fine with MPEG-4/H.264 [Can I use mp4](https://caniuse.com/mpeg4)

#### [High] Image target resolutions changed (640/1280/1920 + original → 800/1280/1920)
- **OLD**: `mod_picture.php:35-37,96` resolutions `S=640, M=1280, L=1920`, **plus `$origLong`** (the original's longest side) added to the loop — so the original full-resolution copy is always kept (subject to no-upscale).
- **NEW**: `application.properties:121` `app.converter.image.sizes=800,1280,1920`. Smallest is **800 not 640**, and there is **no "original size" output** — `ImageService.targetImageSizes` is a fixed list; `getMissingQualities` only ever considers those three.
- **Impact**: (a) the 640 thumbnail tier is gone; (b) high-res originals larger than 1920 are downscaled to 1920 with no full-res cached copy. Old behaviour preserved an original-resolution rendering.
- **Recommendation**: Decide whether the lost "original" tier and the 640→800 change are intended; if not, add `$origLong` equivalent (cap at original longest side) and restore 640.

MZ: Image resolutions have been deliberately changed. Steffen's tests account for this.

#### [High] EXIF orientation / auto-rotation handling MISSING
- **OLD**: `mod_picture.php:120-137` reads EXIF (`exif_read_data`, IFD0 `Orientation`) and rotates the output: orientation 3→180°, 6→-90°, 8→90°.
- **NEW**: MISSING. `ImageConversionService.convert` uses `ImageIO.read` + `getScaledInstance` and never inspects EXIF. `javax.imageio` does **not** auto-apply EXIF orientation.
- **Impact**: Photos shot in portrait on phones (very common, EXIF orientation 6/8) will render **sideways/upside-down** in the new service. This is exactly the "opaque" logic the audit targets.
- **Recommendation**: Add EXIF-orientation reading (e.g. metadata-extractor) and rotate the BufferedImage before scaling, replicating the 3/6/8 cases.

MZ: Implemented (metadata-extractor)

#### [High] SVG and GIF pass-through MISSING (and not handled by the IMAGE module at all)
- **OLD**: `mod_picture.php:55-63,236-242` — SVG and animated GIF are detected by `mime_content_type` and **copied verbatim** (no rasterization, preserving vector/animation). `getFlavour` returns `''` for svg/gif so the original is always served.
- **NEW**: `app.converter.image.mimeTypes` (line 120) does **not** include `image/svg+xml` or `image/gif`. So per `ModuleTypeMapper` (mimeTypePrefix `image`) they hit `ImageRenderModule` as *non-conversion* objects → served from cache as-is via `getObjectLinks` non-conversion branch. That is functionally equivalent to the old "copy verbatim" **only if** the un-converted original is cached and served. Worth verifying: old explicitly forced no resize; new relies on the "not in mimeTypes ⇒ pass through" rule. GIF animation and SVG would be preserved by that path. **Lower-confidence** — likely OK but confirm a GIF/SVG actually round-trips (the non-conversion branch returns the stored object link, which should be the original bytes).
- **Recommendation**: Add an integration test for `image/gif` and `image/svg+xml` confirming the original (un-rasterized) bytes are served.

MZ: SVG and GIF are handled fine, as confirmed by Steffen's tests.

#### [Medium] JPEG sources additionally written as PNG dropped; output format unified to JPEG
- **OLD**: `mod_picture.php:146-153` — for JPEG sources it wrote **both** a `.jpeg` and a `.png` per size; everything else (png/webp/bmp) written as PNG. Alpha preserved (`imageSaveAlpha`, `imageAlphaBlending(false)`).
- **NEW**: All conversion outputs are a single `app.converter.image.format=jpeg` (line 119), written as `BufferedImage.TYPE_INT_RGB`.
- **Impact**: (a) **Transparency is lost** — PNG/WebP with alpha get a black/opaque background on `TYPE_INT_RGB` (old code preserved alpha). (b) The dual jpeg+png output for JPEG sources is gone (probably fine). The alpha loss is the real concern.
- **Recommendation**: For source formats with alpha, output PNG/WebP (or use `TYPE_INT_ARGB` + a transparency-preserving format) rather than forcing JPEG-on-RGB.

MZ: This is fine. If the customer wants transparency, they can simply remove the mimetype from the conversion list.

#### [Medium] Pixabay zero-byte remote fallback to thumbnailurl MISSING
- **OLD**: `mod_picture.php:228-232` (`createInstance`) — if the node is a remote `PIXABAY` repo and the downloaded file is 0 bytes, it falls back to `properties['ccm:thumbnailurl'][0]` as the image source.
- **NEW**: MISSING. No Pixabay-specific zero-byte handling in the image module or mapper.
- **Recommendation**: If Pixabay remote repos are still in scope, port this fallback; otherwise confirm Pixabay no longer routes through IMAGE.

MZ: Pixabay is handled in FE

#### [Medium] Image scaling quality: `Image.SCALE_DEFAULT` vs PHP `imagecopyresampled`
- **OLD**: `imagecopyresampled` = bicubic-ish high-quality resampling.
- **NEW**: `getScaledInstance(..., Image.SCALE_DEFAULT)` (ImageConversionService.kt:35) — `SCALE_DEFAULT` picks `SCALE_REPLICATE`/area-averaging, generally lower quality than a proper `Graphics2D` bilinear/bicubic with `RenderingHints`.
- **Recommendation**: Consider `Graphics2D.drawImage` with `VALUE_INTERPOLATION_BILINEAR/BICUBIC` for visual parity. Low-confidence visual nit.

MZ: Quality seemed fine so far, to be considered if need be.

---

### AUDIO / VIDEO — ffmpeg / codec parameters (opaque, audit core)

#### [Critical] VIDEO bitrate/scaling expression differs — old used `crf 24 -preset veryfast` with a width-rounding scale filter; new uses jave VideoSize with no explicit even-width guarantee parity check
- **OLD video mp4** (`Converter.php:133`):
  `ffmpeg -i SRC -f mp4 -vcodec libx264 -threads N -crf 24 -preset veryfast -vf "scale=-2:'min(RES,if(mod(ih,2),ih-1,ih))'" -c:a aac -b:a 160k OUT`
  Note: scales by **height** (`-2:RES`, width auto, even), codec **libx264**, CRF **24**, preset **veryfast**, audio **aac @ 160k**.
- **NEW video** (`VideoConversionService.kt`): codec `libx264`, CRF `24`, preset from `app.converter.video.ffmpegPreset=veryfast`, `VideoSize(targetWidth, targetHeight)` where `targetWidth = (targetResolution*ratio)` decremented to even (lines 104-106). Threads via `-threads` global arg (`ffmpegThreads=0` ⇒ ffmpeg auto, **old default was 1**).
- **DIFFERENCE — audio codec**: OLD mp4 audio = **AAC** (`-c:a aac -b:a 160k`). NEW video audio = **`libmp3lame` @ 160000** (`VideoConversionService.AUDIO_CODEC="libmp3lame"`, line 41). **An MP4 container with an MP3 audio track is unusual / poorly supported on some players** (Safari/iOS in particular often refuse MP3-in-MP4). This is a concrete, easy-to-miss regression.
- **Recommendation**: Change video audio codec to AAC to match old behaviour and MP4 norms, or verify jave produces a playable MP4 with mp3 audio across target browsers.

MZ: Fixed and tested, we now use AAC.

#### [High] Threads default changed 1 → 0 (auto)
- **OLD**: `FFMPEG_THREADS=1` default (`Converter.php:39-43`, conf default 1) — single-threaded, deliberate to limit CPU per conversion in a queue.
- **NEW**: `app.converter.video.ffmpegThreads=0` (auto / all cores).
- **Impact**: A single conversion can now saturate the host; old design serialized at 1 thread. May be intentional (jobs are queued one-at-a-time per converter) but is a behavioural change.
- **Recommendation**: Confirm desired concurrency model; document the change.

MZ: Thread count is fully configurable for each deployment.

#### [High] FFMPEG execution timeout / stuck-conversion handling MISSING
- **OLD**: `FFMPEG_EXEC_TIMEOUT=3600` wraps every ffmpeg call in `timeout 3600` (`Converter.php:46-49,133,145`), and `markStuckConversions()` (Converter.php:238-260) re-queues conversions stuck in PROCESSING past the threshold (`CONVERSION_STATUS_STUCK`), with `--retry-stuck`/`--restart`/`--retry-failed` CLI recovery modes.
- **NEW**: No per-encode timeout. jave `Encoder.encode` runs unbounded. No "stuck job" detection/requeue. Failure path only sets `SubJobStatus.FAILED` on an exception (`AvReceiver.kt:92-104`); a hung ffmpeg subprocess would block the converter indefinitely.
- **Recommendation**: Add an encode timeout (jave supports a process monitor / you can kill the subprocess) and a stuck-job sweeper analogous to `markStuckConversions`.

MZ: implemented.

#### [High] Resolution set mismatch: old 240/720/1080, new 480/720/1080
- **OLD**: `VIDEO_RESOLUTIONS = ['240','720','1080']`, default 720 (`audio-video.conf.php`).
- **NEW**: `app.converter.video.resolutions` = **480**/720/1080 (priorities 2/1/0). The smallest tier moved 240→480.
- **Impact**: Low-bandwidth 240p tier removed; minimum is now 480p. Also note the old `min()` resolution feeds the "lock screen" log filename (`mod_video.php:179`) and the "lowest first" queue ordering (`Converter.php:199` ORDER BY resolution ASC); new uses priority-based ordering (`VideoRenderModule.createConversionSubJobs` sorts by priority desc; audio priority hard-coded 255). Behaviourally similar (smallest/most-important first) but the tier values differ.
- **Recommendation**: Confirm 240→480 is intended.

MZ: This is intended and accounted for by Steffen's tests.

#### [High] "No upscaling" rule — semantics differ at the boundary
- **OLD**: `Helper::checkResolution` (Helper.php:52-71): if `source_height >= conversion_resolution` ⇒ convert. Otherwise walk `VIDEO_RESOLUTIONS` ascending and allow conversion only for the **first resolution >= source_height** (i.e. produce exactly one "capped" rendering at the next tier up), else reject. So a 500px-high source with tiers 240/720/1080 yields a **720** rendering (next tier ≥ source), not a 500px one.
- **NEW**: `VideoConverterConfig.getPossibleResolutions` (lines 18-23): if `originalHeight < minResolution` ⇒ return **`[originalHeight]`** (convert at the *source's own* height); otherwise return all tiers `<= originalHeight`. And `VideoConversionService.calculateTargetDimensions` (lines 98-103): if `originalHeight < targetResolution` then only allow it when `targetResolution == minResolution` and encode at **original** dimensions (`recheckStorage=true`), else throw `ConversionException("No Upscaling")`.
- **Impact**: For a source *between* tiers (e.g. 500px with min 480): OLD would render at the **next tier up (720, upscaled-ish via cap logic)**; NEW renders 480 (≤500) and, for the sub-min case, at native height. The two strategies diverge on which renditions exist. New is arguably more correct (never upscales), but it is **not** a faithful port of `checkResolution`. Reviewers relying on old behaviour (a guaranteed ≥-source tier) should be aware.
- **Recommendation**: Document the deliberate change from "round up to next tier" to "never upscale, cap at source". Verify no client assumes a fixed tier always exists.

MZ: This is fine, the upscaling behaviour has been deliberately changed.

#### [Medium] AUDIO: sample rate / channels now forced; bitrate units identical
- **OLD audio mp3** (`Converter.php:115`): `ffmpeg -i SRC -f mp3 -y OUT` — **no** `-ar`/`-ac`/`-b:a` flags at all; ffmpeg defaults are used (it copies/derives sample rate & channels and picks a default bitrate). i.e. the old service did **not** pin bitrate, sample rate, or channels for audio.
- **NEW** (`AudioConversionService`): codec `libmp3lame`, **bitrate forced to 160000** (`app.converter.audio.bitrate`), **channels forced to 2**, **sampling rate forced to 44100**.
- **Impact**: Mono sources are now upmixed to stereo; non-44.1kHz sources resampled; bitrate pinned. Output differs from old (which preserved source characteristics). Generally acceptable/standardizing, but it is a behavioural change and the forced stereo could bloat mono podcasts.
- **Recommendation**: Confirm forcing 2ch/44.1k/160k is intended; old behaviour was "let ffmpeg decide".

MZ: This is fine for now.

#### [Medium] AUDIO output format: old `mp3` matches; new MIME `audio/mpeg` matches conf — OK
- OLD `AUDIO_FORMATS=['mp3']`; NEW `OUTPUT_FORMAT="mp3"`, `MIME_TYPE="audio/mpeg"`. Consistent. Info-level only.

MZ: This is fine. Audio is properly detected and handled.

#### [Medium] Conversion-progress parsing reimplemented differently (regex log scrape → jave listener)
- **OLD**: `Helper::getConversionProgress` (Helper.php:5-50) scrapes the ffmpeg log file with regexes (`Duration:`, `time=... bitrate`) to compute a 0-100% progress shown on the "lock" screen; queue position also surfaced (`getPositionInConversionQueue`).
- **NEW**: `AvConversionListener` (jave `EncoderProgressListener`) writes `subJob.progress = p0/10` (0-10 scale, only every 10%) — see AvConversionListener.kt:27-33. Different granularity (0-10 vs 0-100) and the value is persisted on the SubJob rather than parsed from a log.
- **Impact**: Frontend progress semantics change (0-10 vs 0-100). Queue-position display may or may not exist in new UI. Verify the admin/job UI expects the new scale.
- **Recommendation**: Confirm consumer of `subJob.progress` expects 0-10; otherwise scale to 0-100.

MZ: This is fine.

---

### SERVING / REDIRECT HEADERS

#### [Medium] HTTP Range serving — present in new AssetService, verify edge cases
- **OLD**: `modules/{audio,video}/redirect_header.inc.php` implement manual `Accept-Ranges`, `Content-Range`, `206 Partial Content`, 8KB chunked streaming, `416` on malformed/multibyte ranges, and `Content-Disposition: attachment` for DOWNLOAD mode. Video version also sets `Access-Control-Allow-Origin: *` on ranged responses (audio version does not).
- **NEW**: `asset/AssetService.kt` + `AssetController.kt` provide Range support and CSP/Content-Disposition (per service CLAUDE.md and grep hits for `Range`/`Content-Disposition`/`Accept-Ranges`). Not line-compared here.
- **Recommendation**: Verify (a) `416` is returned for unsatisfiable ranges, (b) multi-range requests are rejected/handled, and (c) the `Access-Control-Allow-Origin: *` that old video ranged responses emitted is reproduced if any embed/CORS scenario depends on it. The old audio vs video CORS inconsistency suggests checking what the frontend actually needs.

MZ: Check this! Prio 2.

#### [Low] DOWNLOAD mode redirect (303 See Other) — base class
- **OLD**: `ContentNode/Abstract.php:123-133` `download()` issues `303 See other` to the asset path with session+token. New service uses signed asset links / its own auth; not a direct port concern but note the old token/session URL scheme is replaced by JWT-signed links.

MZ: This is fine.

---

### Summary of the most important (non-obvious) regressions

1. **EXIF orientation rotation lost** (image) → portrait phone photos render sideways. *(High)*
2. **MP4 audio codec changed AAC→MP3** (video) → MP4+MP3 unreliable on iOS/Safari. *(Critical/High)*
3. **WebM video output dropped** entirely (was a configured second format). *(Critical — verify if intentional)*
4. **Image alpha/transparency lost** (forced JPEG / `TYPE_INT_RGB`). *(Medium-High)*
5. **No ffmpeg/encode timeout + no stuck-job requeue** (old had `timeout 3600` + STUCK recovery). *(High)*
6. **Resolution tiers shifted** video 240→480, image 640→800, and **original-resolution image copy dropped**. *(High)*
7. **"No-upscale" semantics changed** from "round up to next tier" to "cap at source/never upscale". *(High — likely intentional, document it)*
8. **Audio now forced 2ch/44.1kHz/160k** vs old ffmpeg-defaults. *(Medium)*
9. **Pixabay zero-byte→thumbnailurl fallback lost**. *(Medium)*
10. **Progress scale 0-100 → 0-10**; verify UI. *(Medium)*

All ffmpeg flags above quoted verbatim from `Converter.php`; new equivalents from `VideoConversionService.kt` / `AudioConversionService.kt` + `application.properties`.

---

# §3 — Document, Office, Spreadsheet, QTI/Onyx & Jupyter modules


Parity audit of the PHP → Kotlin/Spring-Boot rewrite for the document/office/spreadsheet,
QTI(Onyx) and Jupyter rendering paths.

### Sources read

OLD (PHP):
- `modules/doc/mod_doc.php`, `modules/doc/config.php.example` (empty)
- `modules/office/mod_office.php`
- `modules/office_spreadsheet/mod_office_spreadsheet.php`
- `modules/qti/mod_qti.php`, `modules/qti21/mod_qti21.php`, `modules/qti21/config.php.example`
- `func/classes.new/ESRender/Module/ContentNode/Abstract.php`
- (confirmed: **no** old `.ipynb`/`nbconvert`/`jupyter` module exists anywhere in the PHP tree — Jupyter is genuinely new)

NEW (Kotlin / service):
- `modules/document/{DocumentRenderModule,SpreadsheetRenderModule,DocumentConversionService,DocumentService,DocumentConverterConfig,DocumentReceiver,DocumentModuleTypeMapper}.kt`
- `modules/onyx/{OnyxRenderModule,OnyxReceiver,OnyxUploadService}.kt`
- `modules/jupyter/{JupyterRenderModule,JupyterConversionService,JupyterJobService,JupyterReceiver,JupyterConverterConfig,JupyterRenderModuleTypeMapper}.kt`
- `modules/ConverterWebServiceCaller.kt`, `modules/ConverterWebServiceArguments.kt`
- `asset/AssetController.kt` (PDF download-right gating + CSP)

NEW converters:
- `document-converter/.../{ConversionController,ConversionService,ConverterConfig}.kt` + `application.properties`
- `jupyter-converter/src/main/python/main.py`

---

## Key architectural difference (context for the findings)

The OLD service rendered **HTML page output** itself (templates per doctype: pdf/odf/html, dynamic/embed/inline) and did the conversions **in-process with PHP libraries** (PhpWord, PhpPresentation, PhpSpreadsheet, dompdf). The NEW service does **not** render HTML pages at all — it converts to a target file (PDF/HTML) via the standalone `document-converter` (jodconverter/LibreOffice) and Jupyter converter, stores the result in S3, and serves the asset bytes; the *display chrome* now lives in the edu-sharing frontend. So "missing template/inline/embed/dynamic" is by design and is **not** flagged below. The findings concentrate on **opaque conversion/format/permission/URL logic** that materially changes behavior.

---

## Findings

### Critical

#### C1. PowerPoint / presentation rendering silently dropped
- **Severity:** Critical
- **OLD:** `modules/office/mod_office.php` (lines 61-70, 78-82, 100-117). PPTX (`...presentationml.presentation`) was converted to ODP via `PhpPresentation` (`createWriter(..., 'ODPresentation')`); ODP (`...opendocument.presentation`) was renamed/passed through as ODP; PPT (`application/vnd.ms-powerpoint`) was explicitly handled (set to `DOCTYPE_UNKNOWN`, i.e. download fallback — "we can't convert them"). `canProcess` listed presentation types (commented out for VIEWER_JS but reader/writer logic existed).
- **NEW:** `DocumentModuleTypeMapper.kt` maps PPT and PPTX → `documentRenderModule`, whose `getTargetMimetype()` is **always `application/pdf`** (`DocumentRenderModule.kt:39`). The `document-converter` lists `ppt,pptx,...,odp` in `app.supportedExtensions`, so LibreOffice *can* convert PPTX/ODP/PPT → PDF. So presentations are now converted to **PDF**, not ODP.
- **Assessment:** This is actually a **behavioral improvement** (PPT was unrenderable in OLD; now all presentation formats become PDF). BUT note the **output format changed from ODP to PDF** for PPTX/ODP. If any downstream/front-end logic still expects the `_converted.odp` presentation viewer path, presentations will break. Verify the frontend treats PDF output for presentations correctly.
- **Recommendation:** Confirm frontend presentation handling expects PDF. If an ODP/impress.js-style player is still wired, this is a real regression; otherwise downgrade to Info.

MZ: This is intentional.

#### C2. Document HTML output is no longer sanitized (HTMLPurifier dropped)
- **Severity:** Critical (security)
- **OLD:** `mod_doc.php` `createInstance()` (lines 112-127) ran **HTMLPurifier** over `text/html` content and stored a `_purified.html`; the rendered content used the purified file (`renderTemplate`, line 88). This was explicit XSS sanitization of user-uploaded HTML before serving.
- **NEW:** **MISSING.** There is no HTML purification anywhere in the document path. For `text/html` / `text/plain` source the NEW `DocumentModuleTypeMapper` does **not even register** `text/html` — only `text/plain` (TXT) and the office/spreadsheet types (see H1). The spreadsheet path produces HTML from jodconverter and the document-converter only adds a `<style>` block (`ConversionService.getStyledHtml`) — **no sanitization**. Jupyter HTML from nbconvert is likewise served unsanitized.
- **Assessment:** For spreadsheet→HTML and Jupyter→HTML output served inline, the only mitigation is the per-repo **CSP header** (`SpreadsheetRenderModule.getCspHeader`, applied in `AssetController.prepareResponse`). CSP is weaker and config-dependent than server-side purification. The OLD explicit purify step is gone.
- **Recommendation:** Confirm a strict CSP is configured for SPREADSHEET (and JUPYTER — see H4) for every repo, or reinstate server-side HTML sanitization in the document-converter / jupyter-converter before storing. Treat nbconvert/LibreOffice HTML as untrusted.

MZ: Check this! Prio 2.

#### C3. Onyx `uniqueId` semantics changed — per-user/course assessment isolation lost
- **Severity:** Critical
- **OLD:** `mod_qti21.php` (lines 89-95) built `uniqueId` = `repo + appId + courseId + authorityName + authorityName (+ authorityName again)`. It deliberately incorporated **app id, course id and the requesting user's authorityName**. `mod_qti.php::refineInstanceConstraints` additionally scoped the cached instance by `LMS_ID`/`COURSE_ID`/`RESOURCE_ID`, requiring course+resource id to be supplied together. This makes each user's (and each course's) Onyx assessment run a **distinct session** — important for test/assessment state isolation and grading.
- **NEW:** `OnyxUploadService.uploadTest` (lines 41, 58) builds `uniqueId = "${nodeId}_${hash}"` — only the node id and content hash. **No user, no course, no app id.** All users sharing the same node+version get the **same Onyx uniqueId** → shared/colliding assessment sessions.
- **Assessment:** For an assessment/QTI player this is a serious correctness/data-isolation regression: different students could land in the same Onyx run session, or per-user attempt state could be conflated. Also the course/resource scoping (`refineInstanceConstraints`) has no equivalent.
- **Recommendation:** Confirm with the Onyx integration whether `uniqueId` must be per-user/per-course. If so, incorporate the authenticated user's authority + course/app id into the uniqueId (the data is available on the session/node permission context). At minimum document why a node-scoped id is now acceptable.

MZ: Check this! Prio 1.

---

### High

#### H1. `text/html` source documents no longer handled at all
- **Severity:** High
- **OLD:** `mod_doc.php::setDoctype` (lines 194-201) recognized `text/html` → DOCTYPE_HTML and `text/plain` → DOCTYPE_TEXT and `application/pdf` → DOCTYPE_PDF, rendering each. So uploaded **HTML files** and **plain-text** and **PDF** were all renderable.
- **NEW:** `DocumentModuleTypeMapper.kt` registers TXT (`text/plain`) and the office types but **not `text/html`** and **not `application/pdf`**. So:
  - `text/html` source → falls through `ModuleRegistry` to the `text/*`-prefix or mimetype fallback (if any) or `ObjectTypeNotSupportedException`. There is no DOCUMENT handling.
  - `application/pdf` → handled elsewhere (PDFs are served directly as assets; the document module only *produces* PDFs). This is consistent with the new design (PDF is a passthrough asset, see `AssetController` PDF branch) — **OK**.
  - `text/plain` (TXT) is mapped to `documentRenderModule` whose target is **PDF**, i.e. TXT now gets converted to PDF by LibreOffice rather than shown as `nl2br(htmlentities(...))` text. Behavior change but acceptable.
- **Assessment:** The real gap is **`text/html` source files**: OLD rendered (purified) HTML; NEW has no DOCUMENT registration for it. Whether this matters depends on whether `text/html` is handled by another module (e.g. an `eduhtml` module — see package list). 
- **Recommendation:** Verify `text/html` nodes are routed to a module (eduhtml/html) in the new system; if not, this is a lost capability. Tie in with C2 (sanitization).

MZ: text/html is now handled by the html module.

#### H2. Spreadsheet engine & multi-sheet/format fidelity changed (PhpSpreadsheet → LibreOffice)
- **Severity:** High
- **OLD:** `mod_office_spreadsheet.php` used **PhpSpreadsheet** with explicit per-mimetype readers: Xlsx, Xls, Ods, **Csv** (lines 80-92), writer = `Html`. Each sheet rendered to one combined HTML.
- **NEW:** `SpreadsheetRenderModule` (gated on `app.converter.spreadsheetToHtml.enabled=true`) routes XLS/XLSX/ODS/CSV → document-converter with `format=html` (`DocumentConversionService.convertAndMoveToCache`, lines 56-57). Conversion is now **LibreOffice/jodconverter → HTML**, post-processed by jsoup which only injects a table `<style>` block (`ConversionService.getStyledHtml`).
- **Assessment:** Engine swap → different HTML output, different multi-sheet handling (LibreOffice Calc HTML export vs PhpSpreadsheet), and **CSV is now driven through LibreOffice** rather than PhpSpreadsheet's CSV reader. LibreOffice CSV import applies locale-dependent delimiter/encoding heuristics (the PHP `Csv` reader had its own defaults). Output is no longer a single deterministic HTML table style. **Opaque difference** worth a visual regression test on multi-sheet workbooks and on CSV.
- **Recommendation:** Run regression on a multi-sheet XLSX, an Ods, an Xls, and a CSV (incl. semicolon-delimited / non-UTF8) and compare. Note the spreadsheet module is **optional** (`@ConditionalOnProperty`) — if disabled, XLS/XLSX/ODS/CSV fall back to `documentRenderModule` → **PDF** (`DocumentModuleTypeMapper.kt:42-45`), a different output again.

MZ: This is true. However, this is a price we have to pay.

#### H3. Onyx language hard-coded to "de" (was driven by request language)
- **Severity:** High
- **OLD:** `mod_qti21.php:98` `wrappedParams->language = $LanguageCode;` — the Onyx run language came from the request `$LanguageCode`.
- **NEW:** `OnyxUploadService.kt:42` `builder.part("language", "de")` — **hard-coded German**.
- **Assessment:** Non-German users now get a German Onyx player UI/locale. Clear behavioral regression.
- **Recommendation:** Plumb the request/user language into the Onyx upload instead of literal `"de"`.

MZ: Check this. Onyx module needs care! Prio 1

#### H4. Jupyter HTML served unsanitized & possibly without CSP
- **Severity:** High (security)
- **OLD:** N/A (no old module).
- **NEW:** `jupyter-converter/main.py` uses `nbconvert HTMLExporter` (default full template, includes notebook-authored HTML/Markdown and `<script>` from cell outputs). Output served as the asset. `JupyterRenderModule` does **not override `getCspHeader`** (unlike SpreadsheetRenderModule), so unless a JUPYTER CSP is configured per-repo it serves arbitrary notebook HTML/JS inline.
- **Assessment:** Notebooks routinely contain executable JS in outputs and Markdown HTML — serving nbconvert full-template HTML inline is an XSS surface. nbconvert is not a sanitizer.
- **Recommendation:** Add a CSP for the JUPYTER module (override `getCspHeader`) and/or use a sanitizing nbconvert template; treat notebook HTML as untrusted.

MZ: Check this! Prio 1

#### H5. Source-extension allow-list mismatch: WMF/PPT/RTF/OTT behavior & `application/msword` (DOC)
- **Severity:** High
- **NEW:** `DocumentModuleTypeMapper.kt` registers DOC (`application/msword`) → documentRenderModule (→ PDF). `document-converter` `app.supportedExtensions = doc,docx,ppt,pptx,xls,xlsx,odt,odp,ods,txt,ott,rtf,csv`. The service derives the **upload file extension from the mimetype** via Tika: `DocumentConversionService.getExtensionFromMimeType` → `MimeTypes.getDefaultMimeTypes().forName(mimeType).extension` (lines 69-75).
- **Risk:** Tika's canonical extension for a mimetype may **not match** the converter's allow-list token. E.g. Tika returns extensions **with a leading dot** (`.docx`), whereas the converter compares against bare tokens (`docx`) via `FilenameUtils.getExtension` on the temp filename. Also `application/msword` Tika canonical extension is `.doc` — fine — but `text/plain` → `.txt`, `application/rtf` vs `application/x-rtf`, and OTT (`...text-template`) need verification. The temp file is created with `File.createTempFile(prefix, originalFileExtension)` where `originalFileExtension` may be `.docx` (with dot) → temp filename `..._<hash>.docx.tmp`? Actually `createTempFile` appends the suffix verbatim, and Java's default suffix is `.tmp` only when suffix is null; here suffix = `.docx` so the filename ends `.docx` — but **if Tika returns a dotted extension the multipart filename will contain `.docx` and the converter's `getSourceFormat`→`FilenameUtils.getExtension` yields `docx`** (OK) — *unless* Tika returns multiple/space-separated extensions or an empty string.
- **Assessment:** This is fragile, opaque coupling between Tika's mimetype→extension table and the converter's hard-coded `app.supportedExtensions`. Any mimetype whose Tika extension token isn't in the allow-list throws "is not supported" or "Source extension ... is not supported." OLD used explicit mimetype switches with no such indirection.
- **Recommendation:** Add a test that every mimetype in `DocumentModuleTypeMapper` resolves (via Tika) to an extension present in the converter's `app.supportedExtensions`. Verify dotted-vs-bare extension handling end-to-end.

MZ: Mime and file types have been checked with Steffen. All needed files are covered by automated tests.

---

### Medium

#### M1. Multipart file extension passed without leading dot in callers (temp file naming)
- **Severity:** Medium
- **NEW:** `JupyterConversionService` passes `originalFileExtension = "ipynb"` (no dot) to `ConverterWebServiceCaller`, which does `File.createTempFile(prefix, "ipynb")` → temp file named `prefix...ipynb` (no dot, suffix concatenated). The Jupyter converter (`main.py`) ignores the filename entirely (reads `file.file`), so harmless there. The **document** path passes the Tika extension (which *may* include a dot — see H5) and the document-converter **does** parse the filename extension. Inconsistent handling between callers.
- **Recommendation:** Normalize extension handling (always leading-dot, or always bare) in `ConverterWebServiceCaller` and document the contract.

MZ: This is fine.

#### M2. `requiredCredentialKeys` for Onyx is a single malformed string (validation no-op)
- **Severity:** Medium (bug)
- **NEW:** `OnyxRenderModule.kt:34`:
  `private val requiredCredentialKeys = setOf("onyxresturl, onyxrunurl, returnservice")`
  This is a **set of one string** `"onyxresturl, onyxrunurl, returnservice"`, not three keys. `validateThirdPartyCredentials` therefore checks for a single credential key literally named `"onyxresturl, onyxrunurl, returnservice"`, which will never exist → credential validation is effectively broken / will reject all configs (or, depending on `validateCredentials` semantics, validate nothing meaningfully).
- **OLD:** config required `cfg_onyx_service` (WSDL) and `cfg_onyx_runurl`.
- **Recommendation:** Fix to `setOf("onyxresturl", "onyxrunurl", "returnservice")`. Verify against `validateCredentials` implementation.

MZ: Check this! Onyx module needs care! Prio 1.

#### M3. Onyx transport changed SOAP → REST multipart; `instructions`/`templateId` defaults differ; no zipping
- **Severity:** Medium (Info-ish, verify)
- **OLD:** `mod_qti21.php` used a **SOAP** client (`cfg_onyx_service` WSDL), calling `run(uniqueId, contentPackage, language, instructions, templateId, serviceName, allowShowSolution)` with `instructions = '<html><body><h1>ONYX</h1></body></html>'`, `templateId = 'onyxdefault'`, `serviceName = 'esrender'`, `allowShowSolution = 'true'`. `contentPackage` = **raw file_get_contents** of the QTI zip (no re-zipping). Optional HTTP proxy via `ProxyHelper`.
- **NEW:** `OnyxUploadService` uses **REST multipart POST `/run`** to `onyxresturl`, parts: `contentPackage` (the file), `uniqueId`, `language`, `serviceName` (from `returnservice` credential), `allowShowSolution=true`, `templateId=onyxdefault`. **No `instructions` part** is sent. `serviceName` now comes from config (`returnservice`) instead of hard-coded `'esrender'`. No proxy handling. The content package is the raw downloaded file (no zipping in either version — confirmed).
- **Assessment:** Transport change is presumably intentional (Onyx exposes a REST endpoint now). Two concrete deltas: (a) `instructions` param is dropped — confirm the Onyx REST API doesn't require/expect it; (b) old HTTP-proxy support for the Onyx call is gone.
- **Recommendation:** Confirm the Onyx REST contract doesn't need `instructions`; confirm proxying isn't needed in the new deployment topology.

#### M4. Onyx run URL: missing session param, `urlencode` dropped
- **Severity:** Medium
- **OLD:** `mod_qti21.php:114-115` `oru = cfg_onyx_runurl . "?id=" . urlencode($uniqueId) . "&" . session_name() . "=" . session_id()` — id is **URL-encoded** and the PHP session is appended (needed for the Onyx player to call back into the render session).
- **NEW:** `OnyxUploadService.kt:58` `runUrl = "${onyxrunurl}?id=${nodeId}_${hash}"` — **no URL-encoding** of the id and **no session token**. nodeId/hash are unlikely to contain unsafe chars (so encoding is low-risk), but the absence of any session/auth param on the run URL is a behavioral change — if the Onyx player previously relied on the render session cookie, that linkage is gone.
- **Recommendation:** Verify the Onyx player no longer needs the render session on the run URL. URL-encode the id defensively.

MZ: Check this! REST works fine, but: Onyx module needs care! Prio 1

#### M5. Cache/instance-existence + corrupt-cache self-heal logic not obviously mirrored
- **Severity:** Medium
- **OLD:** `mod_doc.php::instanceExists` (lines 252-301) checked the DB **and** that the cache file actually exists on disk; if the DB row existed but the file was missing it **deleted the DB row and returned false** (self-healing of a stale cache entry). `mod_office`/`mod_office_spreadsheet` overrode `instanceExists` to check for the `_converted.*` file.
- **NEW:** Cache hit detection is `DocumentService.getObjectLinks` → `storageImplementation.getObjectLink(lookUpObject)` catching `ResourceNotFoundException` (S3 lookup of the target-mimetype object). This checks the storage backend directly, so a "DB says yes but file missing" inconsistency is structurally less likely (single source of truth = S3). Reasonable parity, but there's **no equivalent of pruning a stale job/tracking entry** if the S3 object is gone — verify the job/tracking layer can't get wedged on a missing cached asset.
- **Recommendation:** Low-priority; confirm cacheCleaner/tracking handles orphaned entries.

MZ: This is fine.

---

### Low / Info

#### L1. `ENABLE_VIEWER_JS` / `VIEWER_JS_CONFIG` per-repo feature gating replaced by static config
- **Severity:** Low/Info
- **OLD:** Office/spreadsheet `canProcess` was gated by `ENABLE_VIEWER_JS && in_array(module, VIEWER_JS_CONFIG)` and `createInstance` could rewrite module to `'doc'` when viewer-js was off (`Abstract.php:48-56`). This let a repo turn office/spreadsheet rendering on/off and fall back to plain doc/download.
- **NEW:** SPREADSHEET gated by global `app.converter.spreadsheetToHtml.enabled`; fallback when disabled is to `documentRenderModule` (→ PDF), not download. Office is always-on (DOCUMENT). The per-repo viewer-js toggle is gone (now global). Probably intentional given the new architecture.
- **Recommendation:** Confirm no repo relied on disabling office/spreadsheet rendering.

MZ: This is fine.

#### L2. `memory_limit = 4000M` and in-process conversion → now externalized with 128 MB multipart limit
- **Severity:** Info
- **OLD:** `Abstract.php:37` bumped PHP `memory_limit` to 4 GB for in-process conversion.
- **NEW:** Conversion is out-of-process (LibreOffice/jodconverter, nbconvert). Limits are now: document-converter multipart **128 MB**; service outbound `spring.http.codecs.max-in-memory-size=20MB` (`DocumentConverterConfig`/`JupyterConverterConfig` set the WebClient in-memory codec to this). **Note:** the converter accepts 128 MB uploads but the service's WebClient buffers responses in memory capped at **20 MB** — a converted PDF/HTML larger than 20 MB will fail `bodyToMono(ByteArray)` in `ConverterWebServiceCaller` (it buffers the whole response). OLD had no such 20 MB ceiling.
- **Recommendation:** Confirm 20 MB is sufficient for converted outputs (large spreadsheets→HTML or many-page→PDF can exceed it); consider streaming instead of `bodyToMono(ByteArray)`.

MZ: Implemented by SW.

#### L3. No explicit per-conversion timeout configured
- **Severity:** Info
- **NEW:** No soffice/jodconverter task timeout or WebClient response timeout is set in the read configs (`ConverterConfig` builds a default `LocalConverter`; WebClients use builder defaults). A hung LibreOffice subprocess or slow conversion could block. OLD relied on PHP/web-server timeouts.
- **Recommendation:** Set a jodconverter task execution timeout and a WebClient response timeout.

MZ: Implemented by SW.

#### L4. CSV target / `text/csv` and ODT-template (OTT) routing — verify
- **Severity:** Info
- **NEW:** `text/csv` → spreadsheet (HTML) when enabled, else PDF. OTT (`...text-template`) → documentRenderModule → PDF. OLD office module did not handle OTT/CSV at all (CSV was only in spreadsheet via PhpSpreadsheet). New coverage is broader; just confirm OTT/CSV→PDF is desired.

MZ: This is fine.

---

## Summary of severities
- **Critical:** C1 presentation output format change (verify regression), C2 lost HTML sanitization (security), C3 Onyx uniqueId no longer per-user/course (assessment isolation).
- **High:** H1 `text/html` source unhandled, H2 spreadsheet engine swap fidelity, H3 Onyx language hard-coded "de", H4 unsanitized Jupyter HTML w/o CSP (security), H5 Tika-mimetype↔converter allow-list coupling fragility.
- **Medium:** M1 extension-dot inconsistency, **M2 broken Onyx `requiredCredentialKeys` (clear bug)**, M3 SOAP→REST deltas (instructions/proxy), M4 run URL missing session/encoding, M5 stale-cache self-heal.
- **Low/Info:** L1 viewer-js per-repo toggle gone, L2 20 MB WebClient buffer ceiling, L3 no conversion timeouts, L4 CSV/OTT routing.

---

# §4 — H5P module & lumi


Audit of the H5P rendering parity between the OLD PHP rendering service and the NEW Kotlin/Spring-Boot `h5p` module + the `lumi` (Node/TS, `@lumieducation/h5p-*`) component.

### Architecture summary (what changed conceptually)

The OLD service was a **self-hosted H5P installation**. It embedded the official `h5p-core` PHP library (`vendor/lib/h5p-core/*`) and implemented the full `H5PFrameworkInterface` itself (`H5PFramework.php`, 1163 lines). It stored libraries, content, dependencies, languages, and the content-type hub cache in a relational DB (`h5p_libraries`, `h5p_contents`, `h5p_contents_libraries`, `h5p_libraries_libraries`, `h5p_libraries_languages`, `h5p_libraries_hub_cache`; `empty.sqlite` is the seed schema). On render it ran the official `H5PValidator->isValidPackage()`, `H5PStorage->savePackage()`, `H5PCore->filterParameters()` (the official sanitizer), resolved preloaded dependencies, and emitted a hand-built iframe HTML page (`mod_h5p.php::render()`).

The NEW design **delegates all H5P concerns to the `lumi` service** (`@lumieducation/h5p-server`, core 1.28.0 / editor 1.25). The Kotlin module is a thin orchestrator:
- `H5pRenderModule` → returns a cached link or schedules a job.
- `H5pJobService`/`H5pReceiver` → async job that pulls content from the repo and `H5pUploadService` POSTs the `.h5p` to lumi (`POST /edusharing`).
- lumi `router.ts` does `h5pEditor.uploadPackage()` (validation + library install) + `saveOrUpdateContent()`, stores content/libraries in Mongo+S3, and `h5pPlayer.render()` emits the player HTML (`eduSharingPlayer.ts`).
- `LumiProxyController`/`LumiProxyService` proxy player + asset requests from `/public/h5p/**` to lumi, enforcing node read permission and a CSP header.
- `LumiContentManagementService`/`LumiCacheRepository` keep a Redis cache of `nodeId+hash → lumiContentId`.

This is a sound modernization: validation, semantics, dependency resolution, the hub cache, and sanitization that the OLD `H5PFramework` re-implemented by hand are now handled by the maintained `@lumieducation` libraries. Most "missing" framework methods (`getLibraryStats`, `getNumAuthors`, `replaceContentHubMetadataCache`, `getOption`/`setOption`, library upgrade, etc.) were already TODO/no-op stubs in the OLD code and have no functional parity gap. The findings below focus on behavior the OLD code *actually performed* that the NEW path changes or drops.

---

### Findings

#### 1. [High] Lumi `coreApiVersion` / `h5pVersion` are computed with swapped & wrong fields
- OLD: `config.php` sets `H5P_Version = '1.3.0'`; `H5PFramework::getPlatformInfo()` reports it. Core/editor versions came from the bundled vendored lib.
- NEW: `lumi/src/index.ts:72-76`:
  ```ts
  config.coreApiVersion = { major: h5p_core_version_minor, minor: h5p_core_version_major }  // = {major:28, minor:1}
  config.h5pVersion = `${h5p_core_version_major}.${h5p_core_version_major}.${h5p_core_version_patch}` // = "1.1.0"
  ```
  With `h5p.settings.ts` = major 1 / minor 28 / patch 0, `coreApiVersion` becomes **{major:28, minor:1}** (fields swapped — should be {major:1, minor:28}) and `h5pVersion` becomes **"1.1.0"** (uses `major` twice instead of `major.minor.patch` → should be "1.28.0").
- Impact: H5P uses `coreApiVersion` to decide which libraries a package may run against. A bogus `coreApiVersion` of 28.1 means almost any library passes the "core API needed" check (or fails wrongly on a stricter library), and the reported `h5pVersion` is wrong in integration metadata. This is a latent correctness/compatibility bug introduced in the rewrite.
- Recommendation: fix to `coreApiVersion = { major: h5p_core_version_major, minor: h5p_core_version_minor }` and `h5pVersion = `${major}.${minor}.${patch}``. Add a test asserting the values match `h5p.settings.ts`.

MZ: There is no issue, claude found ancient code. 

#### 2. [High] No `.h5p` package size / validation guard on the Kotlin upload path; whole package buffered in memory in lumi
- OLD: `H5PValidator->isValidPackage()` (official validator) ran before save; invalid packages were rejected and the temp folder removed (`mod_h5p.php:90-105`). File-extension whitelisting existed in the framework contract (`getWhitelist`, though the OLD impl returned `''`).
- NEW: `H5pUploadService.uploadPackage()` streams the repo content to a temp file and POSTs it; lumi `router.ts:82` calls `h5pEditor.uploadPackage(request.file.buffer, …)`. Validation is delegated to `@lumieducation` (good), but:
  - lumi uses `multer()` with **no `limits`** (`router.ts:45`), so the entire upload is buffered in memory (`request.file.buffer`). `config.json` `maxFileSize`/`maxTotalSize` = ~1 GB. A large/malicious `.h5p` can OOM the Node process.
  - On the Kotlin side there is no pre-flight size cap either; `H5pUploadService` copies the full stream to a temp file.
- Recommendation: set `multer({ limits: { fileSize: … } })` in lumi aligned with `maxFileSize`, and/or guard the package size in `H5pUploadService` before upload.

MZ: Check this! Make the size configurable! Prio 1

#### 3. [Medium] xAPI statement handling changed: target/object enrichment lost, transport changed
- OLD: `mod_h5p.php:202-231` wired `H5P.externalDispatcher.on("xAPI", onXapi)` and, on each statement, **set `statement.object.id` to the node path and `statement.object.definition.name` to the title**, then POSTed the statement directly to the repo REST endpoint `…/rest/node/v1/nodes/-home-/{nodeID}/xapi` with `withCredentials`. (Note: in OLD it was gated off by `const xapi = false`.)
- NEW: `lumi/src/eduSharingPlayer.ts:22-53` forwards xAPI statements via `window.parent.postMessage({type:'H5P_XAPI', contentId, statement}, '*')`. It does **not** enrich the statement with the node id/title and does **not** POST to the repo — it relies on a parent window listener to do so.
- Impact: behavior is intentionally different (statements bubble to the embedding page instead of being posted server-side). Two concerns: (a) the OLD object-id/name enrichment (so the LRS knows which node/title) is gone — the embedder must now do it; (b) `postMessage(..., '*')` uses a wildcard target origin, which leaks statements to any parent origin. Since OLD had xAPI hard-disabled, this is arguably new functionality rather than a regression, but the enrichment + targeted origin are worth re-adding.
- Recommendation: confirm the edu-sharing embedding page consumes `H5P_XAPI` and performs node/title enrichment + repo POST; restrict the `postMessage` target origin instead of `'*'`.

MZ: XAPI works fine, Moodle is happy. 

#### 4. [Medium] Player display options diverge (copyright/license button, embed/download)
- OLD: `get_content_settings()` (`mod_h5p.php:282-288`) `displayOptions`: frame=true, export=false, embed=false, **copyright=true**, icon=true.
- NEW: `router.ts:122-126` render opts: `showCopyButton:false, showDownloadButton:false, showFrame:true, showH5PIcon:true, showLicenseButton:true`. `eduSharingPlayer.ts:54-58` additionally hides the whole action bar with `ul.h5p-actions { display:none }`.
- Impact: OLD showed a copyright button and frame; NEW sets `showLicenseButton:true` but then CSS-hides `ul.h5p-actions`, so the frame action buttons (license/copyright) are not actually visible to the user despite being enabled. Net effect: copyright/rights-of-use UI that OLD exposed is effectively suppressed in NEW.
- Recommendation: decide intended UX; either drop the `display:none` rule or set `showLicenseButton:false` to avoid the contradictory config. Verify copyright/license info remains reachable (legal/attribution requirement for some content).

MZ: This is fine.

#### 5. [Medium] MathDisplay / LaTeX rendering wiring is narrower in NEW
- OLD: `mod_h5p.php:187-188` injected `window.renderingServiceUrl` and a global `mathdisplay.js` into **every** H5P page, so LaTeX rendered regardless of content type.
- NEW: LaTeX is handled via `config.json editorAddons` which adds `H5P.MathDisplay` only to `H5P.CoursePresentation`, `H5P.InteractiveVideo`, `H5P.DragQuestion`. `editorAddons` apply in the **editor**, and only for those three content types.
- Impact: content types not in that list (e.g. Question Set, Course presentation sub-types, Interactive Book, plain Text-based libs) that previously got global MathDisplay may no longer render LaTeX. Also `editorAddons` affect editing context, not necessarily the player-only path used here.
- Recommendation: confirm whether `addons`/`editorAddons` propagate MathDisplay to the player for all relevant content types; if not, broaden the addon list or use a player-side addon mechanism to match OLD's universal LaTeX support.

MZ: Check this! Test file needed. Prio 2

#### 6. [Medium] OLD "recently modified → bust cache" safeguard has no equivalent
- OLD: `mod_h5p.php::wasObjectLatelyModified()` + `clearPotentiallyBrokenObject()` — if an ESOBJECT was modified within `H5P_DISABLE_CACHE_DELAY` seconds, the cache entry was deleted to force a re-render. This was an explicit workaround for the async repo save handing **unfinished** H5P data to the renderer (see the comment at `mod_h5p.php:494-501`).
- NEW: caching key is `nodeId + content.hash` (`LumiContentManagementService.getContentId`, `LumiCacheRepository.findByNodeIdAndHash`). There is no time-delay invalidation. The assumption is that the content hash changes when content changes, so a stale/partial upload would have a different hash.
- Impact: if the repo can momentarily serve content under a hash that later represents a different/complete payload (the exact race the OLD workaround guarded), the NEW cache could pin a broken render. Likely lower risk because the hash is content-derived, but the OLD authors found this necessary in practice.
- Recommendation: confirm the repo's content hash is computed only over finalized content; if a window exists, consider a short re-validation/TTL for very recently created entries.

MZ: Check this! I hope it will not be needed! Prio 1

#### 7. [Medium] Dummy single user + LaissezFaire permissions in lumi; access control lives only in the proxy
- OLD: framework `hasPermission()` returned `true` (no per-user enforcement) but content was served through the PHP render pipeline behind edu-sharing's session/token checks.
- NEW: lumi uses a hard-coded `User` (`User.ts`, id="1") and `LaissezFairePermissionSystem` (`createH5PEditor.ts:152`) — lumi itself performs **no** authorization. All access control is enforced upstream by `LumiProxyService.processProxyRequest` via `@PreAuthorize("hasPermission(#nodeInfo.nodeId,'ReadAll')")` and the CSP header.
- Impact: this is acceptable **only if lumi is never directly reachable** (must sit behind the Kotlin proxy on a private network). lumi has no auth, so a direct route to `app.lumi.host` exposes all content. Also note `LumiProxyService` copies *all* inbound request headers to lumi (`router`/`processProxyRequest:67-71`) including `Authorization`/`Cookie`, and `LumiContentManagementService` has a `// TODO impl. lumi per repoId!!!` — a single shared lumi instance serves all repos, so per-repo content isolation depends entirely on bucket/collection config, not on lumi.
- Recommendation: ensure deployment network-isolates lumi; document that the `/edusharing/*` and `/:contentId` routes have no auth. Track the per-repoId TODO.

MZ: This is fine.

#### 8. [Low] iframe resizer: OLD shipped a custom `h5p-resizer.js`; NEW relies on lumi/core resizer
- OLD: bundled `h5p-resizer.js` (custom: `prepareResize` tweaked to compare `clientHeight` rather than `scrollHeight`, comment at lines 52-63) plus iframe `width:100%` handling.
- NEW: resizing is handled by the H5P core JS that lumi serves; there is no custom resizer. The `eduSharingPlayer.ts` page is itself the iframe body (`class="h5p-iframe"`).
- Impact: OLD had a deliberate local patch to the resize logic to avoid flicker/incorrect height. If that patch addressed a real layout bug, the stock core resizer may reintroduce it. Low risk but worth a visual check on tall/scrolling content.
- Recommendation: visually verify auto-resize of embedded H5P (esp. content that shrinks) matches OLD behavior.

MZ: This is fine. Resizer is in place and works (served by repo).

#### 9. [Low] `redirect.inc.php` library-path guard had a real bug; not relevant in NEW (info)
- OLD: `redirect.inc.php:2` guards library cache access with `strpos($_REQUEST['ID'],'cache/h5p/libraries') !== -1` — `strpos` returns `false`/int and `!== -1` is **always true** (strpos never returns -1), so the intended whitelist check was effectively a no-op; only the `..` traversal check (`strpos(...,'..') === false`) actually constrained access. Library/asset files were then served via `redirect_header.inc.php` with mime-type forcing for css/html/js.
- NEW: assets are served through `LumiProxyController.getContentAssets` / `getH5PCoreAssets` proxying to lumi, with normal Spring routing. The OLD path-traversal/whitelist logic is not carried over and is not needed.
- Recommendation: none (informational). Just confirm the NEW asset proxy does not allow path traversal into arbitrary lumi files — `LumiProxyService` forwards `request.requestURI` after `pathPrefix`; lumi's `h5pAjaxExpressRouter` and `/content/:contentId/**` constrain this, but a quick traversal test (`/public/h5p/content/x/../../`) is worth running.

MZ: Check this! But it is probably fine. Prio 2.

#### 10. [Low] OLD content "description"/title mapping nuance not replicated
- OLD: `H5PFramework::loadContent()` had a special case (lines 826-835): because the H5P `title` column stored the edu-sharing nodeId+hash, it substituted the DB `description` (the real node title) into `metadata.title`. `createInstance` set `description` = node title (`mod_h5p.php:92-93`).
- NEW: lumi stores its own H5P metadata title from the package (`saveOrUpdateContent` derives the main library/params from the package metadata, `router.ts:90-96`); the edu-sharing node title is not injected into the H5P metadata. The mapping table is just `{nodeId, contentId}` (`EduSharingModel.ts`).
- Impact: the title shown in the H5P frame/copyright dialog now comes from the package's own metadata rather than the edu-sharing node title. Usually fine, but a difference from OLD where the node title overrode it.
- Recommendation: confirm acceptable; if node title must win, pass it through on upload.

MZ: Check this! Prio 1.

#### 11. [Info] Whitelisted extensions / library restriction / patch-upgrade logic
- OLD: `isPatchedLibrary()` and `getLibraryId(... ANY ...)` implemented patch-version upgrade detection and "newest version" resolution; `restricted`/`runnable` columns existed; `getWhitelist()` was a stub returning `''` (i.e. relied on core defaults). `replaceContentTypeCache()` populated the hub cache.
- NEW: all of this is internal to `@lumieducation/h5p-server` (library install/upgrade on `uploadPackage`, semantics, whitelist, hub via `config.json hubContentTypesEndpoint`). No parity gap — this is strictly an improvement (maintained implementation vs. partially-stubbed hand-rolled one). `contentHubEnabled:true` + `hubContentTypesEndpoint` mean lumi can reach `api.h5p.org`; OLD also did. Just note that library installation now happens implicitly at upload time (`onlyInstallLibraries:false`, `router.ts:85`) — there is no admin library-management UI equivalent.

MZ: This is fine.

---

### Net assessment
The rewrite is structurally sound: it replaces ~1900 lines of hand-maintained H5P framework/validator/storage glue with the maintained `@lumieducation` stack, and pushes auth/CSP enforcement into the proxy layer. The genuine **bug to fix** is finding #1 (swapped/incorrect `coreApiVersion`/`h5pVersion` in `lumi/src/index.ts`). The next priorities are the upload memory/size guard (#2), the contradictory copyright-button config (#4), and confirming LaTeX/MathDisplay coverage (#5) and xAPI wiring (#3) still meet requirements. The remaining items are lower-risk behavioral diffs to verify against product expectations.

---

# §5 — Integration modules & dropped modules


Audit of the PHP→Kotlin rewrite of the edu-sharing rendering service. Compares OLD PHP
integration/misc modules against the NEW Kotlin modules, with special focus on opaque logic
and dropped functionality.

- OLD root: `/home/marian/testCheckouts/edu-sharing-community-services-rendering/service/src/main/php/modules/`
- NEW root: `/home/marian/development/rendering2/services-rendering-service2/service/src/main/kotlin/org/edu_sharing/rendering/modules/`

### Architectural note (applies to all findings)
The OLD service was a **synchronous PHP renderer that produced HTML** (iframes, `<video>`
tags, LTI launch forms, embed snippets) and served it directly. The NEW service is an
**async job/JSON service**: a module either returns a cached `ObjectLink` or enqueues a
RabbitMQ job, and an `objectLinks` / `additionalData` JSON payload is returned to the caller
(the edu-sharing repository / a frontend renders the actual HTML). Consequently, all the
HTML/embed-snippet generation that lived in the PHP modules (`getVideoEmbedding`,
`getH5PEmbedding`, LTI launch `<form>`, etc.) is *expected* to be absent from the backend —
but only if a frontend equivalent exists. Several modules below have **no equivalent at all,
front or back**, which is the real risk.

---

## DROPPED MODULES (no new equivalent — flagged explicitly)

The following OLD modules have **NO** corresponding NEW Kotlin module, type-mapper, or
controller. Verified by reading every `*TypeMapper.kt`, the `ModuleRegistry` dispatch table,
and grepping the whole `modules/` tree for `lti`, `oauth`, `youtube`, `vimeo`, `prezi`,
`pixabay`, `learningapps`, `wwwurl`, `collection`, `scenario`, `directory`, `saved_search`.

| OLD module | OLD path | Status in NEW |
|---|---|---|
| **url** (`mod_url`) | `url/mod_url.php` | **DROPPED** (partial: see C1) |
| **lti** (`mod_lti`, `ltiTool`, `edutoolVanilla`, `edutoolEtherpad`, `ims-blti/*`, `etherpad/*`) | `lti/` | **DROPPED** (see C2) |
| **learningapps** (`mod_learningapps`) | `learningapps/mod_learningapps.php` | **DROPPED** (see H1) |
| **scenario** (`mod_scenario`) | `scenario/mod_scenario.php` | **DROPPED** (see Info-1) |
| **collection** (`mod_collection`) | `collection/mod_collection.php` | **DROPPED** (see M1) |
| **directory** (`mod_directory`) | `directory/mod_directory.php` | **DROPPED** (see M2) |
| **saved_search** (`mod_saved_search`) | `saved_search/mod_saved_search.php` | **DROPPED** (see M3) |

---

## Findings

### C1 — Critical: `url` module dropped — remote/embed handling (video, audio, image, H5P, Prezi, Pixabay, LTI-1.3, custom embedding) has no backend equivalent
- **Severity:** Critical (if URL/remote objects are still a live content type)
- **OLD:** `url/mod_url.php` (426 lines — the single largest integration module).
- **NEW:** MISSING / DROPPED. No `noConversion`-style or other module maps `ccm:wwwurl`,
  `RemoteObjectType`, or `repositoryType` YOUTUBE/PIXABAY. `ModuleRegistry` has no
  `resourceType`/`mimeType` entry that would catch a `mod_url` node; such a node now hits
  `ObjectTypeNotSupportedException`.
- **What OLD does (opaque, load-bearing):**
  - Type-dispatches a remote object via `RemoteObjectType` into: video, audio, image, H5P,
    Prezi, Pixabay embeddings, plus LTI-1.3 tool launch and a config-driven `urlEmbedding`
    (`customEmbedding` placeholder with `{{VIDEO_FOOTER_PLACEHOLDER}}`).
  - **YouTube**: rewrites to `youtube-nocookie.com/embed/<id>?modestbranding=1`; parses both
    `youtu.be/<id>` short links and `?v=` query form.
  - **Vimeo**: decodes HTML entities, parses `/video/<id>?h=<token>` *and* `/<id>/<token>`
    path forms and reconstructs the privacy-hash `?h=` param (genuinely opaque logic).
  - **Prezi**: appends `/embed`.
  - **Data-protection (DSGVO) gate**: `ESRender_DataProtectionRegulation_Handler` produces a
    consent dialog for Vimeo/external embeds; audio playback `#timestamp` deep-link is
    suppressed when behind data protection. None of this is reproduced.
  - LTI-1.3 tool embedding: builds an auto-resizing iframe from
    `nodeUrls->generateLtiResourceLink` with `&editMode=false&launchPresentation=iframe`.
- **How NEW covers it:** It does not. The only "remote" modules in NEW are **DDB**
  (`remoteRepositoryType = "DDB"`) and **SODIX** (`replicationSource = "SODIX"`) — both new
  integrations, neither replaces generic `ccm:wwwurl` / YouTube / Vimeo / Pixabay / Prezi /
  LTI-1.3 URL rendering. The `av/video` module handles **content** audio/video by mime-type
  prefix only (`VideoService`/`VideoRenderModule` contain no youtube/vimeo logic — confirmed
  by grep).
- **Recommendation:** Confirm with product whether generic URL/remote-link nodes are still
  renderable in the new architecture. If yes, this is a major gap: the YouTube short-link /
  Vimeo privacy-hash parsing and the DSGVO consent gating are non-trivial and security/
  privacy-relevant — they must be re-implemented (likely frontend-side, but the backend must
  at minimum *route* such nodes instead of throwing). If URL nodes are intentionally retired,
  document it.

MZ: Url module is now frontend only.

### C2 — Critical: LTI-1.1 / Etherpad / Vanilla — entire LTI launch + OAuth-1.0 signing dropped
- **Severity:** Critical (security-relevant; OAuth signing logic lost)
- **OLD:** `lti/mod_lti.php`, `lti/ltiTool.php`, `lti/edutoolVanilla.php`,
  `lti/edutoolEtherpad.php`, `lti/ims-blti/blti_util.php`, `lti/ims-blti/OAuth.php`,
  `lti/etherpad/index.php`.
- **NEW:** MISSING / DROPPED. No `lti` package; grep for `lti|oauth|signParameters` in the
  NEW `modules/` tree returns only unrelated DDB/Binder hits.
- **What OLD does (highly opaque, security-critical):**
  - `ltiTool.php`: builds an LTI-1.1 `basic-lti-launch-request` and **signs it with
    HMAC-SHA1** — manually constructs the OAuth base string
    (`POST&urlencode(endpoint)&rawurlencode(sorted params)`), key = `tool_instance_secret&`,
    `base64(hash_hmac('sha1', base, secret, true))`. Auto-submitting `<form>` carries
    `oauth_consumer_key` = `ccm:tool_instance_key`, roles/params from node properties.
  - `edutoolVanilla.php` + `ims-blti/OAuth.php`/`blti_util.php`: a full OAuth-1.0
    implementation (`OAuthRequest::from_consumer_and_token`, `OAuthSignatureMethod_HMAC_SHA1`)
    used to launch a "Vanilla" forum tool with `VANILLA_KEY`/`VANILLA_SECRET`.
  - `edutoolEtherpad.php` + `etherpad/index.php`: issues a one-time `md5(microtime)` token
    written to `CC_RENDER_PATH/etherpad/token/`, then a JS client calls the Etherpad API
    (`createAuthorIfNotExistsFor`, `createGroupIfNotExistsFor`, `createGroupPad`,
    `createSession`) with `APIKEY`.
- **How NEW covers it:** Not at all. Note: this is **LTI-1.1** (consumer-side signing). The
  dropped `mod_url` (C1) handled **LTI-1.3** tool launch links separately — so *both* LTI
  paths are gone from the backend.
- **Recommendation:** Determine whether LTI-1.1 tool launch, the Vanilla forum integration,
  and the Etherpad integration are still supported products. The OAuth-1.0 HMAC-SHA1 signing
  is exactly the kind of opaque, easy-to-get-subtly-wrong logic this audit targets — if LTI
  is still needed, do not re-implement signing from memory; port carefully and test against a
  known consumer. If these are dead (Vanilla/Uni-Weimar references look legacy), record the
  removal decision.

MZ: This is also handled on the frontend-side.

### H1 — High: `learningapps` module dropped
- **Severity:** High
- **OLD:** `learningapps/mod_learningapps.php`.
- **NEW:** MISSING / DROPPED.
- **What OLD does:** Renders LearningApps.org embeds; rewrites the URL
  `https://learningapps.org/<id>` → `https://learningapps.org/view<id>` (opaque, easy to
  miss), and shows a DSGVO consent dialog (`LEARNINGAPP`).
- **How NEW covers it:** No module maps a LearningApps URL/resourceType. Same data-protection
  consent gap as C1.
- **Recommendation:** Confirm whether LearningApps content still exists in repositories. If
  so, the `view`-URL rewrite must be preserved (without it the embed shows the editor, not
  the app) and the consent dialog re-introduced (frontend).

MZ: This is handled on the frontend-side.

### H2 — High: SCORM/HTML custom entry-point + Articulate Storyline `story.html` handling lost
- **Severity:** High
- **OLD:** `html/mod_html.php::getIndexFileName()`.
- **NEW:** `eduhtml/EduHtmlConversionService.kt` (`getZipRootPath`), `eduhtml/EduHtmlService.kt`.
- **What OLD does:** Picks the archive entry point intelligently:
  1. If resourceType is `ADL SCORM`/`IMS Common Cartridge` **and** `ccm:ccresourcesubtype` ==
     "articulate storyline" → serve **`/story.html`**.
  2. Else honor a custom main entry from node property **`ccm:ccressourcemainentity`**.
  3. Else fall back to `/index.html`.
- **How NEW covers it:** Partially. `EduHtmlConversionService.getZipRootPath` only searches
  for `index.html`/`index.htm` to locate the zip root, and `EduHtmlService.getObjectLink`
  hard-codes `"index.html"`. **Articulate Storyline packages (entry `story.html`, no
  `index.html`) will fail with `ConversionException`**, and the `ccm:ccressourcemainentity`
  custom-entry override is gone. (Confirmed: grep for `story|mainentity|articulate` finds
  nothing in NEW.)
- **Note on mapping:** OLD `mod_html` mapped to NEW **eduhtml** (resourceType `eduhtml`), and
  OLD `mod_scorm` (the Moodle SCORM upload path) maps to NEW
  `moodle/ScormRenderModule`. The old SCORM-via-`mod_html` zip-serving behavior is what
  `eduhtml` replaces, so the Storyline/custom-entry logic belongs here.
- **Recommendation:** Re-add the `story.html` fallback and `ccm:ccressourcemainentity`
  override in `EduHtmlConversionService`/`EduHtmlService`, or confirm those package types are
  no longer served this way.

MZ: Check this! Important! Prio 1.

### H3 — High: Moodle restore no longer persists/reuses the course id (re-restores every render)
- **Severity:** High (functional + performance/correctness)
- **OLD:** `moodle/mod_moodle.php` (`cacheCourseId`/`getCourseId` write/read a
  `<filepath>.txt`), `scorm/mod_scorm.php` (same).
- **NEW:** `moodle/MoodleUploadService.kt`, `moodle/MoodleRenderModule.kt`,
  `moodle/MoodleJobService.kt`.
- **What OLD does:** On `createInstance` it restores the course **once**, writes the returned
  `courseId` to a sidecar `.txt`, and on every later `dynamic()`/`embed()` reads the cached
  id instead of re-restoring; the per-user forward token is minted fresh each call from the
  cached course id.
- **How NEW does it:** `MoodleUploadService.getUrl` **always** uploads/restores the course
  (`uploadCourse`/`uploadScorm`) and then mints the user token — there is no persisted
  courseId cache. Each render request enqueues a fresh `MoodleJobMessage` and re-runs the
  whole restore→token flow. The new flow does add a robust async restore poll
  (`local_edusharing_status`, 10-min timeout, `triggerCron`) absent in OLD, and surfaces a
  user-facing `MoodleUploadException.publicMessage`.
- **Recommendation:** Verify whether the Moodle plugin side now dedupes restores by nodeId
  (the new WS `local_edusharing_restore_course` may return an existing `courseId` when already
  restored — `uploadCourse` does short-circuit if `courseId > 0`). If Moodle does *not* dedupe,
  the missing courseId cache means a repeated, expensive restore per view — confirm and, if
  needed, cache the courseId (e.g. in the `RenderingJob`/registration store).

MZ: This is fine, we decided to look up the id every time.

### H4 — High: Moodle WS contract changed substantially — confirm Moodle plugin version match
- **Severity:** High (integration contract; opaque)
- **OLD:** `mod_moodle.php` / `mod_scorm.php`.
- **NEW:** `MoodleUploadService.kt`, `MoodleRenderModule.kt`.
- **What changed (load-bearing details):**
  - WS function names: OLD `local_edusharing_restore` / `local_edusharing_scorm` /
    `local_edusharing_handleuser`; NEW `local_edusharing_restore_course` /
    `local_edusharing_scorm_course` / `local_edusharing_user`, plus **new** functions
    `local_edusharing_status` and `local_edusharing_ping` and a `cron.php` trigger.
  - Token acquisition: OLD used a **static `MOODLE_TOKEN`** constant; NEW can either use a
    configured `token` *or* fetch one dynamically via `/login/token.php?service=es-webservice`
    with user/password (`getWebserviceToken`). Good improvement, but a hard contract change.
  - Params: OLD posts form-encoded (`CURLOPT_POSTFIELDS`) with `htmlentities`-escaped values;
    NEW sends them as GET **query params** (`UriComponentsBuilder`), no `htmlentities`. Field
    names changed (`user_givenname`→`firstName`, `user_surname`→`lastName`, `user_name`→
    `userName`, `nodeid`→`nodeId`, `courseid`→`courseId`).
  - `Host` header override: OLD `MOODLE_PUBLIC_HOST`; NEW derives the Host header from
    `publicurl` (`buildHostHeader`). Preserved.
  - OLD workaround `str_replace('<?php', '', $resp)` (Moodle sometimes prefixes `<?php`) is
    **gone** — NEW parses raw JSON via `objectMapper.readTree`; if Moodle still emits that
    prefix, parsing fails. Worth verifying against the deployed Moodle plugin.
- **Recommendation:** This requires a **matching new `local_edusharing` Moodle plugin**.
  Confirm the deployment ships that plugin; otherwise all Moodle/SCORM rendering breaks. Note
  the lost `<?php`-stripping defensive hack.

MZ: This is fine, we use a completely new logic in rendermoodle.

### M0 — Medium: Two redundant identical user-token calls in Moodle upload
- **Severity:** Medium (efficiency / likely bug)
- **NEW:** `MoodleUploadService.getUrl` (lines ~55-69) calls `getUserToken(...)` **twice**
  with identical arguments (`userTokenPreview`, `userTokenLink`) to build a `previewUrl` and a
  `linkUrl`. Each `getUserToken` mints a fresh token; the two forward URLs differ only by an
  independently-minted token. OLD also minted fresh tokens per call (`getForwardUrl` is called
  twice for `url`/`url2`), so behavior parity is roughly preserved, but this doubles Moodle
  user-provisioning calls per job.
- **Recommendation:** Confirm whether preview vs. link genuinely need distinct one-time
  tokens. If not, mint once and reuse.

MZ: This is fine, we need 2 separate tokens.

### M1 — Medium: `collection` module dropped
- **Severity:** Medium
- **OLD:** `collection/mod_collection.php` — trivial: extends `mod_doc`, sets
  `showDownloadAdvice = false`, delegates to doc rendering.
- **NEW:** MISSING / DROPPED. The NEW `document` module handles documents but there is no
  collection-specific variant suppressing the download advice.
- **Recommendation:** Low-risk (thin wrapper), but confirm collections are now rendered by the
  generic document/preview path and that the "no download advice" UX detail is intentionally
  dropped.

MZ: Check this! But the module was excluded from the new service. Prio 1.

### M2 — Medium: `directory` module dropped (incl. instance-cache validation logic)
- **Severity:** Medium
- **OLD:** `directory/mod_directory.php`.
- **NEW:** MISSING / DROPPED.
- **What OLD does:** Renders a folder/directory node as an inline list of children
  (icon/name/NodeID), with a "open in workspace" link and creator name (suppressing
  administrator/unknown). Also implements `instanceExists()` — a **DB + filesystem cache
  consistency check** (queries the `ESOBJECT` table by repId/contentHash/objectId[/version],
  and if the cache file is missing, deletes the stale DB row). This cache-coherency logic is
  opaque and edu-sharing-specific.
- **How NEW covers it:** The new cache model is S3/Mongo-based and the cache-coherency concern
  is handled differently (`cacheCleaner`, tracking), so the `instanceExists` logic is not a
  1:1 loss. But there is **no directory listing renderer** at all.
- **Recommendation:** Confirm directory nodes are no longer rendered by this service (likely
  handled by the repo/frontend now). Flag the dropped inline children-listing UX.

MZ: Check this! But the module was excluded from the new service. Prio 2

### M3 — Medium: `saved_search` module dropped
- **Severity:** Medium
- **OLD:** `saved_search/mod_saved_search.php` — inline render of a saved-search node
  (title, home-repo URL, objectId, children, metadata).
- **NEW:** MISSING / DROPPED.
- **Recommendation:** Confirm saved-search rendering moved to the frontend/repo.

MZ: Check this! But the module was excluded from the new service. Prio 2

### Info-1 — Info: `scenario` module dropped (already-dead legacy)
- **Severity:** Info (almost certainly dead)
- **OLD:** `scenario/mod_scenario.php` + `config.php.example`.
- **What OLD does:** SOAP-based (`SoapClient`) integration with a "metacoon"/scenario player
  (`authenticateByApp`, `createScenario`); config points at `127.0.0.1` WSDL placeholders;
  code is full of `var_dump(); die();` debug paths. Clearly legacy/abandoned.
- **NEW:** MISSING / DROPPED.
- **Recommendation:** Safe to drop; record the decision. No action needed beyond confirming no
  repository still tags nodes as `scenario`.

MZ: Check this! But the module was excluded from the new service. Prio 2

### Info-2 — Info: `default/` and `html/redirect*.inc.php` content-serving helpers superseded by AssetController
- **Severity:** Info
- **OLD:** `modules/default/redirect_header.inc.php`, `modules/default/cc_rd_debug_handle.inc.php`,
  `modules/html/redirect_header.inc.php`, `modules/html/redirect.inc.php`, top-level
  `modules/redirect.php`.
- **What OLD does:** Sets `Content-Type`/`Content-Disposition` (download vs inline) per
  `display_kind`; forces `text/css` for `.css`; `Pragma: no-cache`/`Expires: 0`. `redirect.php`
  streams files in 2 KB chunks with `Access-Control-Allow-Origin: *`, path sanitization
  (`//`→`/`), and a debug-die logger. `html/redirect.inc.php` is a **session allow-list /
  token-skip**: if the request basename is `index.html` it records the dir in
  `$_SESSION['esrender']['mod']['html']['allow']`, then sub-resources under that dir skip the
  token check.
- **How NEW covers it:** The `asset` package (`AssetController`/`AssetService`) handles
  download/stream with HTTP Range support and CSP headers (per service `CLAUDE.md`), and the
  eduhtml/html modules return signed/permission-scoped `ObjectLink`s. So the *mechanism* is
  replaced. **However**, the OLD per-directory session allow-list (serve `index.html` then
  allow sibling assets without re-tokenizing) is a specific access-control pattern — verify the
  new asset/permission model grants access to *all* extracted sub-assets of an eduhtml/html
  package, not just the `index.html` the module returns. `EduHtmlService.getObjectLink` only
  returns a link to `index.html`; relative asset requests must still resolve under the node's
  permission scope.
- **Recommendation:** Verify sub-resource (CSS/JS/images) access for extracted HTML packages
  works under the new `AssetController` permission/session model. Note the `text/css` forcing
  — `EduHtmlConversionService` uses `URLConnection.guessContentTypeFromName` per entry, which
  does return `text/css` for `.css`, so that detail is preserved.

MZ: This is fine and has been checked.

---

## Mapped modules — coverage summary (quick reference)

| OLD | NEW | Coverage |
|---|---|---|
| `moodle/mod_moodle` | `moodle/MoodleRenderModule` + `MoodleUploadService` + async job/receiver | Covered, but WS contract changed (H4), courseId cache dropped (H3), duplicate token call (M0) |
| `scorm/mod_scorm` | `moodle/ScormRenderModule` (subclass; `local_edusharing_scorm_course`) | Covered (same caveats as moodle) |
| `html/mod_html` | `eduhtml/*` (zip extract + serve index.html) | Mostly; **lost** Storyline `story.html` + `ccm:ccressourcemainentity` (H2) |
| `url/mod_url` | — | **DROPPED** (C1) |
| `lti/*` | — | **DROPPED** (C2) |
| `learningapps` | — | **DROPPED** (H1) |
| `collection` | — | **DROPPED** (M1) |
| `directory` | — | **DROPPED** (M2) |
| `saved_search` | — | **DROPPED** (M3) |
| `scenario` | — | **DROPPED**, legacy/dead (Info-1) |
| `default/*`, `redirect*.inc.php` | `asset/AssetController` | Replaced (Info-2; verify sub-asset access) |

**New integrations with no OLD counterpart** (confirmed not migrations): `sodix/*`
(`replicationSource = "SODIX"`, paid-media role handling) and `ddb/*`
(`remoteRepositoryType = "DDB"`, REST + IIIF image API). `binder/*` and `onyx/*` are likewise
new/separate from the OLD set listed here.

---

# §6 — Admin, storage/cache cleanup & deploy


Parity audit of the PHP → Kotlin/Spring rewrite, focused on the **admin API**, **storage/cache cleanup**, and **deploy/config** surfaces. Emphasis on *opaque* logic (eviction policy, thresholds, cron, quota, maintenance/migration tasks) that may have been lost.

Architectural note up front: the OLD service stored renderings on **local disk** (`CC_RENDER_PATH`, optional `CC_RENDER_PATH_SAFE`) and tracked them in a **PostgreSQL/MySQL** schema (`ESOBJECT`, `ESTRACK`, `ESMODULE`, `ESOBJECT_CONVERSION`, `VERSION`, `h5p_*`). The NEW service stores renderings in **S3** (bucket strategies) and tracks them in a **MongoDB `Tracking` collection**. So "lost logic" usually means an old disk/SQL maintenance behavior that has no S3/Mongo counterpart, or a different eviction policy — not a literal missing line.

---

### Severity index

| # | Sev | Title |
|---|-----|-------|
| 1 | Critical | Cache-cleaner eviction policy & trigger fundamentally changed (disk-ratio LRU → per-repo quota-ratio LRU) |
| 2 | High | "Use disk free space" cleanup mode (`useDiskSize`) dropped |
| 3 | High | Per-module / forced full cache wipe (`cleanUp(force)`, `cleanUpByModule`) has no admin/CLI equivalent |
| 4 | High | H5P sweep maintenance task (`sweepH5P`) — no equivalent |
| 5 | High | Version-gated update/migration ladder (`update.php`) — no equivalent migration framework |
| 6 | Medium | H5P library/core admin (install, upgrade, content bulk-upgrade) — no equivalent |
| 7 | Critical | Helm cache-cleaner thresholds are **negative** (`-0.7`/`-0.9`) → would evict entire repo caches every run |
| 7b| Medium | Cleaner now requires a quota to do anything; repos with `quota=0` are never cleaned (not surfaced in helm/compose) |
| 13| High | Threshold values disagree across the three config sources |
| 14| Medium | Cron format changed 5-field Unix → 6-field Spring (operator schedules will fail to parse) |
| 15| Medium | Dropped converter limits: FFmpeg exec timeout, H5P disableCacheDelay, outbound HTTP proxy; audio bitrate unit mismatch |
| 8 | Medium | Readiness probe semantics changed (server-load 503, version, DB) |
| 9 | Low | Old per-installation CLI installer / initdb — N/A by design |
| 10| Info | Admin auth model changed (MD5 DB-password login → HTTP Basic single admin) |
| 11| Info | Admin UI scope greatly *expanded* in new (jobs/assets/storage/repo dashboard) |
| 12| Low | Tracking size accounting differs (per-directory size vs per-object) |

---

### 1. [Critical] Cache-cleaner eviction policy & trigger fundamentally changed

- **OLD:** `service/src/main/php/func/classes.new/Helper/CacheCleanerClass.php` + `cacheCleaner.php`
- **NEW:** `service/src/main/kotlin/org/edu_sharing/rendering/cacheCleaner/CacheCleaner.kt` (+ `TrackingService.kt`)

**What OLD does (opaque core):**
- Global, **disk-usage-ratio** driven, recursive. `cleanUp()` computes `cacheSize / availableSpace` across `CC_RENDER_PATH` (+ `CC_RENDER_PATH_SAFE`). Hard-coded constant `define('RATIO_MAX', 0.8)` — i.e. start evicting at **80% of total disk**.
- While `diskUsageRatio > RATIO_MAX` (or `forceDelete`), it deletes **one** "undemanded" object per pass and **recurses** until back under 80%.
- Victim selection = global LRU via SQL: `SELECT ESTRACK_ESOBJECT_ID, MAX(ESTRACK_TIME) FROM ESTRACK GROUP BY ESTRACK_ESOBJECT_ID ORDER BY MAX(ESTRACK_TIME) ASC LIMIT 1` (the single least-recently-accessed object across the whole node). Per victim: delete `ESOBJECT` row, delete its on-disk module/sub-uri dir (and SAFE mirror), special-case H5P (delete `h5p_contents`/`h5p_contents_libraries` + `h5p/content/<id>` dir), then `DELETE FROM ESTRACK WHERE ESTRACK_ESOBJECT_ID = …`.

**What NEW does:**
- `@Scheduled(cron = app.cache.cleaner.schedule)` (default `0 0 1 * * *` = daily 01:00), `@ConditionalOnMaster`, **in-process** (not a separate job).
- Iterates **per repo** (`storageService.getStorageInfo()` → one `StorageInfo` per repoId from the Mongo bucket aggregation). For each repo: `usedSpace = size / maxSize` where `maxSize` is the repo's **registered quota** (not disk). If `usedSpace > upperThreshold` (default `0.25`), evict oldest-`lastAccessed`-first (`TrackingService.getTrackedObjectsByRepoId`, sorted ASC) accumulating until `totalSize >= lowerThreshold * maxSize` (default `0.15`), then bulk-delete S3 objects + Tracking entries grouped by bucket manager.

**Differences / risk:**
- **Trigger metric changed**: OLD = % of *physical disk*; NEW = % of *configured per-repo quota*. These are not equivalent; a deployment that relied on disk pressure will behave differently.
- **Thresholds changed**: OLD single threshold 0.8; NEW dual band 0.25 upper / 0.15 lower. NEW evicts much more aggressively *relative to quota* and never considers actual disk/bucket free space.
- **Scope changed**: OLD = global LRU across all content; NEW = per-repo LRU, only for repos that have a quota.
- **Recommendation:** Confirm with ops that quota-relative eviction is the intended new model. The hard-coded `RATIO_MAX=0.8` disk safety net is gone — there is no global "S3/disk is filling up" backstop in NEW (see #2, #7).

MZ: This is fine, we want the new logic.

### 2. [High] "Use disk free space" cleanup mode (`useDiskSize`) dropped

- **OLD:** `CacheCleanerClass::dirSize()` + ctor read `getenv('SERVICES_RENDERING_SERVICE_CACHE_CLEANER_USE_DISK_SIZE')`; helm `deploy/.../values.yaml` `job.cachecleaner.config.useDiskSize: true` (default **true** in prod).
- **NEW:** MISSING.

When `useDiskSize=true`, OLD measured cache size as `disk_total_space - disk_free_space` (true filesystem occupancy, counting *everything* on the volume) instead of summing tracked file sizes — a deliberate "protect the actual disk" mode that was the **default in production**. NEW has no notion of physical free space at all (S3 buckets + Mongo-tracked sizes only). **Recommendation:** If S3 backing storage has finite capacity, add a free-space/quota backstop; otherwise document that disk-based protection is intentionally retired with the move to S3.

MZ: This is fine, we want the new logic (old one would not work with S3).

### 3. [High] Per-module / forced full cache wipe has no admin/CLI equivalent

- **OLD:** `CacheCleanerClass::cleanUp($forceDelete=true)` and `cleanUpByModule()` (invoked as `php cacheCleaner.php <moduleName>`). The module path deletes **all** `ESOBJECT` rows for a module, removes `CC_RENDER_PATH/<module>`, and **TRUNCATEs all H5P tables** (`h5p_contents`, `h5p_contents_libraries`, `h5p_libraries`, `h5p_libraries_libraries`, `h5p_libraries_languages`, `h5p_libraries_hub_cache`).
- **NEW:** Partial. `AdminAssetController.deleteAssetsByType` / `deleteAllAssets` (per-repo, by type or all) and `AdminController.deleteObjectFromCache` cover targeted/per-repo deletion. There is **no** global "force clean now", no "wipe an entire module/type across all repos", and **no H5P-library-table truncate**. The scheduled cleaner cannot be triggered on demand.
- **Recommendation:** If operators relied on `cacheCleaner.php h5p` to nuke a content type globally, expose an admin endpoint or accept that it's now per-repo only. Note the new `deleteAllAssets` is per-repo (`repoId` required), so a true global flush requires iterating repos.

MZ: This is fine, it does now.

### 4. [High] H5P sweep maintenance task — no equivalent

- **OLD:** `service/src/main/php/install/sweepH5P.php` (`\h5p_install\sweep_h5p`), exposed via `admin/cli/sweepH5P.php`. Deletes all `ESOBJECT` for the h5p module, deletes `ESTRACK` rows for it, and `rrmdir`s `CC_RENDER_PATH/h5p` (+ SAFE).
- **NEW:** MISSING (grep for h5p library/hub/sweep in `modules/h5p/` returns nothing).
- This was the "reset all H5P caches" recovery operation. NEW H5P uses Lumi + S3 (`modules/h5p/lumi/*`), so the SQL/dir form is obsolete, but the *capability* (purge all H5P renderings to force regeneration) should be re-confirmed as available via per-repo asset deletion by type. **Recommendation:** verify "type" granularity matches H5P and document the replacement; flag the OLD `ESTRACK_MODULE_ID` column-name (vs `ESTRACK_MODUL_ID` used elsewhere) as a latent OLD bug not worth porting.

MZ: This is fine, just use the by type deletion.

### 5. [High] Version-gated update/migration ladder — no equivalent

- **OLD:** `service/src/main/php/admin/update/update.php` (driven by `admin/model/Updater.php`, `admin/cli/update.php`, `admin/index.php?action=doupdate`). A long `run($installedVersion)` chain of `version_compare`-gated blocks performing DDL alters, mimetype/`ESMODULE` seed inserts, **config-file rewrites**, recursive dir deletes, a **mass JPG→PNG conversion** of the picture cache (<4.0.0), a **full cache+DB wipe** (<5.1: delete `ESOBJECT`/`ESOBJECT_LOCK`/`ESTRACK`/`ESOBJECT_CONVERSION` + `rrmdir(CC_RENDER_PATH)`), and an H5P SQLite→RDBMS migration (<6.0.99). Tracked via the `VERSION` table.
- **NEW:** MISSING as a concept. The new service is stateless-config + Mongo/S3; there is no in-app schema-migration framework and no `VERSION` table. Mongo `@Document` schemas evolve implicitly; `RenderingJob`/`Tracking` carry TTL indexes.
- **Assessment:** Mostly **correctly obsolete** (those migrations targeted the old SQL schema and disk layout). The watch-item is whether any **data migration from the old service to the new** is needed at cutover — none exists here, and the old `VERSION`-table update protocol is gone by design. **Recommendation:** confirm no in-place upgrade path is expected; new deployments start fresh.

MZ: This is fine.

### 6. [Medium] H5P library/core admin — no equivalent

- **OLD:** `admin/h5p/` — `index.php` (search/paginate/delete H5P content from `h5p_contents`), `libraries.php` + `libraries-update.php` + `h5p_ajax.php` (list installed libraries, compute upgrades, **client-driven bulk content-upgrade** writing `h5p_contents`/`h5p_contents_libraries`), `update_core.php` (upload + **backup-and-swap** of `vendor/lib/h5p-core`, restore). Shared login via `$_SESSION['loggedin']`.
- **NEW:** MISSING — there is no H5P library-management or content-upgrade UI/endpoint. H5P is handled by Lumi (`modules/h5p/lumi/*`) which has its own content model.
- This is a genuine **feature drop** if customers used the H5P library/core admin. **Recommendation:** confirm Lumi makes the old H5P-core/library tooling unnecessary; if any "upgrade existing H5P content to a new library version" workflow is still needed, it has no home in the new admin API. Note the OLD `h5p_ajax.php` had its **nonce/CSRF check commented out** — a latent OLD vuln, not to be ported.

MZ: We use Lumi now.

### 7. [Critical] Helm cache-cleaner thresholds are negative

- **NEW helm:** `deploy/docker/helm/service/src/main/chart/values.yaml:288-290` sets `config.cache.cleaner.threshold.lower: -0.7` and `upper: -0.9`, rendered verbatim into `app.cache.cleaner.threshold.{lower,upper}` via `templates/configmap-env.yaml:66-67`.
- **Effect on `CacheCleaner.cleanCache()`:** `usedSpace` (a ratio ≥ 0) is compared `> upperThreshold (-0.9)` → **always true** whenever a quota is set, so the cleaner fires every run. The cleanup target is `lowerThreshold * maxSize = -0.7 * maxSize` (negative), so `takeUntil(totalSize >= maxSize)` with a negative target evicts **the entire repo's cache on every run**.
- **Cross-source check:** the **compose** file (`docker/compose/src/main/compose/1_rendering2-common.yml:107-108`) uses `${RENDERING2_SERVICE_CACHE_CLEANER_THRESHOLD_LOWER:-0.7}` / `...UPPER:-0.9}` — here `:-` is the **bash default-value operator**, so compose actually defaults to **`0.7` / `0.9`** (positive, sane). The helm `-0.7`/`-0.9` are real YAML negatives — almost certainly a copy-paste of the compose strings that dropped the `:` and turned defaults into negative literals.
- **Recommendation:** Fix helm to positive values consistent with the intended policy. This is currently latent only because quota defaults to 0 (#7b), but becomes destructive the moment any repo quota is set in a helm deploy.

MZ: I guess Frank knows what he's doing.

### 7b. [Medium] Cleaner is a no-op for repos without a quota; quota not surfaced in deploy

- `CacheCleaner.cleanCache()`: `if (it.maxSize == 0L) { log "Nothing to clean."; return }`. `maxSize = RepositoryRegistration.quota` (default `0L`, `RepositoryRegistrationStorageService`).
- Neither the NEW helm `configmap-env.yaml` nor the compose files render any `app.repository.registration.id.<id>.quota` key (zero `quota` occurrences in NEW deploy `src/`). Quota is settable only via `app.repository.registration.id.<id>.quota` config or the admin API.
- **Net default behavior:** out-of-the-box the cleaner **never deletes anything**; the S3 cache grows unbounded. OLD always cleaned (disk-ratio, no quota needed). **Recommendation:** require/expose a quota in deploy, or add a global default-quota / disk-free backstop (see also #2). Combined with #7 this is a footgun: no quota → never cleans; set a quota under current helm → wipes everything.

MZ: This is fine.

### 13. [High] Threshold values disagree across config sources

`app.cache.cleaner.threshold.{lower,upper}` resolves to **different values** depending on delivery:
- `service/src/main/resources/application.properties:6-7` → **0.15 / 0.25**
- compose `1_rendering2-common.yml:107-108` → **0.7 / 0.9** (via bash defaults)
- helm `values.yaml:288-290` → **-0.7 / -0.9** (broken, see #7)

At most one of these reflects the intended productive eviction band. **Recommendation:** pick one policy and align all three; document the meaning (fraction of per-repo quota).

MZ: This is fine.

### 14. [Medium] Cron schedule format changed (5-field → 6-field)

- OLD schedules were Unix 5-field (`cron.sh` default `0 0 * * 0` weekly; helm CronJob `0 * * * *` hourly).
- NEW `app.cache.cleaner.schedule` is a **Spring 6-field** cron (`0 0 1 * * *` daily 01:00) parsed by `@Scheduled`.
- An operator who reuses an OLD 5-field string in the NEW env override (`RENDERING2_SERVICE_CACHE_CLEANER_SCHEDULE`) gets a **startup parse failure**. **Recommendation:** document the format change in upgrade notes.

MZ: This is fine.

### 15. [Medium] Dropped converter limits / proxy; audio bitrate unit mismatch

From the deploy/config cross-check (verify each is intentional, not silently lost):
- **FFmpeg exec timeout** — OLD `SERVICES_RENDERING_VIDEO_TIMEOUT` (default `3600`s) / `FFMPEG_EXEC_TIMEOUT`. No `app.converter.video.*timeout` key found in NEW properties/helm — long video conversions may now run unbounded.
- **H5P `disableCacheDelay`** — OLD `SERVICES_RENDERING_H5P_DISABLE_CACHE_DELAY` (default `0`); OLD helm `config.h5p.disableCacheDelay`. No NEW equivalent found.
- **Outbound HTTP proxy** — OLD `SERVICES_RENDERING_SERVICE_PROXY_*` → `proxy.conf.php`. No `app.*proxy*` client-proxy key in NEW (the Varnish "proxyCache" sidecar is unrelated). Confirm outbound proxy support isn't needed.
- **Audio bitrate unit mismatch** — `application.properties` `app.converter.audio.bitrate=160000` (bits/s) vs helm `bitrate: 160` (looks like kbps). Likely a defect; reconcile units.
- **Video defaults** — threads `1 → 0` (auto); resolution set `240,720,1080` → `480,720,1080` (240 dropped, 480 added; "default resolution" replaced by priority ordering). Confirm intended.

(These are converter-config items adjacent to this section's scope; full converter parity belongs to the modules report, but they surfaced during the deploy/config sweep and are recorded here.)

### 8. [Medium] Readiness probe semantics changed

- **OLD:** `admin/probe/ready.php` returns JSON with installed `VERSION`; checks Linux 1-min load-average and returns **HTTP 503** if `load[0] > 80`; opens DB and returns **HTTP 500** on PDO failure. `admin/probe/live.php` is empty (always 200).
- **NEW:** Spring Boot Actuator. `application.properties`: `management.endpoint.health.probes.enabled=true`, health exposed on `management.server.port=9080`; `S3HealthIndicator` (`storage/S3HealthIndicator.kt`) reports UP/DOWN by `listBuckets()`. Standard liveness/readiness groups.
- **Lost:** the **server-load → 503 "too busy"** backpressure signal (NEW has no loadavg gate) and explicit version reporting in the probe. DB-down is covered by Mongo/Redis health contributors; S3-down by the S3 indicator (arguably better than OLD). **Recommendation:** if load-shedding via readiness was relied on, reproduce it (e.g. a custom HealthIndicator or HPA), otherwise treat as intentionally dropped.

### 9. [Low] Per-installation CLI installer / initdb — N/A by design

- **OLD:** `admin/cli/install.php` + `install/initdb.php` + `install/install.php` — copy config templates, token-replace, **drop+create** all DDL tables, seed content, generate SSL keypair, register home repo, probe PHP extensions/Apache modules, create cache dirs.
- **NEW:** Replaced by container env/config + Mongo (no DDL), key handling in `edusharingRepo/services/PrivatePublicKeyService.kt` / `RepositoryPublicKeyService.kt`, repo registration via `RegistrationRunner` + `AdminController`. **No action** beyond confirming registration flow parity (covered by other report sections).

### 10. [Info] Admin auth model changed

- **OLD:** `admin/model/LoginManager.php` — login = DB username + **MD5(DB password)** from `conf/db.conf.php`, 30-minute PHP session; H5P pages reuse `$_SESSION['loggedin']`. (`Version.php` even silently rewrites `db.conf.php` to force MySQL/port 3306 when `$dsn` is empty — an OLD landmine, not to port.)
- **NEW:** HTTP Basic, single `ROLE_ADMIN` user `app.security.adminPassword`, `privateAPIFilterChain` over `/admin/**`, CSRF off (per service CLAUDE.md). MD5 hashing is gone (good). **Recommendation:** ensure `app.security.adminPassword` is set to a strong secret in deploy; no functional parity gap.

### 11. [Info] Admin UI scope expanded (not lost)

The OLD admin UI (`admin/view/choose.phtml`) offered essentially: run **update** and link to the **H5P tool**. The NEW `admin-frontend` (Angular SPA: `features/{dashboard,jobs,repo,assets,login}`) is far richer — per-repo storage usage + quota %, job stats/list/delete, asset browse/delete by node/type/all, repository registration/module/CSP management. Net: admin capability **increased**, except for the H5P library/core tooling (#6) and the H5P-content delete-by-id search page (subsumed by per-repo asset delete). No action; recorded for completeness.

### 12. [Low] Tracking size accounting differs

- **OLD:** `ESTRACK` stored only `(ESOBJECT_ID, TIME)`; eviction summed on-disk dir sizes at clean time.
- **NEW:** `TrackingEntry.binarySize` is the **directory size of the cache object root** computed at PUT time (`S3StorageService.putObjectInternal` → `getDirectorySize(bucket, rootPath)` then `trackCacheObject(..., size)`); GET/range access updates `lastAccessed` but **not** size. `AdminController.getCacheUsage` exposes `discrepancy = |actualS3Size − trackedSize|`, acknowledging drift.
- **Risk (minor):** tracked sizes can drift from real S3 occupancy (e.g. objects written outside the tracked path, or multi-quality variants), which feeds directly into the cleaner's quota-ratio math (#1). The `exact=true` reconciliation in `AdminStorageController` only works in per-customer bucket mode. **Recommendation:** none required; be aware tracked-size is an approximation driving eviction.

MZ: This is fine. Keeping super accurate score is to expensive with S3.

---

### Storage layout / bucket-key scheme (for context, not a defect)

- **OLD:** `CC_RENDER_PATH/<module>/<subUri>/<objectId>_<version>` on local disk (+ optional SAFE mirror).
- **NEW:** S3, bucket chosen by `BucketStrategy` (`@ConditionalOnStorageByCustomer` → `BucketPerCustomerStrategy` `rs2-<repoId>`; `@ConditionalOnStorageByMediaType` → `BucketPerMediaTypeStrategy`; external buckets). Object key root = `<type>/<nodeId>/<hash>` (`BucketPerCustomerStrategy.getCacheObjectRootPath`), with `_<quality>` + mime extension. Temp objects in a `temp` bucket. **In `byType` mode the S3 key has no repoId** — which is exactly why all per-repo admin/cleaner logic is **Tracking-collection-driven** (the `Tracking` doc carries `repoId`). This is an important opaque design point: repo-scoped admin/eviction correctness depends entirely on the Mongo `Tracking` collection staying consistent with S3.

### Deploy / cron — the headline opaque finding

- **OLD ran the cache cleaner OUT-OF-PROCESS on a schedule, two ways:**
  - K8s **CronJob**: `deploy/docker/helm/service/src/main/chart/templates/cronjob.yaml` runs `php func/classes.new/Helper/cacheCleaner.php`; `values.yaml` `job.cachecleaner.schedule: "0 * * * *"` (**hourly**), `suspend`, and crucially `config.useDiskSize: true`.
  - In-container **cron** (`deploy/docker/build/service/src/main/build/assets/cron.sh`): default `SERVICES_RENDERING_SERVICE_CACHE_CLEANER_SCHEDULE="0 0 * * 0"` (**weekly, Sunday 00:00**).
- **NEW:** the cleaner is an **in-process** `@Scheduled` bean on the **master** role (`CacheCleaner.kt`), default `app.cache.cleaner.schedule=0 0 1 * * *` (**daily 01:00**), wired in helm via `configmap-env.yaml` → `app.cache.cleaner.{schedule,threshold.lower,threshold.upper}`. **No `cronjob.yaml` and no `cron.sh` exist in the new deploy.**
- **Implications:** (a) cadence changed (hourly/weekly → daily); (b) the cleaner now competes for resources inside the running service instead of an isolated job pod with its own resource limits; (c) `useDiskSize` and the dedicated cleaner log config (`de.metaventis.esrender.log4php.cachecleaner.properties`) are gone. **Recommendation:** confirm the daily in-process schedule is acceptable load-wise, and that losing the isolated CronJob pod (separate resources/failure domain) is intended.

**Additional deploy jobs removed (intentional, recorded):**
- OLD helm post-install/post-upgrade **init Job** (`templates/job.yaml`, `init.sh`) that DELETE+PUT-registered the rendering service as an application in the repository — gone; replaced in-app by `edusharingRepo/RegistrationRunner.kt` + `RepositoryRegistrationConfig` (`app.repository.registration.*`).
- OLD **persistent volumes** `share-config` (1Gi) and `share-data` (5Gi) and the `persistentvolumeclaim.yaml` — gone (cache lives in S3; NEW chart has no PVC). The OLD ~380-line `entrypoint.sh` install/update/config-persist flow is gone; NEW entrypoint just launches the Spring jar with profile `docker`.
- Only other NEW scheduler is `app.cors.sync.schedule` (`CorsSyncScheduler.kt`, default 300000ms) — a new feature, no OLD counterpart.

### Verification status

The cron/cleaner/probe/threshold facts above were verified directly against the cited files (helm `values.yaml:288-290`, `cronjob.yaml`, `cron.sh`, compose, `application.properties`). The negative-helm-threshold (#7), quota-no-op (#7b), source disagreement (#13), cron-format (#14), and dropped-limits (#15) findings were corroborated by a dedicated deploy/config sub-agent and spot-checked here.
