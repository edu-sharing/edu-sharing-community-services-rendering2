package org.edu_sharing.rendering.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

/**
 * HTTP client and call timeouts of the S3 client (`app.s3.client.*`), see [S3Config].
 *
 * The SDK defaults are unsuitable here: 50 pooled connections are far below the parallelism of one
 * pod (K = 50 per remote queue, ~90 STANDARD consumers plus HTTP asset streaming, each holding a
 * connection while it streams), and without call timeouts a stuck endpoint blocks threads forever.
 *
 * @property maxConnections Size of the Apache connection pool. Size it to the pod's peak S3
 *   parallelism (sum of the queue concurrencies plus concurrent asset downloads).
 * @property connectionTimeout TCP connect timeout.
 * @property socketTimeout Max wait for data on an established connection (per read, not per
 *   object): large objects can stream for a long time as long as bytes keep flowing.
 * @property connectionAcquisitionTimeout Max wait for a free pooled connection; fails fast
 *   instead of queueing forever when the pool is exhausted.
 * @property connectionMaxIdleTime Idle connections older than this are closed (below typical
 *   LB/conntrack idle limits).
 * @property apiCallAttemptTimeout Deadline of one HTTP attempt. Deliberately generous: it also
 *   bounds big uploads/downloads (multi-GB objects, slow links).
 * @property apiCallTimeout Deadline of a whole call including SDK retries; should be at least
 *   [apiCallAttemptTimeout]. Same reasoning as above.
 */
@ConfigurationProperties(prefix = "app.s3.client")
data class S3ClientProperties(
    @DefaultValue("200") val maxConnections: Int,
    @DefaultValue("10s") val connectionTimeout: Duration,
    @DefaultValue("2m") val socketTimeout: Duration,
    @DefaultValue("30s") val connectionAcquisitionTimeout: Duration,
    @DefaultValue("1m") val connectionMaxIdleTime: Duration,
    @DefaultValue("30m") val apiCallAttemptTimeout: Duration,
    @DefaultValue("1h") val apiCallTimeout: Duration
)
