# Verbesserungs-Roadmap

Priorisierte Bestandsaufnahme aus der Gesamtanalyse (Stand 2026-06). Sie ergänzt
[`ARCHITECTURE.md`](ARCHITECTURE.md): *was* gebaut ist steht dort, *was noch fehlt* steht hier.

**Legende Prio:** 🔴 hoch · 🟡 mittel · 🟢 niedrig.
Aufwand grob in Personentagen (PT).

## Bereits umgesetzt (Quick-Fixes)

Diese Punkte aus der Analyse sind erledigt und dienen hier nur der Nachvollziehbarkeit:

| Befund | Lösung | Datei |
|---|---|---|
| `.block()`-Calls ohne Timeout (19 Stellen) | Zentraler `ReactorClientHttpConnector` mit connect-/response-Timeout im geteilten `WebClient.Builder`; per Property steuerbar | `service/.../config/WebClientConfig.kt`, `application.properties` |
| Signatur-Algorithmus ungeprüft an `Signature.getInstance()` | Whitelist `app.security.allowed-signature-algorithms`, 400 bei Verstoß, Slice-Test | `service/.../core/RenderController.kt` |
| jupyter `HTTPException` nicht importiert + kein nbconvert-Error-Handling | Import ergänzt, `try/except` → 400 bei korruptem `.ipynb`, `on_event` → `lifespan` | `jupyter-converter/.../main.py` |
| jupyter Caret-Pinning für nbconvert | `nbconvert` auf `~7.16.4` (patch-level) | `jupyter-converter/.../pyproject.toml` |
| No-op `catch`/Dead-Code | `catch { throw it }` entfernt; toter Schlüssel-Cache-Block in `MetadataService` entfernt | `ConverterWebServiceCaller.kt`, `MetadataService.kt` |

---

## B1 · Resilienz / Robustheit

| Befund | Impact | Datei(en) | Aufwand | Prio |
|---|---|---|---|---|
| Keine Retry/Backoff-Logik für transiente Fehler (Mongo-`save`, S3, externe HTTP) | Einzelne Netzwerk-Hänger lassen Jobs fehlschlagen, obwohl ein Retry erfolgreich wäre | `renderingJob/`, `storage/`, `modules/*` | 2–3 PT | 🔴 |
| RabbitMQ ohne Dead-Letter-Queue: bei Exception wird die Message verworfen, Job nur als FAILED markiert | Fehlgeschlagene Jobs sind nicht reprozessierbar; keine Fehler-Forensik | `renderingJob/queue/`, `JobReceiver` | 1–2 PT | 🟡 |
| Kein Circuit-Breaker für Converter-/Repo-Calls | Ein dauerhaft kaputter Downstream-Dienst lastet Worker-Threads aus (Timeout greift erst pro Call) | `modules/ConverterWebServiceCaller.kt`, `edusharingRepo/services/` | 1–2 PT | 🟡 |
| Kein Graceful Shutdown für `JobReceiver` / RabbitMQ-Consumer | In-flight Jobs werden bei Pod-Restart hart abgebrochen | `renderingJob/queue/` | 1 PT | 🟢 |
| Temp-File-Cleanup nicht garantiert (`.delete()` ohne Prüfung im `finally`) | Bei Dauerlast Disk-Füllung möglich | `modules/ConverterWebServiceCaller.kt` | 0.5 PT | 🟢 |

**Empfehlung:** Resilience4j einführen (aktuell **keine** Retry-Dependency im POM) oder Spring
`@Retryable`; DLQ ist der Einstieg mit dem besten Nutzen/Aufwand-Verhältnis.

## B1b · Durchsatz / Job-Parallelität (Prefetch & Concurrency) — ✅ umgesetzt

| Befund | Impact | Datei(en) | Aufwand | Prio |
|---|---|---|---|---|
| ~~RabbitMQ-Listener fest auf **1 Job gleichzeitig** (`setPrefetchCount(1)` + `setConcurrentConsumers(1)` hartcodiert)~~ → Prefetch **und** Consumer-Anzahl pro Queue/Modul und pro Instanz konfigurierbar | Durchsatz je Instanz über die vorhandenen CPU/IO-Reserven skalierbar | `renderingJob/queue/{QueueConfig,QueueContainerConfig,TunableQueueContainerConfig}.kt` | — | ✅ |

