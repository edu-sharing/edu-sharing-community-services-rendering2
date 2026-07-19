# Phase B — Lasttest im k8s-Cluster (Skalierungs-Ebene 2)

Phase B zeigt das, was lokal (Phase A) unsichtbar bleibt: das **Zusammenspiel beider
Skalierungsebenen** — Kubernetes-HPA (Pod-Anzahl) und die pod-interne Consumer-Skalierung
(Spring AMQP), plus competing consumers über mehrere Pods hinweg. Siehe `docs/QUEUE_SCALING.md`.

Erst fahren, wenn Phase A grün ist und die per-Converter-Baseline steht.

## 1. Metriken im Cluster aktivieren

Die Helm-Chart-CRDs für Prometheus sind vorhanden, aber standardmäßig aus. Für den Testlauf in
`deploy/docker/helm/service/src/main/chart/values.yaml` (bzw. per `--set`) setzen:

```yaml
global:
  metrics:
    servicemonitor:
      enabled: true      # scrapt /actuator/prometheus (:9080) inkl. der rendering_*-Metriken
    rules:
      enabled: true
```

Die neuen Custom-Metriken (`rendering_queue_consumers_active`, `rendering_subjob_duration_*`,
`rendering_job_duration_*`, `rendering_job_failed_total`) werden dann automatisch vom
Cluster-Prometheus erfasst. Das Grafana-Dashboard aus `../observability/grafana/dashboards/`
ist wiederverwendbar (Datenquelle auf das Cluster-Prometheus zeigen lassen).

## 2. HPA pro Rolle aktivieren

Damit Ebene 2 (Pod-Autoscaling) sichtbar wird, HPA für die lasttragenden Rollen einschalten:

```yaml
roles:
  converter:
    autoscaling:
      enabled: true
      minReplicas: 1
      maxReplicas: 6
      targetCPU: 60
  avconverter:
    autoscaling: { enabled: true, minReplicas: 1, maxReplicas: 4, targetCPU: 60 }
  job-manager:
    autoscaling: { enabled: true, minReplicas: 1, maxReplicas: 3, targetCPU: 60 }
```

## 3. k6-Test als Archiv in eine ConfigMap legen

Der Test besteht aus mehreren Dateien (Imports aus `lib/`), darum ein k6-Archiv verwenden:

```bash
cd loadtest/k6
k6 archive scenarios/all.js -o archive.tar
kubectl -n <namespace> create configmap rs2-k6-archive --from-file=archive.tar
```

## 4. TestRun starten

`testrun.yaml` an Namespace/Service-DNS/Prometheus-URL anpassen und anwenden:

```bash
kubectl -n <namespace> apply -f loadtest/k8s/testrun.yaml
kubectl -n <namespace> get testrun rs2-converter-loadtest -w
```

`parallelism` steuert die Zahl der Runner-Pods (verteilte Last). `PROFILE`/`RATE`/`DURATION`
(Env im Manifest) steuern das Lastprofil analog zu Phase A.

## 5. Beobachtungsziele Ebene 2

- **Pod-Count-Kurve (HPA)** vs. Last — Panel `kube_deployment_status_replicas` bzw. HPA-Metriken.
- **Consumer-Skalierung pro Pod** — `rendering_queue_consumers_active` je Pod-Instanz.
- **Lastverteilung über Pods** — belegen, dass niedriger `prefetch` gleichmäßig verteilt.
- **Pod-Crash-Verhalten** — unacked Nachrichten werden requeued (`defaultRequeueRejected=false`).

## 6. Drittanbieter-Mock (sodix / omega / ddb) im Cluster

Für die Import-Module (sodix/omega/ddb) braucht Phase B ein In-Cluster-Pendant zum compose-Mock.
Ein WireMock-Deployment bedient alle drei über Pfad-Präfixe; die Stub-Mappings sind identisch mit
Phase A (`loadtest/mocks/wiremock/mappings/`, u. a. die **1–2 s-Sodix-Latenz**).

**6.1 Mappings als ConfigMap + Mock deployen** (Mappings sind Single-Source-of-Truth, keine
Duplikate im Manifest — analog zum k6-Archiv):

```bash
kubectl -n <namespace> create configmap rs2-thirdparty-mock-mappings \
  --from-file=loadtest/mocks/wiremock/mappings/
kubectl -n <namespace> apply -f loadtest/k8s/thirdparty-mock.yaml
kubectl -n <namespace> rollout status deploy/rendering2-thirdparty-mock
```

Nach Mapping-Änderungen die ConfigMap neu erzeugen (`create ... --dry-run=client -o yaml | kubectl apply -f -`)
und `kubectl rollout restart deploy/rendering2-thirdparty-mock`.

**6.2 Service auf den Mock zeigen lassen.** Wie schon der Converter-Lasttest braucht der Service
das `debug,loadtest`-Profil (TEST-Bypass + Lasttest-Deltas). Die vier Mock-URLs aus
`application-loadtest.properties` zeigen per Default auf `localhost:8092` (Phase A) und werden
für das Cluster auf den Service-DNS `rendering2-thirdparty-mock:8080` überschrieben. Beides geht
über `config.override` der Service-Chart (landet als `SPRING_APPLICATION_JSON`, das den
`-Dspring.profiles.active=docker`-Default des Images überstimmt):

```yaml
config:
  override: |
    {
      "spring.profiles.active": "docker,debug,loadtest",
      "app.repository.registration.id.local.module.SODIX.credentials.baseurl": "http://rendering2-thirdparty-mock:8080/sodix/",
      "app.repository.registration.id.local.module.OMEGA.credentials.baseurl": "http://rendering2-thirdparty-mock:8080/omega/get",
      "app.module.ddb.rest-api-base-url": "http://rendering2-thirdparty-mock:8080/ddb/rest",
      "app.module.ddb.iiif-api-base-url": "http://rendering2-thirdparty-mock:8080/ddb/iiif"
    }
```

> Liegt der Mock in einem anderen Namespace als der Service, den vollqualifizierten DNS-Namen
> verwenden: `rendering2-thirdparty-mock.<mock-ns>.svc.cluster.local:8080`.

**6.3 k6-Szenarien.** Das Archiv aus §3 mit den Import-Szenarien bestücken, z. B. gemischt:

```bash
k6 archive loadtest/k6/scenarios/all.js -o archive.tar -e MIX=sodix,omega,ddb
# oder einzeln: k6 archive loadtest/k6/scenarios/sodix.js -o archive.tar
```

**6.4 Beobachten.** `rendering_queue_consumers_active{queue="sodix_job_queue"}` unter der
1–2 s-Latenz — die langsamen, I/O-gebundenen Consumer treiben Ebene-1- **und** Ebene-2-Scaling
am deutlichsten. Dabei die **CPU des Mock-Pods** im Blick behalten: sättigt er, wird er zum
Flaschenhals und verfälscht die Messung → `kubectl scale deploy/rendering2-thirdparty-mock --replicas=N`.
