# RS2 Converter-Lasttest

Bewertet die einzelnen Converter unter Last und macht das Skalierungsverhalten des Rendering
Service (Consumer-Auto-Scaling, Queue-Tiefe/Backpressure, Erholung nach Burst) messbar. Ergänzt
die Betriebsdoku [`../docs/QUEUE_SCALING.md`](../docs/QUEUE_SCALING.md).

Es gibt zwei Phasen:
- **Phase A (lokal, hier beschrieben)** — compose-Stack, schnelle Iteration, per-Converter-Baseline,
  Skalierungs-**Ebene 1** (Consumer-Threads pro Instanz).
- **Phase B (k8s)** — Cluster mit HPA, Skalierungs-**Ebene 2** (Pod-Autoscaling + competing
  consumers). Siehe [`k8s/README.md`](k8s/README.md).

## Wie es funktioniert

Mit `app.security.enabled=false` (im `debug`-Profil) greift ein Test-Bypass, der ohne
Mock-Repository und ohne Signatur auskommt:
- `repoId` mit Präfix `TEST` → synthetische Repository-Registrierung.
- `nodeId` mit Präfix `TEST_` → Quelldatei vom Classpath, z. B. `TEST_loadtest/image.jpg` →
  `classpath:loadtest/image.jpg` (Sample-Dateien unter
  `../service/src/main/resources/loadtest/`).

Der k6-Client feuert `POST /public/renderdata` mit einem minimalen Node-JSON (der `mimetype`
steuert den Ziel-Converter) und pollt `GET /public/job` bis zum terminalen Status. Ein pro
Request eindeutiger `content.hash` erzeugt jedes Mal einen Cache-Miss → echte Konvertierung.

## Voraussetzungen

