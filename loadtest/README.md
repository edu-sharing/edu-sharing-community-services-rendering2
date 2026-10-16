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
| `scenarios/video-mix.js` | großes Video + gleichzeitige einfache Last (siehe [Video unter Last](#video-unter-last-masterworker-cluster-lokal)) | je nach `MIX` |

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

## Virtual-Thread-Pinning bei CPU-gebundenen Modulen (image) reproduzieren

Hypothese: `image` (in-process, `ImageConversionService`) läuft wie alle Converter-Queues auf
`rabbitConsumerExecutor` (virtuelle Threads, `QueueConfig.kt`). Deren Carrier-Pool ist
standardmäßig `Runtime.availableProcessors()` groß. `ImageConversionService` nutzt Java2D/ImageIO
(`BufferedImage`, `Graphics2D`, `ImageIO`) — deren JDK-Interna verwenden `synchronized`-Blöcke, die
auf Java 21 (vor JEP 491 "Unpinning", JDK 24) einen virtuellen Thread für die gesamte
Konvertierungsdauer an seinen Carrier **pinnen**. Auf CPU-knapp limitierten Pods (Prod: die
`converter`-Rolle) kollabiert die effektive Parallelität dadurch auf ~Kernzahl, unabhängig von
`app.queue.image.concurrency`.

So reproduzieren:

1. Service mit produktionsnah reduzierter Kernzahl **und** Pinning-Tracing starten:

   ```bash
   DEV=true ./service/mvnw -Pdev -pl service spring-boot:run \
     -Dspring-boot.run.profiles=debug,loadtest \
     -Dspring-boot.run.jvmArguments="-XX:ActiveProcessorCount=2 -Djdk.tracePinnedThreads=full"
   ```

   `-XX:ActiveProcessorCount=2` zwingt `Runtime.availableProcessors()` — und damit den
   Default-Carrier-Pool der virtuellen Threads — auf 2, unabhängig von der echten Kernzahl der
   Dev-Maschine (passend zur `converter`-Rolle im Cluster). `-Djdk.tracePinnedThreads=full`
   schreibt bei jedem Pinning-Event einen Stacktrace nach stdout — der direkte Beweis.

2. Burst über der konfigurierten Concurrency fahren (`app.queue.image.concurrency=4` lokal):

   ```bash
   k6 run -e PROFILE=arrival -e RATE=10 -e DURATION=2m loadtest/k6/scenarios/image.js
   ```

3. Im Grafana-Dashboard **„RS2 Lasttest"** beobachten:
   - **„Aktive Consumer-Threads pro Queue"** (`rendering_queue_consumers_active{queue="image"}`,
     misst tatsächlich gerade verarbeitete Nachrichten, nicht nur registrierte Consumer): Bleibt
     der Wert deutlich unter 4 (z. B. bei ~2), während **„Queue-Tiefe: ready"** für
     `image_job_queue` gleichzeitig wächst (Backlog vorhanden, aber nicht abgearbeitet) →
     bestätigt die Hypothese.
   - Im Service-Log parallel nach Stacktraces der Form `Thread[...] Pinned ... at
     java.desktop/... (synchronized)` während der Konvertierung suchen.
4. Kontrolllauf **ohne** `-XX:ActiveProcessorCount` wiederholen (Dev-Maschine mit mehr Kernen):
   Der Consumer-Gauge sollte jetzt näher an 4 herankommen und keine Pinning-Traces mehr auftauchen
   — das belegt, dass die knappe Kernzahl (nicht der Code-Pfad an sich) die Ursache ist.

Das gleiche Rezept eignet sich auch für `av`/`video` (ffmpeg läuft zwar außerhalb der JVM, aber der
Consumer-Thread wartet blockierend auf den Subprozess — dort ist Pinning weniger wahrscheinlich,
aber ein Kontrolllauf schadet nicht). Für `document` ist die Ursache bereits separat identifiziert
(einzelner LibreOffice-Prozess im `document-converter`, siehe `jodconverter.local.port-numbers`) —
das Pinning-Rezept ist dort nicht der relevante Mechanismus.

## Document-Converter direkt testen (LibreOffice-Parallelität)

`scenarios/document-converter.js` schickt ein ~400-Zeilen-DOCX (`fixtures/document.docx`) direkt an
`POST /conversion` des document-converters — ohne Service/Queue/S3. Damit lässt sich isoliert messen,
wie sich die Zahl der LibreOffice-Prozesse pro Container auswirkt.

Stellschrauben im root-`compose.yml` (alle per Env überschreibbar):

| Env | Default | Bedeutung |
|---|---|---|
| `LIBREOFFICE_PORT_NUMBERS` | `2002,2003` | ein Port je LibreOffice-Prozess = parallele Konvertierungen |
| `DOCUMENT_CONVERTER_CPUS` / `_MEMORY` | `2` / `4g` | Container-Limits (Faustregel ~1 vCPU + ~1 GiB je Prozess) |
| `DOCUMENT_CONVERTER_JAVA_XMS` / `_XMX` | `1g` / `1g` | Heap bewusst klein, LibreOffice läuft außerhalb des Heaps |
| `LIBREOFFICE_TASK_QUEUE_TIMEOUT` u. a. | siehe `compose.yml` | Wartezeit auf freien Prozess, Task-Timeout, Recycling |

```bash
# Beispiel: 4 Prozesse, 4 CPUs, 6 GiB
LIBREOFFICE_PORT_NUMBERS=2002,2003,2004,2005 DOCUMENT_CONVERTER_CPUS=4 DOCUMENT_CONVERTER_MEMORY=6g \
  docker compose up -d --force-recreate rendering2-document-converter
# Sättigung: viele VUs, Durchsatz = Iterationen/s
k6 run -e PROFILE=ramping -e VUS=16 -e RAMP=5s -e DURATION=45s loadtest/k6/scenarios/document-converter.js
```

Das Image enthält den Build zum Zeitpunkt des Pushs; für lokale Änderungen den frisch gebauten Jar
per Override als `/app.jar` einhängen (`entrypoint: java -jar /app.jar`).

Messung (24-Kern-Dev-Maschine, `cpus` = Prozesse, sättigende Last, 45 s, 0 Fehler):

| Prozesse | Durchsatz | p95 |
|---|---|---|
| 1 | 1,9 /s | 9,1 s |
| 2 | 3,3 /s | 5,2 s |
| 4 | 6,0 /s | 3,0 s |
| 8 | 9,6 /s | 3,5 s |

Skalierung nahezu linear bis 4 Prozesse, danach flacht sie ab (8 Prozesse ≈ 7 Kerne ausgelastet, nur
~1,5 GiB RSS für dieses Dokument). Reale Office-Dokumente brauchen deutlich mehr Speicher je Prozess.

## Video unter Last (Master+Worker-Cluster lokal)

Reproduziert die Kundensituation „großes Video ist langsam, andere Konvertierungen laufen währenddessen in
Timeouts“ auf der Helm-Topologie **Master+Worker** mit CPU-limitierten Pods. `local-cluster.sh` startet
einen master- und `WORKERS` Worker-JVMs (Rollen `controller,job-manager,converter,avconverter`), jeden in einer
eigenen cgroup mit `CPUQuota`/`MemoryMax` (= `limits.cpu`/`limits.memory`). Die ffmpeg-Prozesse erben die
cgroup und teilen sich die Quota mit dem JVM — wie im Pod. Queue-/Converter-Werte = Helm-Chart-Defaults
(`app.queue.av.concurrency=2`, `ffmpegThreads=4`, `veryfast`), nicht `application.properties`.

```bash
docker compose up -d rustfs repository-cache rabbitmq mongo rendering2-document-converter
# beliebiges echtes Video >= 1080p (synthetische testsrc-Videos sind viel zu billig zu encodieren)
VIDEO_FIXTURE=/pfad/zu/video-1080p.mp4 CPU_SET=0-11 loadtest/local-cluster.sh start
k6 run -e BASE_URL=http://localhost:8085,http://localhost:8086 -e RATE=2 \
  -e MIX=image,document,pdf,audio [-e VIDEO_COUNT=2] loadtest/k6/scenarios/video-mix.js
loadtest/local-cluster.sh stats    # CPU + CFS-Throttling je Pod
loadtest/local-cluster.sh stop
```

- `video-mix.js`: konstante einfache Last (`RATE`/s aus `MIX`), nach `WARMUP_S` (60 s) startet `VIDEO_COUNT`
  Videos; sind alle terminal, endet der Test. Metriken: `rs2_video_quality_ready{quality}`,
  `rs2_video_first_quality`, `rs2_time_to_finish{phase:before|during}`, `rs2_job_errors{phase:...}`
  (ein einfacher Job über `POLL_TIMEOUT_MS` = 120 s zählt als Fehler).
- Ohne lokal installiertes k6: `docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/loadtest/k6:/k6:ro" grafana/k6 run ... /k6/scenarios/video-mix.js`.
- Varianten per Env: `EXTRA_PROPS="-Dapp.queue.av.concurrency=4 -Dapp.converter.av.niceness=10"`, dedizierte
  AV-Converter mit gleichem CPU-Budget: `WORKER_ROLES=controller,job-manager,converter WORKER_CPU=1 AV_CONVERTERS=1 AV_CPU=2`.
- Belegte Ports (z. B. 8081 durch ein anderes Projekt): `DOCUMENT_CONVERTER=http://localhost:<port>` und den
  document-converter per compose-Override umlegen.
- Nach Codeänderungen `local-cluster.sh build` (die JVMs laufen direkt aus `service/target/classes`; während
  eines Laufs nicht neu kompilieren).

Messung (Oktober 2026, i7-12700H, Pods auf P-Cores; Video: Big Buck Bunny 1080p, 10:34 min, 276 MB, 3
Zielauflösungen; einfache Last 2/s aus image/document/pdf/audio; 2 Worker × 2 CPU, 6 GiB):

| Lauf | 480p / 720p / 1080p fertig nach | einfache Jobs während Video (p50 / p95, Timeouts) |
|---|---|---|
| R1 Helm-Defaults, 1 Video | 991 / 1021 / 1019 s | 1,5 / 3,7 s, 0 (image p50 0,6 → 2,9 s) |
| R1b Helm-Defaults, **2 Videos** | 480p beider Videos nach 882/886 s, danach abgebrochen | **415 Audio-Timeouts**, Audio-Wartezeit in der AV-Queue bis 880 s; image p95 10 s |
| R4 dedizierter AV-Converter (2 CPU) + 2 Worker à 1 CPU, 1 Video | 602 / 628 / 1177 s | image/document/pdf unbeeinflusst (p95 0,6 s); **266 Audio-Timeouts** |
| R5 `av.concurrency=4`, 2 Videos | 743–1640 / ~1700 / 1013 s + **1× FAILED (30-min-Grenze)** | 1,8 / 5,4 s, 0 Timeouts |
| R3 Code-Fix (Encoder-Threads) + `niceness=10`, 1 Video | **377 / 770 / 1005 s** | **0,9 / 2,7 s**, 0 (image p50 1,8 s) |

Erkenntnisse:

1. **`ffmpegThreads` wirkte bis zum Fix nicht auf den Encoder.** jave setzte das `-threads` *vor* `-i`
   (= nur Decoder); libx264 wählte seine Threadzahl aus den CPUs des Nodes, nicht aus dem Pod-Limit. Unter
   CPU-Quota teilt der Kernel CPU pro Thread — die 1080p-Encodierung mit den meisten Threads verdrängte die
   billige 480p-Stufe (R1: 480p so spät wie 1080p; R5: 480p eines Videos erst *nach* dessen 1080p).
   Fix: `EncodingAttributes.setDecodingThreads/setEncodingThreads`.
2. **Timeouts einfacher Jobs entstehen durch AV-Slots, nicht durch CPU.** Audio teilt sich die AV-Queue mit
   den Videostufen. Belegen lange Encodierungen alle `Pods × app.queue.av.concurrency` Slots, wartet jedes
   Audio (und jedes kleine Video) bis eine Stufe fertig ist — Minuten. Reine CPU-Konkurrenz verlangsamt
   in-JVM-Jobs nur (Faktor 2–6), ohne Timeouts in dieser Last.
3. **Harte 30-min-Grenze pro AV-Job:** `app.converter.av.conversionTimeout=PT30M` *und* RabbitMQs Default
   `consumer_timeout` (1800000 ms) — der Broker schließt den Kanal und stellt die Nachricht neu zu. Mehr
   parallele Encodierungen pro Pod (R5) verlängern jede einzelne und reißen diese Grenze.
4. **`app.converter.video.resolutions` aus Helm/compose war wirkungslos:** der String `480,720,1080` band
   nicht auf die damalige Map `resolutions.<h>.priority`; es galten immer die drei Einträge aus
   `application.properties`. Behoben: `resolutions` ist jetzt eine Liste, die Prioritäten stehen getrennt in
   `app.converter.video.priorities.<h>`.
5. **Dedizierte AV-Converter** isolieren image/document/pdf vollständig, lassen bei gleichem CPU-Budget aber
   die Worker leer laufen (0,2 von 1 CPU) und verschärfen den Audio-Stau (nur 2 Slots).

Was eine Stufe kostet (reines ffmpeg, 120-s-Ausschnitt 1080p, 2-CPU-Quota, 2 Encoder-Threads, Einzelläufe):

| | 480p | 720p | 1080p | Dateigröße 1080p |
|---|---|---|---|---|
| `veryfast` (Default) | 27 s | 45 s | 60 s | 26,8 MB |
| `superfast` | 25 s | 51 s | 45 s | 46,3 MB |
| `ultrafast` | 20 s | 25 s | 30 s | 85,2 MB |
| 1080p nur umpacken (`-c:v copy`, Quelle ist schon H.264 1080p) | | | 2 s | 36,5 MB |
| alle drei Stufen in einem ffmpeg-Lauf (einmal decodieren) | 115 s statt 132 s | | | |

Im Ende-zu-Ende-Lauf kostet das ganze Video (10:34 min) für alle drei Stufen ~2200 CPU-Sekunden (R4: 1080p
allein 509 s auf 2 CPUs) — bei 2 × 2 CPUs also rund 10 Minuten des gesamten Clusters. `-threads 4` statt
`2` macht unter 2-CPU-Quota nach dem Fix kaum einen Unterschied (64 vs. 62 s).

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
