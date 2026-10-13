# Robustheits-Audit: Software und angebundene Subsysteme

Stand: 2026-10-09 · Auslöser: RENDER-177 („Startup von falscher Moodle URL blockiert kompletten Startup“)

Umfang: `service`, `document-converter`, `jupyter-converter`, `lumi`, `deploy` (Helm, Compose, Dockerfiles).
Methode: reine Code-Durchsicht, nichts gebaut oder ausgeführt. Befunde mit „(prüfen)“ sind Hypothesen, bei denen das Framework-Verhalten nicht verifiziert wurde.

## 1. Einordnung RENDER-177

Die Jackson-Exception aus dem Ticket ist im Code bereits entschärft: `parseMoodlePingResponse` fängt Fehlerobjekte ab, und `RegistrationRunner` fängt Fehler pro Modul ab. Das Muster dahinter ist aber noch dreifach offen:

1. **Entrypoint-Wait ohne Limit.** `entrypoint.sh:47-53` wartet endlos auf jedes konfigurierte Repository, ohne `curl`-Timeout. Ein einziges nicht erreichbares Repo verhindert den JVM-Start, ein Neustart hilft nicht.
2. **Synchroner Runner.** Der `RegistrationRunner` arbeitet alle Repos nacheinander ab und läuft vor der Readiness. Hängt ein Aufruf, wird der Pod nie ready, die Startup-Probe killt ihn nach ca. 15 Minuten. Das Retry-Budget (ca. 2 Min. pro Repo) kann den 5-Minuten-Lock überschreiten, dann registriert ein zweiter Master parallel mit `force=true`.
3. **Kein Retry nach dem Start.** Schlägt Registrierung oder Modulaktivierung beim Start fehl, bleibt das Repo unregistriert bzw. das Modul inaktiv bis zum Neustart. Der Kommentar im `RegistrationCorsSyncRetrier` („fails application startup“) ist veraltet.

## 2. Kritisch

| # | Befund | Stelle | Fix |
|---|---|---|---|
| K1 | Endlos-Wait auf Repos und Infrastruktur im Entrypoint; `cut -d'=' -f2` kürzt URLs mit `=` | `deploy/docker/build/service/src/main/build/assets/entrypoint.sh:47-53` | Repo-Wait entfernen oder mit Deadline und `curl --connect-timeout 3 -m 5` versehen, `cut -d= -f2-`. Mongo/Redis/Rabbit/S3 mit Deadline, danach `exit 1`. |
| K2 | Runner blockiert Start, kein Gesamtdeckel, kein späterer Retry | `service/.../edusharingRepo/RegistrationRunner.kt:38-115` | Registrierung asynchron nach `ApplicationReadyEvent`, parallel pro Repo mit Timeout. ShedLock-Scheduler wiederholt offene Registrierungen/Modulaktivierungen mit Backoff. Lock-Dauer ans Budget koppeln. |
| K3 | Moodle-Client umgeht `WebClientConfig` (kein Connect-/Response-Timeout); `getWebserviceToken(...).block()` ohne `.timeout` | `modules/moodle/MoodleRenderModule.kt:74-77, 169-181` | Injizierten Builder per `clone()` nutzen, `.timeout` und `.onErrorMap` mit lesbarer Meldung ergänzen. |

## 3. Hoch

