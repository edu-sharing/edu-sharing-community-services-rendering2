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
