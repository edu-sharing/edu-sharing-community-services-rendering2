## Parameters

### Global parameters

| Name                                    | Description                           | Value                  |
| --------------------------------------- | ------------------------------------- | ---------------------- |
| `global.annotations`                    | Define global annotations             | `{}`                   |
| `global.cluster.istio.enabled`          | Enable Istio Service mesh             | `false`                |
| `global.cluster.pdb.enabled`            | Enable PDB                            | `false`                |
| `global.image.pullPolicy`               | Set global image pullPolicy           | `Always`               |
| `global.image.pullSecrets`              | Set global image pullSecrets          | `[]`                   |
| `global.image.registry`                 | Set global image container registry   | `${docker.registry}`   |
| `global.image.repository`               | Set global image container repository | `${docker.repository}` |
| `global.image.common`                   | Set global image container common     | `${docker.common}`     |
| `global.metrics.rules.enabled`          | Enable metrics rules                  | `false`                |
| `global.metrics.scrape.interval`        | Set prometheus scrape interval        | `60s`                  |
| `global.metrics.scrape.timeout`         | Set prometheus scrape timeout         | `60s`                  |
| `global.metrics.servicemonitor.enabled` | Enable metrics service monitor        | `false`                |
| `global.password`                       | Set global password                   | `""`                   |
| `global.security`                       | Set global custom security parameters | `{}`                   |

### Local parameters

| Name                                            | Description                                  | Value                                       |
| ----------------------------------------------- | -------------------------------------------- | ------------------------------------------- |
| `nameOverride`                                  | Override name                                | `edusharing-services-rendering2-lumi`       |
| `image.name`                                    | Set image name                               | `${docker.prefix}-deploy-docker-build-lumi` |
| `image.tag`                                     | Set image tag                                | `${docker.tag}`                             |
| `replicaCount`                                  | Define amount of parallel replicas to run    | `1`                                         |
| `service.port.api`                              | Set port for service API                     | `3000`                                      |
| `config.base`                                   | Set base path                                | `/rendering`                                |
| `config.cache.type`                             | Set cache type                               | `in-memory`                                 |
| `config.files.maxsize`                          | Set files max size                           | `1000`                                      |
| `config.log.level`                              | Set log level                                | `info`                                      |
| `config.log.scope`                              | Set log scope                                | `h5p:*`                                     |
| `config.metrics.relabelings`                    | Define relabelings for metrics               | `[]`                                        |
| `config.metrics.rules.nodejsUp.enabled`         | Enable metric rule nodejsUp                  | `true`                                      |
| `config.metrics.rules.nodejsUp.for`             | Set metric rule nodejsUp wait interval       | `5m`                                        |
| `config.metrics.rules.nodejsUp.labels.severity` | Set metric rule nodejsUp severity level      | `critical`                                  |
| `config.mongodb.database`                       | Set mongodb database                         | `lumi`                                      |
| `config.mongodb.host`                           | Set mongodb host                             | `edusharing-rendering2-mongodb`             |
| `config.mongodb.port`                           | Set mongodb port                             | `27017`                                     |
| `config.mongodb.username`                       | Set mongodb username                         | `rendering2`                                |
| `config.mongodb.password`                       | Set mongodb password                         | `""`                                        |
| `config.mongodb.collections.content`            | Set collection for content                   | `h5p`                                       |
| `config.mongodb.collections.edusharing`         | Set collection for edu-sharing               | `lumiedusharing`                            |
| `config.mongodb.collections.library`            | Set collection for library                   | `h5plibraries`                              |
| `config.s3.host`                                | Set S3 host                                  | `edusharing-rendering2-minio`               |
| `config.s3.port`                                | Set S3 port                                  | `9000`                                      |
| `config.s3.username`                            | Set S3 username                              | `rendering2`                                |
| `config.s3.password`                            | Set S3 password                              | `""`                                        |
| `config.s3.buckets.content`                     | Set S3 bucket for content                    | `lumi-contentbucket`                        |
| `config.s3.buckets.library`                     | Set S3 bucket for library                    | `lumi-libbucket`                            |
| `config.s3.buckets.temporary`                   | Set S3 bucket for temporary                  | `lumi-tempbucket`                           |
| `nodeAffinity`                                  | Set node affinity                            | `{}`                                        |
| `podAntiAffinity`                               | Set pod antiaffinity                         | `soft`                                      |
| `tolerations`                                   | Set tolerations                              | `[]`                                        |
| `podAnnotations`                                | Set custom pod annotations                   | `{}`                                        |
| `podSecurityContext.fsGroup`                    | Set fs group for access                      | `1000`                                      |
| `podSecurityContext.fsGroupChangePolicy`        | Set change policy for fs group               | `OnRootMismatch`                            |
| `securityContext.allowPrivilegeEscalation`      | Allow privilege escalation                   | `false`                                     |
| `securityContext.capabilities.drop`             | Set drop capabilities                        | `["ALL"]`                                   |
| `securityContext.runAsUser`                     | Define user to run under                     | `1000`                                      |
| `terminationGracePeriod`                        | Define grace period for termination          | `120`                                       |
| `startupProbe.failureThreshold`                 | Failure threshold for startupProbe           | `30`                                        |
| `startupProbe.initialDelaySeconds`              | Initial delay seconds for startupProbe       | `0`                                         |
| `startupProbe.periodSeconds`                    | Period seconds for startupProbe              | `20`                                        |
| `startupProbe.successThreshold`                 | Success threshold for startupProbe           | `1`                                         |
| `startupProbe.timeoutSeconds`                   | Timeout seconds for startupProbe             | `10`                                        |
| `livenessProbe.failureThreshold`                | Failure threshold for livenessProbe          | `3`                                         |
| `livenessProbe.initialDelaySeconds`             | Initial delay seconds for livenessProbe      | `30`                                        |
| `livenessProbe.periodSeconds`                   | Period seconds for livenessProbe             | `30`                                        |
| `livenessProbe.timeoutSeconds`                  | Timeout seconds for livenessProbe            | `10`                                        |
| `readinessProbe.failureThreshold`               | Failure threshold for readinessProbe         | `1`                                         |
| `readinessProbe.initialDelaySeconds`            | Initial delay seconds for readinessProbe     | `10`                                        |
| `readinessProbe.periodSeconds`                  | Period seconds for readinessProbe            | `10`                                        |
| `readinessProbe.successThreshold`               | Set threshold for success on readiness probe | `1`                                         |
| `readinessProbe.timeoutSeconds`                 | Timeout seconds for readinessProbe           | `10`                                        |
| `resources.limits.cpu`                          | Set CPU limit on resources                   | `500m`                                      |
| `resources.limits.memory`                       | Set memory limit on resources                | `2Gi`                                       |
| `resources.requests.cpu`                        | Set CPU for requests on resources            | `500m`                                      |
| `resources.requests.memory`                     | Set memory for requests on resources         | `2Gi`                                       |
