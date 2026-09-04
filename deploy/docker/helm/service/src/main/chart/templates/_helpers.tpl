{{- define "edusharing_services_rendering.pvc.share.config" -}}
share-config-{{ include "edusharing_common_lib.names.name" . }}
{{- end -}}

{{- define "edusharing_services_rendering.pvc.share.data" -}}
share-data-{{ include "edusharing_common_lib.names.name" . }}
{{- end -}}

{{/*
Normalize a config.home.registrations[*].id into a value that is safe as a Spring
property-map key when delivered as an env-var name (ConfigMap/Secret keys via envFrom).

Spring splits property names on ".", and the SystemEnvironmentPropertyMapper additionally
treats "_" as a separator, so "my.repo"/"my_repo" would be bound as map key "my" with a
bogus nested property "repo.url". Bracket notation (id[my.repo].url) would be the Spring
fix but is not expressible here: ConfigMap/Secret keys are limited to [-._a-zA-Z0-9].

So everything outside [a-z0-9-] is folded to "-" and the result is lowercased. The map key
is only a label (it is never used as a repoId; only the literal "local" is looked up in
test mode), so normalizing it is safe. Collisions are rejected by validate-registrations.yaml.
*/}}
{{- define "edusharing_services_rendering.registrationId" -}}
{{- regexReplaceAll "^-+|-+$" (regexReplaceAll "[^a-z0-9-]" (lower (toString .)) "-") "" -}}
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
