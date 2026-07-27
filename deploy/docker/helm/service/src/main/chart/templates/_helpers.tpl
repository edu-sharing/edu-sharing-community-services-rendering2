{{- define "edusharing_services_rendering.pvc.share.config" -}}
share-config-{{ include "edusharing_common_lib.names.name" . }}
{{- end -}}

{{- define "edusharing_services_rendering.pvc.share.data" -}}
share-data-{{ include "edusharing_common_lib.names.name" . }}
{{- end -}}

{{/*
Build the JAVA_OPTS string from a dict {minPercentage, maxPercentage, debug}.
Single source for both the global default (configmap-env) and the per-role override
(statefulset). Empty min/max percentages are skipped, mirroring the previous behaviour.
*/}}
{{- define "edusharing_services_rendering.javaOpts" -}}
{{- $opts := list -}}
{{- with .minPercentage }}{{- $opts = append $opts (printf "-XX:InitialRAMPercentage=%s" (toString .)) -}}{{- end -}}
{{- with .maxPercentage }}{{- $opts = append $opts (printf "-XX:MaxRAMPercentage=%s" (toString .)) -}}{{- end -}}
{{- $opts = append $opts "-Dcom.sun.management.jmxremote" -}}
{{- $opts = append $opts "-Dcom.sun.management.jmxremote.authenticate=false" -}}
{{- $opts = append $opts "-Dcom.sun.management.jmxremote.port=7199" -}}
{{- $opts = append $opts "-Dcom.sun.management.jmxremote.ssl=false" -}}
{{- if .debug }}{{- $opts = append $opts "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005" -}}{{- end -}}
{{- join " " $opts -}}
{{- end -}}
