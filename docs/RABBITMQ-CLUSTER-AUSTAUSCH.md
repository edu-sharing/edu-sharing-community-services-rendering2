# RabbitMQ: automatischer Cluster-Austausch per Pipeline-Update

Stand: 2026-10-27. Der RabbitMQ-Cluster wird nicht aktualisiert, sondern **durch einen neuen ersetzt**. Das passiert
im normalen `helm upgrade` der Update-Routine der Pipeline, ohne manuelle Schritte pro Umgebung: Das Bundle setzt
einen neuen Namen (`…-rabbitmq-4-3`, siehe 1.1), Helm legt dafür ein neues StatefulSet mit neuen PVCs an und entfernt das alte
StatefulSet selbst. Der Service-Host folgt dem Namen (YAML-Anker im Bundle). Datenverlust ist akzeptiert, der
Service deklariert alle Queues beim Verbinden selbst.

> **Status:** Nicht gegen ein echtes Upgrade erprobt. Geprüft wurden das Chart-Rendering (offline und lesend gegen
> `bird-dev`) und das Verhalten des bestehenden Clusters in `bird-dev` (Ausfalltests). Alles *Ungeprüfte* vor dem
> Rollout in Dev nachspielen.

## 1. Was im Repo geändert ist

| Wo | Änderung |
|---|---|
| `edu-sharing-community-common-deployment-rabbitmq` | `podManagementPolicy: Parallel` (bei bestehendem StatefulSet bleibt der vorhandene Wert, damit `helm upgrade` nie an der Unveränderlichkeit scheitert), `target_cluster_size_hint`, Discovery-Retries, PDB, Spread, schnellere Readiness; neuer Schalter `config.cookieFromName` |
| Bundle `values.yaml` | `edusharing_services_rendering2_rabbitmq.nameOverride` = `edusharing-services-rendering2-rabbitmq-4-3`, `config.cookieFromName: true` |
| Service-Chart `values.yaml` | Default `config.rabbitmq.host` = `…-rabbitmq-4-3` (das Bundle überschreibt ihn ohnehin über den Anker) |
| Dashboard (`sdk/monitoring/dashboards/edusharing.json` im Enterprise-Repo) | keine Änderung nötig: alle 12 RabbitMQ-Selektoren sind `pod=~"edusharing-services-rendering2-rabbitmq.*"` und treffen auch `…-rabbitmq-4-3-N`. Während des Austauschs werden alter und neuer Cluster kurz zusammengezählt. |

**Reihenfolge der Veröffentlichung:** Erst das RabbitMQ-Chart (neue Version), dann Bundle und Service. Das Bundle
bezieht das RabbitMQ-Chart als `1.x.x`; ein älteres Chart kennt `cookieFromName` nicht und ignoriert es, dann hätten
beide Cluster beim Upgrade denselben Cookie (siehe 3.1).

### 1.1 Benennung nach RabbitMQ-Serie
Der Name des StatefulSets enthält die **Serie** (Major-Minor) des Brokers: `edusharing-services-rendering2-rabbitmq-4-3`.
Die Serie kommt aus der Maven-Property `docker.edu_sharing.community.common.rabbitmq.series` in
`deploy/docker/pom.xml` (steht dort direkt unter dem Tag und muss mit ihm übereinstimmen, `.` wird zu `-`).
- **Patch-Bump** (4.3.7 → 4.3.8): Name bleibt, normales Rolling-Upgrade (das StatefulSet hat jetzt `Parallel`).
- **Minor-Bump** (4.3 → 4.4): Name ändert sich, Helm ersetzt den Cluster (Ablauf wie in Abschnitt 2). Das passt zu den
  Upgrade-Regeln von RabbitMQ, ein Minor-Wechsel ist so immer ein frischer Cluster statt eines In-Place-Upgrades.
- Die Property wird im Bundle (`nameOverride`) und im Service-Chart (Default-Host) verwendet. Wer nur das Tag anhebt
  und die Serie vergisst, bekommt statt eines Austauschs ein In-Place-Upgrade über die Minor-Grenze. Die beiden Werte
  deshalb immer zusammen ändern.
- Länge: Der Headless-Service heißt `…-rabbitmq-4-3-headless` (52 Zeichen), das Limit für Kubernetes-Namen liegt bei 63.

