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

## B1b · Durchsatz / Job-Parallelität (Prefetch & Concurrency)

| Befund | Impact | Datei(en) | Aufwand | Prio |
|---|---|---|---|---|
| RabbitMQ-Listener fest auf **1 Job gleichzeitig**: `setPrefetchCount(1)` + `setConcurrentConsumers(1)` hartcodiert (`singlePrefetchConnectionFactory`) | Jede Instanz arbeitet pro Listener nur **einen** Job ab; Durchsatz skaliert nur über mehr Pods, nicht über vorhandene CPU/IO-Reserven einer Instanz | `renderingJob/queue/QueueConfig.kt` (Z22-23) | 1–2 PT | 🟡 |

**Ziel:** Prefetch-Count und Consumer-Anzahl konfigurierbar machen — idealerweise **pro Modul
und pro Instanz** tunebar, damit z.B. der Image-Converter viele leichte Jobs parallel abarbeitet,
während ein RAM-intensiver AV-/Document-Job konservativ bei wenigen Consumern bleibt.

**Skizze der Umsetzung:**
- `prefetchCount` / `concurrentConsumers` (+ optional `maxConcurrentConsumers` für dynamisches
  Hochskalieren) als Properties exponieren, z.B.
  `app.queue.<modul>.prefetch-count` / `app.queue.<modul>.concurrency` mit globalem Default.
- Entweder pro Modul eine eigene `RabbitListenerContainerFactory` registrieren und im jeweiligen
  `@RabbitListener(containerFactory = ...)` referenzieren, oder die Werte per SpEL direkt im
  `@RabbitListener(concurrency = "...")` aus den Properties ziehen.
- Achtung Reihenfolge/Idempotenz: Prefetch > 1 bzw. mehrere Consumer heben die strikte
  Sequenzierung auf — sicherstellen, dass Sub-Jobs idempotent sind (sie sind es i.d.R., da
  Ergebnisse pro `nodeId/hash` in S3 landen). Werte modulweise konservativ defaulten.
- Sinnvoll zu kombinieren mit der DLQ aus **B1** (höhere Parallelität → mehr Bedarf an sauberem
  Fehler-Routing) und mit rollenspezifischen Resource-Limits (siehe Helm-Punkt in **B2**).

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