- **H1 Hauptjob hängt in PROCESSING.** Schlägt nach `save(PROCESSING)` auch das Speichern von FAILED fehl (z. B. Mongo-Blip), wird die Nachricht verworfen. Der Job bleibt ohne Sub-Jobs stehen, der Reaper erfasst ihn nicht, der Unique-Index `activeJobPerNodeHash` blockiert den Node dauerhaft (`renderingJob/queue/JobReceiver.kt:43,69-76`, `StaleJobReaper.kt`). Fix: Reaper-Pass für PROCESSING-Hauptjobs ohne Sub-Jobs.
- **H2 Publish nach Save nicht atomar.** Bei RabbitMQ-Ausfall oder Nack bleibt ein QUEUED-Job mit Dedup-Flag zurück, Folgeanfragen bekommen dessen ID (`MainJobCreationService.kt`, `QueueConfig.kt`). Fix: Kompensation bei Publish-Fehler; Nack per `correlationData` auf die Job-ID abbilden; alternativ Outbox.
- **H3 Keine Timeouts für Mongo und RabbitMQ.** `socketTimeoutMS` unendlich; Rabbit ohne `connection-timeout` und Heartbeat (`application.properties:85-103, 299-304`).
- **H4 Readiness prüft keine Abhängigkeiten.** Keine Health-Groups; fällt Mongo/Redis aus, bleibt der Pod ready und liefert 500er. (Positiv: Liveness hängt an keinem Subsystem.)
- **H5 Graceful Shutdown für HTTP fehlt** (`server.shutdown` nicht gesetzt, prüfen).
- **H6 Credentials in URLs und damit in Logs.** Moodle: Passwort (`token.php?username&password`) und `wstoken` in der Query (`MoodleRenderModule.kt:171-178`, `MoodleUploadService.kt:189,278,302,326`); `WebClientException.message` enthält die URI, geloggt u. a. in `RegistrationRunner.kt:86,105,108` und `MoodleUploadService.kt:333`. DDB: `oauth_consumer_key` in der Query (`DdbApiService.kt:61,95,124`). Fix: POST mit Form-Body bzw. Sanitizer im WebClient-Filter.
- **H7 `ConverterWebServiceCaller` ohne Retry/Fehlerklassifikation** (`.block()` ohne `.timeout`). Ein Converter-Neustart markiert alle laufenden Jobs dauerhaft als FAILED.
- **H8 Binder-SSE.** Client umgeht `WebClientConfig`, kein Idle-Timeout; im Fehlerpfad wird `processMainJob` nicht aufgerufen; ohne Terminal-Event bleibt der Sub-Job PROCESSING (`BinderUploadService.kt:61-84,124`).
- **H9 `clearRegistrations()` vor Remote-Calls.** Schlägt `about()` danach fehl, ist die Registrierung weg, sofern keine echte Mongo-Transaktion greift (nicht geprüft) (`RepositoryRegistrationService.kt` ~96-101).
- **H10 Document-Converter.** Ergebnis komplett im RAM (bis 128 MB Upload, ca. 1 Gi Heap) → OOM-Risiko; kein LibreOffice-HealthIndicator, ein toter Office-Prozess bleibt ready (`ConversionService.kt`, `statefulset.yaml:42-73`).

## 4. Mittel

- **Redelivery:** STANDARD-Receiver ohne Status-Guard (`ImageReceiver` geprüft, übrige nicht im Detail); keine DLQ (`QueueConfig.kt`).
- **Keine Retries für idempotente GETs** (Sodix, Omega, DDB, GitHub, Lumi). Bewusst *nicht* wiederholen: Moodle `restore_course`/`scorm_course`/`local_edusharing_user`, Onyx-POST, Lumi-POST, Converter-POST.
- **Kein Circuit-Breaker/Bulkhead.** Fällt ein Modul aus, bindet jeder Job das volle Timeout; `PoolAcquirePendingLimitException` wird nicht gemappt.
- **Unerwartete Antwortformate** (HTML/Fehlerobjekt statt JSON) in Sodix, Omega, Lumi, H5P-Upload; NPE-Risiko durch `get()` statt `path()` und Jackson-2-Mapper in `MoodleUploadService.kt:29,316`.
- **Lumi:** `getBucketInfo`/`deleteContent` ohne `.timeout` (bis 10 Min.); `LumiProxyService.kt:69-76` leitet alle Request-Header inkl. Cookie/JWT 1:1 weiter.
- **S3-Client ohne Konfiguration** (SDK-Defaults, 50 Verbindungen, Streams halten sie bis zum Ende); Race bei `createBucket` zwischen Pods (`S3Config.kt`, `S3StorageService.kt:533`).
- **Standalone-Redis ohne Timeouts** (60 s Command-Timeout; nur der Cluster-Pfad ist gehärtet, `RedisConfig.kt:60-67`); Spring Session führt beim Start `CONFIG SET` aus (prüfen).
- **Mongo-WriteConcern UNACKNOWLEDGED** für Tracking/Registrierungen (`MongoConfig.kt`), obwohl Cache-Cleaner und Quota daran hängen.
- **Scheduler nur 2 Threads** bei mehreren langen ShedLock-Tasks, der Reaper kann verhungern (`SchedulingConfig.kt`).
- **CORS-Sync:** Ein Fehler bricht die Schleife über alle Repos ab (`CorsSyncService.kt:35`); Timeouts des OkHttp-ApiClient ungeprüft.
- **Jupyter-Converter:** `async def` mit synchronem nbconvert blockiert den Event-Loop; ein Worker, keine Größenlimits, nur `tcpSocket`-Liveness (`main.py:93-108`).
- **Container-Betrieb:** `exec sh -c "$@"` im Entrypoint, SIGTERM erreicht die JVM evtl. nicht (prüfen); PDB `minAvailable: 1` bei `replicaCount: 1` blockiert Node-Drains; UID-Mismatch Image (1001/1002) vs. `runAsUser: 1000`; lumi ohne `unhandledRejection`-Handler und mit 10-s-Zwangs-Shutdown trotz 15-Min-Imports; Temp-Dateien bleiben nach Kill liegen.
- **Compose:** Healthchecks und `service_healthy` fehlen für die meisten Dienste.
- **Token im Log:** Binder-Token im Info-Log (`BinderUploadService.kt:61-64,130`).