**Umgesetzt (2026-07):**
- **Concurrency** pro Queue via `app.queue.<modul>.concurrency` (SpEL im `@RabbitListener`),
  inkl. Auto-Scaling-Range (`"1-200"` → `concurrentConsumers`/`maxConcurrentConsumers`) für die
  leichten API-Passthroughs (sodix, omega, ddb).
- **Prefetch** pro Queue modul-lokal über `QueueContainerConfig`-Beans (Convenience-Basisklasse
  `TunableQueueContainerConfig`), von der geteilten `queueListenerContainerFactory` per
  `ContainerCustomizer` eingesammelt und angewendet. Default bleibt **1** für die schweren
  Konvertierer (fair dispatch bei ungleich langen Jobs); die leichten Passthroughs (sodix,
  omega, ddb) nutzen **3**.
- **Auto-Scaling-Geschwindigkeit** der leichten Queues (sodix, omega, ddb) burst-getunt, **pro Queue
  separat** über je eine modul-lokale `@ConfigurationProperties`-Subklasse (`SodixQueueProperties`
  usw. : `QueueProperties`), die name + prefetch + das `scaling`-Profil (`BurstScaling`) aus
  `app.queue.<queue>.*` bündelt und in die `TunableQueueContainerConfig`-Bean fließt. Scaling-Knöpfe:
  `startConsumerMinInterval`, `stopConsumerMinInterval`, `consecutiveActiveTrigger`,
  `consecutiveIdleTrigger`. Burst-Defaults: schneller Ramp-up (+1 Consumer/s, Trigger 1) und Teardown
  (−1/s, 3 Idle-Zyklen Grace). In Helm über die verschachtelte `config.queue.scaling`-Map exponiert
  (rendert `app.queue.<queue>.scaling.*`); jeder Knopf unset = Framework-Default. (`key`/`concurrency`
  bleiben flach, da sie nur die `@RabbitListener`-Placeholder speisen.)
- **Pro Instanz** tunebar über Deploy-Env (`RENDERING2_QUEUE_<KEY>_CONCURRENCY` / `_PREFETCH`)
  in compose + Helm.
- Idempotenz unkritisch: Sub-Job-Ergebnisse landen pro `nodeId/hash` in S3, daher gefahrlos bei
  prefetch/concurrency > 1.
- Offen/kombinierbar: DLQ aus **B1** (höhere Parallelität → mehr Bedarf an sauberem
  Fehler-Routing) und rollenspezifische Resource-Limits (Helm-Punkt in **B2**).

**Betriebsdoku:** [`QUEUE_SCALING.md`](QUEUE_SCALING.md) — vollständige DevOps-Erklärung des
Skalierungsmechanismus (zwei Ebenen, Auto-Scaling-Algorithmus, prefetch×concurrency,
competing consumers, Config-Referenz, Tuning-Leitfaden).

## B2 · Sicherheit

| Befund | Impact | Datei(en) | Aufwand | Prio |
|---|---|---|---|---|
| Production-Cookie-Defaults: `secure`/`SameSite` per Property steuerbar, aber Default `false` (`RENDERING2_SERVICE_COOKIE_SECURE`) | Session-Cookie über HTTP übertragbar, wenn Prod-Override fehlt | `config/SessionConfig.kt` (Z74-75), compose/helm env-Defaults | 0.5 PT | 🔴 |
| Hardcoded AWS-Credentials im root `compose.yml`; permissive Defaults (`admin`, `rendering2`) in Compose-Files | Credential-Leak im Repo; unsichere Defaults gelangen ungewollt in Betrieb | `compose.yml`, `deploy/docker/compose/src/main/compose/*.yml` | 1 PT | 🔴 |
| Helm-Hardening fehlt: `runAsNonRoot: true`, `readOnlyRootFilesystem: true`, NetworkPolicies, PDB | Schwächere Container-Isolation; kein Schutz bei Container-Escape | `deploy/docker/helm/*/src/main/chart/values.yaml` | 2 PT | 🟡 |
| Kein Audit-Logging für Admin-Aktionen (Repo-Registrierung/-Löschung, Asset-Deletion) | Sicherheitsrelevante Aktionen nicht nachvollziehbar | `edusharingRepo/AdminController`, `asset/AdminAssetController` | 1 PT | 🟡 |
| Lumi nicht nachweislich per `repoId` isoliert (TODO im Code) | Multi-Tenant-Isolation für H5P-Inhalte verifizieren | `modules/h5p/lumi/LumiContentManagementService.kt:19` | 1–2 PT | 🟡 |