### 1.2 Peer-Discovery über das Kubernetes-Plugin
Das Chart nutzt `cluster_formation.peer_discovery_backend = rabbit_peer_discovery_k8s` und aktiviert das Plugin
`rabbitmq_peer_discovery_k8s` in `RABBITMQ_PLUGINS`. Es sind **keine weiteren k8s-Einstellungen** nötig.
- **Gemessen am frischen 4.3.7-Cluster in `bird-dev` (eigene Testinstanz, 3 Replicas, `Parallel`):**
  Der Cluster bildete sich beim ersten Start von selbst, drei Knoten nach rund 50 s, alle Pods ready nach rund 73 s, alle
  Knoten sehen denselben Cluster, im Log keine 401/403/Forbidden. Dabei lief der Pod unter dem **Default-ServiceAccount**,
  es wurden keine zusätzlichen Rechte angelegt.
- **T2 (alle drei Pods gleichzeitig löschen), k8s-Plugin:** Cluster mit 3 Knoten nach rund 31 s, alle Pods ready nach
  rund 51 s, ohne Eingriff, danach über drei Minuten stabil. Ein Durchlauf, ohne Last.
- Das Plugin meldet beim Start, dass `k8s_service_name`, `k8s_address_type` und `k8s_hostname_suffix` **veraltet sind und
  ignoriert** werden, ebenso `cluster_formation.discovery_retry_limit` („will retry forever"). Das Chart setzt sie
  deshalb nicht. Wie das Plugin die Peers ermittelt (API oder aus den Knotennamen), geht aus dem Log nicht hervor.
- **ServiceAccount nur bei Bedarf:** Falls ein Cluster den Default-ServiceAccount einschränkt und die Pods im Log
  „forbidden" aus der Peer-Discovery melden, lassen sich per Values ServiceAccount und Berechtigung setzen:
  `serviceAccount.create`, `serviceAccount.name` (auch für einen bestehenden ServiceAccount) und
  `serviceAccount.rbac.create` (Role mit `get`/`list`/`watch` auf `endpoints` plus RoleBinding). Default: alles aus.
  Diese Option ist gerendert und per Client-Dry-Run validiert, **im Cluster nicht angewendet**.
- **Clustergröße:** `cluster_formation.target_cluster_size_hint` ist ein statischer RabbitMQ-Wert und wird nicht aus
  DNS oder API abgeleitet. Das Chart rendert ihn aus `replicaCount` (eine Quelle, kein fester Wert).
- **Warum nicht DNS:** Mit `peer_discovery_backend = dns` (A-Records des Headless-Services) bildeten sich unter
  denselben Bedingungen im selben Cluster **drei getrennte Ein-Knoten-Cluster**, auch nach fünf Minuten. Das Backend
  selbst lieferte alle drei Knotennamen korrekt, ein manueller Aufruf von `rabbit_peer_discovery:sync_desired_cluster()`
  verband die Knoten. Beim gleichzeitigen Start lief der Beitritt aber nicht von selbst. Die Ursache ist nicht geklärt.
  Der Reverse-Lookup (PTR) war außerdem mehrdeutig (eine von drei IPs löste auf den Namen des Metrics-Services auf).
- *Ungeprüft:* Wiederholung der Erstbildung (bisher ein einzelner Lauf), Rolling-Restart (T3), Verhalten unter Last und
  die Option mit ServiceAccount/RBAC im Cluster.

### 1.3 Entscheidungen (2026-10-27)
- **Automatischer Austausch per Pipeline: ja.** Er läuft im normalen `helm upgrade` jeder Umgebung.
- **Zielversion: 4.3** (Image 4.3.7), Community-Support bis 31.01.2027.
- **Quorum Queues: umgesetzt.** Alle 15 Business-Queues sind Quorum (`app.queue.<x>.type`, einzeln auf `classic`
  umstellbar), die Namen wurden auf `_v3` umgestellt, damit ein nicht ersetzter Broker nicht mit `406` stillschweigend
  aufhört zu konsumieren. `x-max-priority` ist entfallen (Quorum lehnt es ab), die av-Priorität läuft über die
  Nachrichten-Eigenschaft. Details und Messwerte: `docs/QUEUE_SCALING.md` §6.
- **Consumer-Timeout:** Das Bundle setzt ihn auf 60 min (`config.consumerTimeout`), über dem Reaper-Wert für av (45 min).
- **`partitionHandling` entfernt** (unter 4.3 wirkungslos).
- **`max-queued-time` gekürzt:** Standard 1 h statt 6 h, av 6 h (`app.jobreaper.max-queued-time.av_job_v3`).
- **Fehler mitkorrigiert:** Der Reaper-Override `max-process-time.av_job=PT45M` traf wegen des Routing-Keys `av_job_v2` nie
  zu (av-Jobs wurden nach 30 statt 45 Minuten gereapt). Der Key heißt jetzt `av_job_v3`, in `application.properties`,
  Helm und Compose.

## 2. Ablauf beim Pipeline-Update

1. Die Pipeline führt wie gewohnt `helm upgrade` des Bundles aus.
2. Helm legt `…-rabbitmq-4-3` (StatefulSet, Services, PVCs `data-…-4-3-N`) neu an. Es startet mit `Parallel`, weil
   kein bestehendes StatefulSet dieses Namens existiert, und als **frischer Cluster** (Khepri ist der Default).
3. Die Service-Pods rollen (Host in der ConfigMap geändert), verbinden sich mit dem neuen Cluster und deklarieren alle Queues.
4. Helm entfernt danach StatefulSet und Services des alten Namens. Die **alten PVCs bleiben** liegen
   (Helm löscht keine PVCs).

### Prüfen nach dem Update (je Umgebung)
- `kubectl get pods`: `…-4-3-0..2` auf `1/1`, die alten Pods sind weg.
- `rabbitmqctl cluster_status` auf `…-4-3-0`: **ein** Cluster, drei Knoten, alle `…-4-3-N`.
- `rabbitmq-diagnostics server_version`, `rabbitmqctl list_feature_flags` (`khepri_db` = `enabled`).
- `rabbitmqctl list_queues name state consumers`: alle 15 `_job_queue_v3`-Queues mit Consumern, jeweils vom Typ `quorum`.
- Log der Service-Pods: `Unroutable published message`, `Publish not confirmed`.
- Ein Test-Render-Request, Job muss `FINISHED` werden.

### Ausfalltests an der ersten Umgebung (Dev)

| Test | Aktion | Erwartung |
|---|---|---|
| T1 | Ein Pod löschen | Queues in Sekunden wieder erreichbar |
| T2 | **Alle drei Pods gleichzeitig löschen** | Cluster kommt **ohne Eingriff** in 1–2 min zurück, genau ein Cluster |
| T3 | Rolling-Restart | Kein dauerhafter Ausfall; vor jedem Knoten `check_if_node_is_quorum_critical` |
| T4 | Node-Verlust (Node sperren, Pod löschen) | Pod startet auf anderem Node, Volume wandert (**bisher nicht getestet**) |

T2 hat beim alten Cluster versagt und ist der entscheidende Test.

### Aufräumen (nicht Teil des Updates)
Die PVCs `data-edusharing-services-rendering2-rabbitmq-{0,1,2}` (alter Name) bleiben als Rückfall liegen
(3 × 5 Gi). Nach erfolgreicher Beobachtung manuell löschen. Das ist bewusst **nicht** automatisiert.

## 3. Risiken und Auswirkungen

### 3.1 Kurzzeitig laufen zwei Cluster
Helm legt die neuen Ressourcen an, **bevor** es die alten entfernt. Altes und neues StatefulSet laufen deshalb kurz
parallel (das alte fährt mit bis zu `terminationGracePeriod` = 120 s herunter). Per `helm upgrade` allein ist das
nicht zu vermeiden.
- `cookieFromName` leitet den Erlang-Cookie aus Passwort **und** Name ab (SHA-256). Alter und neuer Cluster haben so
  unterschiedliche Cookies und können sich nicht verbinden, auch wenn die Peer-Discovery einander findet. Das Chart
  rendert mit gesetztem `cookieFromName` je Name einen anderen Cookie, ein expliziter `config.cookie` hat Vorrang
  (geprüft per `helm template`).
- *Ungeprüft:* ob sich zwei Cluster mit dem k8s-Plugin im selben Namespace gegenseitig finden könnten. Der eigene
  Cookie schließt das aus; im Test trotzdem prüfen, dass jeder Cluster nur seine eigenen Knoten sieht.
- Kapazität: kurzzeitig 6 Pods à 4 Gi RAM und 6 PVCs je Umgebung. Vorab prüfen.

### 3.2 Nachrichten und Jobs
- Messages im alten Cluster sind verloren. Während der Mischphase der Service-Pods bleiben Jobs mit verlorenen
  Messages `QUEUED` bis zum Reaper (`app.jobreaper.default-max-queued-time`, Standard jetzt **1 h** (av: 6 h); Dedup liefert bis
  dahin dieselbe tote Job-ID). Wer das nicht hinnehmen will, verkürzt den Wert vorübergehend vor dem Update.
- `PROCESSING`-Jobs löst der Reaper nach 30 min auf (av: 45 min).
- Registrierungs-Cache: höchstens 5 Minuten (TTL) veraltet, falls ein Broadcast verpasst wurde.
- **Das Update trifft jede Umgebung beim nächsten Pipeline-Lauf**, auch Produktion, mit Downtime des Brokers von
  wenigen Minuten und dem Verlust aller Messages. Wer das zeitlich steuern will, muss das Update pro Umgebung
  freigeben.

### 3.3 Rückweg
Bundle-Werte (`nameOverride`, `cookieFromName`) und den Service-Host zurücksetzen: Helm legt den alten Cluster neu
an und entfernt den neuen Cluster. Die alten PVCs werden wiederverwendet, **solange sie nicht gelöscht sind**; der neue Cluster
hatte eigene PVCs und hat die alten Daten nie berührt. Nach dem Aufräumen gibt es diesen Rückweg nicht mehr.

### 3.4 Dauerhafte Folgen
- Der Name `…-4-3` bleibt, solange die Serie 4.3 ist. Patch-Wechsel (4.3.7 → 4.3.8) laufen als normales
  Rolling-Upgrade, denn das StatefulSet hat jetzt `Parallel`. Ein Minor-Wechsel (4.3 → 4.4) ändert den Namen und
  ersetzt den Cluster erneut.
- Alerts (PrometheusRule) und weitere Verweise auf den alten Namen außerhalb dieses Repos sind nicht geprüft. Das Dashboard
  im Enterprise-Repo ist geprüft (siehe Abschnitt 1).
- Compose ist nicht betroffen (eine Instanz, anderer Servicename).

## 4. Offene Punkte

- Reihenfolge von Helm (Anlegen vor Löschen) und das Entfernen des alten StatefulSets in Dev bestätigen
  (mein Wissen zu Helm 3, nicht in der Pipeline getestet). Dev ist dafür geeignet, der dortige Broker steht ohnehin.
- Node-Verlust mit Volume-Wechsel auf NFS (T4) messen; `nfs-storage` ist für RabbitMQ heikel (fsync/Locking),
  ein Storage-Wechsel ließe sich beim Austausch über `persistence.data.spec.storageClassName` erledigen.
- **Image 4.3.7:** Der Bundle-Tag steht in `deploy/docker/pom.xml` auf `4.3.7`, die Serie auf `4-3`. Das Image
  `edu_sharing-community-common-docker-rabbitmq:4.3.7` wird im Common-Image-Repo (Tag `4.3.7`, 2026-10-27) gebaut: Das
  Dockerfile lädt RabbitMQ 4.3.7 und Erlang/OTP 27.3.4.19 aus den Upstream-Quellen (mit SHA256-Prüfung) und nutzt nur
  noch `bitnami/minideb` als Basis, nicht mehr das Bitnami-RabbitMQ-Image. 4.3.6 gibt es als Image nicht. Das Layout bleibt
  das der Bitnami-Images (`/opt/bitnami/rabbitmq`, `entrypoint.sh`, `run.sh`), die Bitnami-Skripte stammen aber aus dem
  Stand 4.1.5 und sind nicht auf 4.3 angepasst. *Ungeprüft:* ob der Tag in der Registry veröffentlicht ist und gezogen
  werden kann, und ob `nodeshutdown.sh` (preStop), der Konfig-Mount `/bitnami/rabbitmq/conf` und die Env-Variablen des
  Charts mit den 4.1.5-Skripten unter 4.3.7 funktionieren. Der 4.2.9-Cluster in Dev läuft auf demselben Aufbau.
  4.3 braucht Erlang 27 oder neuer (erfüllt), Khepri ist einziger Metadatenspeicher (Mnesia entfällt),
  `RABBITMQ_CLUSTER_PARTITION_HANDLING` wirkt nicht mehr (aus dem Chart entfernt). Community-Support für 4.3 endet am 31.01.2027 (Stand der
  Prüfung 2026-10-27, 4.4.0 ist erschienen).
- Ob die Update-Routine Umgebungs-Values besitzt, die `nameOverride` oder den Service-Host überschreiben und den
  Anker aushebeln. Dann müssen beide Werte dort mitgezogen werden.