## 5. Niedrig

ShedLock ohne Keep-Alive; `S3HealthIndicator` nutzt `listBuckets()` ohne Timeout; `channelLocks` wächst nach Reconnects; ungepinnter `async-profiler`-Download im Prod-Image; EOL-Basisimages; Readiness `failureThreshold: 1`; kein `preStop`; `Content-Disposition` ungequotet; Omega-HEAD liefert bei manchen CDNs 405.

## 6. Bereits gut gelöst

Liveness hängt an keinem Subsystem; DMLC mit `missingQueuesFatal=false` und Reconnect; Idempotenz-Guards in `JobReceiver` und REMOTE-Receivern; `StaleJobReaper` mit ShedLock; Redis-Cluster-Timeouts; Defaults und Timeout-Staffelung im Document-Converter; Limits und Probes in allen Charts; lumi mit Import-Queue, Deadline und Exit 1 bei Startfehlern.

## 7. Empfohlene Reihenfolge

1. **Sofort (Ticket-Ursache):** K1, K2, K3.
2. **Sicherheit, schnell machbar:** H6 (Sanitizer), Lumi-Header-Allowlist, Binder-Token-Logging.
3. **Datenkonsistenz:** H1, H2, H9.
4. **Konfiguration:** H3, H4, H5, Redis-Standalone-Timeouts, S3-Client, WriteConcern, Scheduler-Pool.
5. **Resilienz-Schicht:** GET-Retries mit Backoff, Circuit-Breaker/Bulkhead pro Modul, Converter-Retry.

## 8. Entscheidungsprotokoll zu den offenen Punkten (Stand 2026-10-13)

| Fall | Thema | Entscheidung | Stand |
|---|---|---|---|
| 1 | Lumi-Proxy liefert 500 statt 503 | Alle „Lumi nicht erreichbar“-Fälle im Proxy konsistent abbilden: offener Breaker/Bulkhead/Pool 503 mit `Retry-After`, Verbindungsfehler 502, Timeout 504; Nutzertext `GENERIC_MODULE_NOT_AVAILABLE`, WARN statt ERROR-Stacktrace | in Umsetzung (Branch `..._lumi-paths`) |
| 2 | Binder-Token im Klartext in MongoDB | Verschlüsseln (AES-256-GCM, generisch): Privatschlüssel, Modul-Credentials und `SubJob.secretData` (intern, nie an den Client). Einstufiger Rollout, keine Rotation, Schlüssel = eigenes Secret mit Default Admin-Passwort | umgesetzt (Branch `maven/feature/10.0_secret-encryption`) |
| 3 | Lumi-Upload-POST und Streaming-Proxy hinter den Breaker | Eigene Guards `lumi-upload` und `lumi-proxy`. Proxy: Breaker **und** Bulkhead (der Breaker nimmt Druck von Lumi, ein Bulkhead allein täte das nicht), Header-Timeout 20 s statt 120 s, kein Retry, nur Erreichbarkeitsfehler zählen (kein 500), 10 s offen | in Umsetzung (Branch `..._lumi-paths`) |
| 4 | Breaker pro Pod, nur aufeinanderfolgende Fehler | Unverändert lassen, Grenzen dokumentieren, Alarmregeln vorschlagen | in Umsetzung (Doku im selben Branch) |
| 5 | `readOnlyRootFilesystem` | Vorbereiten, im Default aus. Schreibpfade gemessen (`docker diff`, `--read-only`): alle fünf Images laufen mit beschreibbarem `/tmp` (document-converter zusätzlich `HOME=/tmp`, jupyter-converter `PYTHONDONTWRITEBYTECODE=1`, lumi mit Library-Cache-Volume). Aktivierung erst nach Staging-Test unter Last | in Umsetzung (Branch `..._readonly-fs`) |
| 6 | `--enable-preview` im Entrypoint | Entfernt: keine der rund 59 000 Klassen trägt das Preview-Flag, kein Preview-API im Quelltext, Tests laufen ohne das Flag, document-converter ohne Flag geprüft | umgesetzt |