## B3 · Tests & CI

| Befund | Impact | Datei(en) | Aufwand | Prio |
|---|---|---|---|---|
| `document-converter`, `jupyter-converter`, `lumi`: **0 % Test-Coverage** | Keine Regressions-Sicherheit für die Konverter | jeweils `src/test/` (neu) | je 1–3 PT | 🔴 |
| `admin-frontend`: nur 1 Smoke-Test, keine E2E | Admin-Workflow (Login → Repo → Storage/Jobs/Assets) ungetestet | `admin-frontend/src/main/frontend/` | 2–3 PT | 🟡 |
| CI-Pipeline prüft nur den OpenAPI-Contract-Test | Unit-/Integrationstests, Coverage, Security-Scans laufen nicht im CI | `.gitlab-ci.yml` | 1–2 PT | 🟡 |
| Keine Dependency-/Container-Scans, kein Dependabot/Renovate | Bekannte CVEs in Abhängigkeiten bleiben unbemerkt | `.gitlab-ci.yml`, `.pre-commit-config.yaml` | 1 PT | 🟡 |
| Security-Tests für Signatur-Whitelist (positiv/negativ) und CORS-Header-Herkunft fehlen | Sicherheits-Regressionen unbemerkt | `service/src/test/` | 0.5 PT | 🟢 |

## B4 · Observability

| Befund | Impact | Datei(en) | Aufwand | Prio |
|---|---|---|---|---|
| Keine fachlichen Micrometer-Metriken (Job-Duration, Success/Failure-Rate, Queue-Depth) | Betriebsblindheit bei Job-Problemen | `renderingJob/` | 1–2 PT | 🟡 |
| OpenTelemetry vorbereitet, aber kein Exporter konfiguriert (`OTEL_EXPORTER_OTLP_ENDPOINT`) | Traces enden im Nichts, kein verteiltes Tracing nutzbar | `application-docker.properties`, helm/compose | 0.5 PT | 🟡 |
| Prometheus `ServiceMonitor` + Alert-Rules per Default deaktiviert (`global.metrics.*.enabled: false`) | Metriken werden ohne manuelles Opt-in nicht gescrapt | `deploy/docker/helm/*/values.yaml` | 0.5 PT | 🟢 |
| Kein Structured (JSON) Logging fürs Prod-Profil | Log-Aggregation/Korrelation erschwert | `application*.properties` | 0.5 PT | 🟢 |

## B5 · Dokumentation

| Befund | Impact | Datei(en) | Aufwand | Prio |
|---|---|---|---|---|
| Root-`README.md` minimal; kein `DEVELOPMENT.md` (Quick-Start, Voraussetzungen) | Onboarding neuer Entwickler langsam | `README.md`, `DEVELOPMENT.md` (neu) | 1 PT | 🟡 |
| `docs/ARCHITECTURE.md` nicht aus Root-`CLAUDE.md` verlinkt | Gute Doku wird übersehen | `CLAUDE.md` | 0.1 PT | 🟢 |
| Kein `.nvmrc`/`engines` für `lumi` und `admin-frontend` | Node-Version-Drift lokal ↔ Docker | `lumi/`, `admin-frontend/src/main/frontend/` | 0.2 PT | 🟢 |

---

## Bewusst NICHT übernommene Analyse-Befunde (Falsch-Positive)

Bei der Verifikation am Code als unbegründet eingestuft — hier dokumentiert, damit sie nicht
erneut „gefixt" werden:

- **„AuthTokenFilter ignoriert JWT-Fehler / lässt Request durch"** — Normales
  Spring-Security-Muster: der Filter setzt nur den `SecurityContext`; fehlt ein gültiges JWT,
  greift später die Autorisierung. Kein Bug. (`security/AuthTokenFilter.kt`)
- **„jupyter-converter startet wegen fehlendem Import nicht"** — Der Import fehlte real, aber
  der betroffene `else`-Zweig war bei `file: UploadFile` (required) unerreichbar; der Service
  startete problemlos. (Trotzdem im Zuge der Quick-Fixes sauber behoben.)