- Docker + docker compose
- [k6](https://k6.io/docs/get-started/installation/) (lokal installiert)
- JDK 21 (für den Service aus der IDE bzw. via `mvnw`)

## Phase A — Schritt für Schritt

### 1. Infrastruktur + Converter-Sidecars starten (root-compose.yml)

```bash
docker compose up -d          # rustfs(S3), rabbitmq, mongo, redis, document-converter, jupyter, lumi, thirdparty-mock
```

Ports: RabbitMQ-UI :15672 (rendering/rendering), RabbitMQ-Prometheus :15692, S3-Console :9111,
Drittanbieter-Mock (sodix/omega/ddb) :8092.

### 2. Beobachtungs-Stack starten (Prometheus + Grafana)

```bash
docker compose -f loadtest/observability/docker-compose.observability.yml up -d
```

Grafana: http://localhost:3001 (admin/admin) → Dashboard **„RS2 Lasttest"** (vorprovisioniert).
Prometheus: http://localhost:9091.

### 3. Service mit loadtest-Profil starten (aus der IDE oder via mvnw)

```bash
DEV=true ./service/mvnw -Pdev -pl service spring-boot:run \
  -Dspring-boot.run.profiles=debug,loadtest
```

> Wichtig: **`debug,loadtest`** in dieser Reihenfolge — `loadtest` überschreibt `debug`
> (optionale Module + lumi-Port 9112). Prüfen: `curl -s localhost:9080/actuator/prometheus | grep rendering_`.

### 4. Lasttest fahren

```bash
# ein Converter, Scaling-Kurve sichtbar machen:
k6 run -e PROFILE=ramping -e VUS=30 loadtest/k6/scenarios/image.js

# Sättigung/Backpressure:
k6 run -e PROFILE=arrival -e RATE=40 -e DURATION=3m loadtest/k6/scenarios/document.js

# Burst (Ramp-up/Teardown der Auto-Skalierung):
k6 run -e PROFILE=burst -e VUS=50 loadtest/k6/scenarios/video.js

# gemischte Last über mehrere Queues:
k6 run -e PROFILE=ramping -e VUS=40 loadtest/k6/scenarios/all.js
```

Während des Laufs im Grafana-Dashboard beobachten: Consumer-Count pro Queue, Queue-Tiefe,
Sub-Job-Dauer p95, Durchsatz, Fehlerrate.

## Szenarien & Profile

| Szenario-Datei | Converter | Sidecar nötig |
|---|---|---|
| `scenarios/image.js` | IMAGE (in-process) | – |
| `scenarios/audio.js` | AUDIO (ffmpeg in-process) | – |
| `scenarios/video.js` | VIDEO (ffmpeg in-process) | – |
| `scenarios/document.js` | DOCUMENT (txt→pdf) | document-converter :8081 |
| `scenarios/pdf.js` | PDF (copy-through) | – |
| `scenarios/jupyter.js` | JUPYTER | jupyter-converter :9120 |
| `scenarios/sodix.js` | SODIX (Import) | thirdparty-mock :8092 (**Antwort 1–2 s**) |
| `scenarios/omega.js` | OMEGA/DE.FWU (Import) | thirdparty-mock :8092 |
| `scenarios/ddb.js` | DDB (Import, 2 API-Calls/Job) | thirdparty-mock :8092 |
| `scenarios/all.js` | gemischt | je nach `MIX` |

Profile (`-e PROFILE=`): `smoke` (Funktions-Check), `ramping` (Scaling-Kurve),
`arrival` (Sättigung), `burst` (Ramp-up/Teardown). Feintuning: `VUS`, `RATE`, `DURATION`, `RAMP`.

H5P ist nicht vorkonfiguriert: es braucht eine echte `.h5p`-Datei unter
`service/src/main/resources/loadtest/`, den lumi-Sidecar (:9112) und das optionale Modul H5P
(im loadtest-Profil aktiv). Anschließend den auskommentierten `h5p`-Eintrag in
`k6/lib/config.js` aktivieren.

## Drittanbieter-Import-Module (sodix / omega / ddb)

Diese Module konvertieren nichts lokal — sie reihen einen Job ein, dessen Receiver eine externe
API aufruft (playout-/stream-URL bzw. DDB-Metadaten auflöst). Der Lasttest fährt sie gegen einen
**WireMock-Container** (`rendering2-thirdparty-mock`, Host-Port `:8092`), der alle drei über
Pfad-Präfixe bedient. So lässt sich das Consumer-Scaling der `sodix_job_queue`/`omega_job_queue`/
`ddb_job_queue` unter realistischer API-Latenz messen, ohne echte Fremdsysteme zu treffen.

```bash
k6 run -e PROFILE=ramping -e VUS=40 loadtest/k6/scenarios/sodix.js
k6 run -e PROFILE=arrival -e RATE=30 -e DURATION=3m loadtest/k6/scenarios/omega.js
k6 run -e PROFILE=burst   -e VUS=50 loadtest/k6/scenarios/ddb.js
# oder gemischt:
k6 run -e PROFILE=ramping -e VUS=40 -e MIX=sodix,omega,ddb loadtest/k6/scenarios/all.js
```

Wichtige Punkte:

- **Sodix-Latenz:** Der Sodix-Stub verzögert jede Antwort um **1–2 s** (`delayDistribution`
  `uniform` 1000–2000 ms in `mocks/wiremock/mappings/sodix.json`) — das ist der Realwert der
  echten API. Das macht die sodix-Queue zum interessantesten Scaling-Kandidaten (langsame,
  I/O-gebundene Consumer). Zum Variieren der Latenz nur die Werte im Mapping ändern und den
  Container neu starten (`docker compose restart rendering2-thirdparty-mock`).
- **Verdrahtung der URLs:** Sodix und Omega beziehen ihre `baseurl` aus den Repository-Credentials
  (`application-loadtest.properties` → `...module.{SODIX,OMEGA}.credentials.baseurl`). DDBs
  Basis-URLs waren früher hartkodiert; sie sind jetzt über `app.module.ddb.rest-api-base-url` /
  `app.module.ddb.iiif-api-base-url` konfigurierbar (Default = Produktions-URLs) und zeigen im
  loadtest-Profil auf den Mock.
- **Dispatch:** Nicht über mimetype/mediatype, sondern über Node-Felder — sodix/omega via
  `ccm:replicationsource` (`SODIX` bzw. `DE.FWU`), ddb via `node.remote.repository.repositoryType`
  (`DDB`). `k6/lib/renderdata.js` setzt diese Felder anhand des Modul-Katalogs. Omega fällt bei
  „local content" durch, daher setzt der omega-Eintrag ein `cclom:location` (→ `hasLocalContent=false`).
- **Mappings anpassen:** Die Stubs liegen unter `loadtest/mocks/wiremock/mappings/`. WireMock lädt
  sie beim Start; nach Änderungen Container neu starten oder das WireMock-Admin-API nutzen.
- **Phase B (k8s):** Der Mock läuft im Cluster als eigenes Deployment (dieselben Mappings via
  ConfigMap). Ablauf und Service-Verdrahtung siehe [`k8s/README.md`](k8s/README.md) §6 und das
  Manifest [`k8s/thirdparty-mock.yaml`](k8s/thirdparty-mock.yaml).

## Testbedingungen variieren

Die Queue-Knöpfe sind ohne Rebuild per Property/Env setzbar (siehe `../docs/QUEUE_SCALING.md`
§5). Pro Bedingung Service neu starten, k6-Lauf wiederholen, Grafana-Snapshot vergleichen —
z. B. feste vs. auto-skalierende `concurrency`, `prefetch` 1 vs. >1, aggressivere Scaling-Trigger,
H5P single-active-consumer.

## Neue Metriken (Referenz)

| Metrik | Typ | Bedeutung |
|---|---|---|
| `rendering_queue_consumers_active{queue}` | Gauge | aktive Consumer-Threads pro Queue (Ebene-1-Scaling) |
| `rendering_subjob_duration_seconds{module,quality,outcome}` | Timer/Histogram | Dauer einer Einzel-Konvertierung |
| `rendering_job_duration_seconds{module,outcome}` | Timer/Histogram | End-to-End-Job-Dauer |
| `rendering_job_failed_total{module,outcome}` | Counter | fehlgeschlagene Jobs |
| `rendering_subjob_failed_total{module,quality}` | Counter | fehlgeschlagene Sub-Jobs |
| `rendering_redis_commands_pending` | Gauge | an Lettuce übergebene, noch nicht abgeschlossene Redis-Kommandos; wächst im Redis-Ausfall unbegrenzt (Lettuce puffert statt abzulehnen) |
| `rendering_redis_connections_active` | Gauge | aktuell verbundene Lettuce-Connections; fällt im Ausfall auf 0 |
| `rendering_redis_disconnects_total` | Counter | Verbindungsabbrüche zu Redis (auch pro fehlgeschlagenem Reconnect) |
| `rendering_redis_errors_total` | Counter | von Lettuce gemeldete Verbindungsfehler |

Dazu die nativen RabbitMQ-Metriken pro Queue über den Detail-Endpoint
(`rabbitmq_detailed_queue_messages_ready/_unacked`, `:15692/metrics/detailed?family=queue_coarse_metrics`).
Die aggregierte `/metrics`-Ansicht kennt keine per-Queue-Serien.

## Fehlerbehebung

**Alle `POST /public/renderdata` scheitern (k6: `renderdata 200` = 0 %), Dashboard „No data".**
Der Request wird korrekt deserialisiert, scheitert aber intern an S3:
`500 "Bad Request (Service: S3, Status Code: 400)"`. Ursache: im S3-Modus `byType` bildet der
Service den Bucket-Namen aus dem `mediatype` (`rs2-<mediatype>`). Ein leerer `mediatype` ergibt
`rs2-` — ein ungültiger S3-Bucket-Name (endet auf `-`) → S3 lehnt jeden Request mit 400 ab, es
entsteht kein Job, daher bleiben alle Business-Panels leer. Jedes Modul in `k6/lib/config.js`
**muss** einen `mediatype` setzen (`moduleConfig()` wirft sonst laut). Schnelltest:
```bash
curl -s -X POST localhost:8080/public/renderdata -H 'Content-Type: application/json' \
  -d '{"nodeId":"TEST_loadtest/image.jpg","repoId":"TEST_LOADTEST","securedNode":"'$(printf '{"ref":{"id":"TEST_loadtest/image.jpg","repo":"TEST_LOADTEST"},"mediatype":"image","mimetype":"image/jpeg","content":{"hash":"t1"}}' | base64 -w0)'","signature":"'$(printf x|base64)'","signatureAlgorithm":"SHA256withRSA"}'
# erwartet: {"objectLinks":null,"jobId":"...","module":"IMAGE"}
```

**Video-Szenario meldet dauerhaft `PARTIALLY_FAILED`.** RS2 macht kein Upscaling
(`VideoConversionService`: `No Upscaling from <h> to <target>`). Bei den konfigurierten
Zielauflösungen 480/720/1080 scheitern alle Stufen oberhalb der Quellhöhe. Die Fixture
`service/src/main/resources/loadtest/video.mp4` muss daher **≥ 1080p** sein, damit alle drei
Stufen als Downscale durchlaufen (und der Video-Converter mit 3 Encodes/Job realistisch belastet
wird). Neu erzeugen:
```bash
ffmpeg -y -f lavfi -i testsrc=duration=6:size=1920x1080:rate=25 -f lavfi -i sine=frequency=440:duration=6 \
  -c:v libx264 -pix_fmt yuv420p -preset veryfast -crf 28 -c:a aac -b:a 96k -shortest \
  service/src/main/resources/loadtest/video.mp4
```

**Geänderte Fixtures greifen nicht.** Der Service lädt sie vom Classpath
(`target/classes/loadtest/`). Nach dem Austausch einer Datei unter `src/main/resources/loadtest/`
den Service neu bauen/starten (bei `spring-boot:run` reicht ein Neustart) — oder für einen
laufenden Prozess die Datei zusätzlich nach `service/target/classes/loadtest/` kopieren.