## 9. Eigene Themen und Folgevorhaben

### Virtuelle Threads für den Webserver (Tomcat)
- **Heute:** Nur der RabbitMQ-Listener-Executor läuft auf virtuellen Threads (`QueueConfig.rabbitConsumerExecutor`). `spring.threads.virtual.enabled` ist bewusst **nicht** global gesetzt, Tomcat nutzt Plattform-Threads (Standard etwa 200, nirgends konfiguriert).
- **Motivation:** Der Lumi-Proxy blockiert pro Anfrage einen Tomcat-Thread (`.block()` bis zu den Antwort-Headern). Mit virtuellen Threads wäre diese Blockade billig, das Argument „Tomcat-Threads gehen aus“ entfiele weitgehend.
- **Wichtig für die Resilienz-Schicht:** Der Tomcat-Thread-Pool ist heute eine stille, implizite Obergrenze für alle gleichzeitigen Anfragen. Mit virtuellen Threads entfällt sie. Der **Bulkhead wird dann wichtiger, nicht unwichtiger**, weil er die einzige Begrenzung gegenüber Lumi (ein Node-Prozess), Connection-Pools und Speicher ist. Der Breaker bleibt nötig, um Druck von einer kranken Gegenseite zu nehmen. Die Bulkhead-Grenzen müssten vor einer Aktivierung neu bewertet werden.
- **Risiken (allgemeines Wissen, am Stack zu prüfen):** Pinning unter Java 21 (`synchronized` mit blockierender I/O hält den Träger-Thread fest, behoben erst mit JEP 491 in Java 24; `-Djdk.tracePinnedThreads` zum Aufspüren), ThreadLocal-Kontext (Tracing, MDC, Spring Security; für die Listener gibt es schon einen `TaskDecorator`), Speicherbedarf vieler gleichzeitig blockierter Anfragen, andere Thread-Dumps. Ersetzt keine kurzen Timeouts.
- **Vorgehen:** Eigenes Vorhaben mit Lasttest (k6-Setup unter `loadtest/` vorhanden), zunächst nur für die Controller-Rolle. Nicht Teil von RENDER-178.

### Weitere Folgethemen
- **Dead-Letter-Queue** (M1 des Audits): Aufwand etwa 1 bis 2 Tage. Queue-Argumente sind in RabbitMQ unveränderlich, die risikoärmere Variante ist eine Policy statt Argumenten; bringt vor allem Sichtbarkeit und Replay, der Reaper heilt die meisten Fälle schon.
- **Breaker-Zustand pod-übergreifend oder Ratenfenster:** nur bei einem realen Fall, in dem eine Gegenseite teilweise kaputt war und der Breaker nicht öffnete.
- **Compose:** Es fehlt ein `rendering2-jupyter-converter`-Service, obwohl `deploy/CLAUDE.md` ihn nennt.
- **Vor dem Release real zu prüfen:** DDB-Header (Default bleibt `QUERY`), Moodle-POST gegen eine echte Instanz, Entrypoint und SIGTERM im Alpine-Image, UID-Wechsel auf 1000 mit bestehenden PVCs, Helm gegen einen Cluster, Rabbit-Nack/Unroutable gegen einen echten Broker, Rolling Update mit verschlüsselten Secrets, `readOnlyRootFilesystem` unter Last.
