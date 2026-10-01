package org.edu_sharing.rendering.edusharingRepo

import io.micrometer.context.ContextExecutorService
import io.micrometer.context.ContextSnapshotFactory
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import okhttp3.Dispatcher
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttp [Dispatcher] for every repository SDK client (an `ApiClient` is created per call, so the
 * dispatcher must live outside it).
 *
 * **Trace context:** `enqueue` (the SDK's `*Async` calls, e.g. `trackEventAsync`) runs the whole interceptor
 * chain on a dispatcher thread, where the request thread's trace context is not available - so
 * [TracePropagatingInterceptor] would find no context and send no b3 header. The executor captures the
 * context at submission (on the calling thread) and restores it on the worker. Synchronous calls are
 * unaffected.
 *
 * **Throughput:** OkHttp's defaults (64 concurrent calls, only **5 per host**) are a hard ceiling for
 * `*Async` calls, because all of them target the same repository host - anything above it queues silently
 * in the dispatcher. The limits are therefore explicit and tunable via `app.repository-http.*`; raise them
 * before adding more async repository calls or if `trackEventAsync` calls start to queue up. Saturation is
 * visible as `repository.http.dispatcher.calls{state=queued|running}` - a sustained `queued` > 0 means the
 * limits (or the repository) are the bottleneck.
 */
@Configuration
class RepositoryHttpConfig(
    @param:Value($$"${app.repository-http.max-requests:256}")
    private val maxRequests: Int,
    @param:Value($$"${app.repository-http.max-requests-per-host:128}")
    private val maxRequestsPerHost: Int,
) {

    @Bean(destroyMethod = "shutdown")
    fun repositoryHttpExecutor(): ExecutorService =
        // Same shape as OkHttp's default pool: unbounded cached threads, concurrency capped by the Dispatcher.
        ThreadPoolExecutor(0, Int.MAX_VALUE, 60L, TimeUnit.SECONDS, SynchronousQueue()) { runnable ->
            Thread(runnable, "repo-sdk-dispatcher").apply { isDaemon = true }
        }

    @Bean
    fun repositoryDispatcher(repositoryHttpExecutor: ExecutorService, meterRegistry: MeterRegistry): Dispatcher {
        val snapshotFactory = ContextSnapshotFactory.builder().build()
        val dispatcher = Dispatcher(ContextExecutorService.wrap(repositoryHttpExecutor) { snapshotFactory.captureAll() }).apply {
            maxRequests = this@RepositoryHttpConfig.maxRequests
            maxRequestsPerHost = this@RepositoryHttpConfig.maxRequestsPerHost
        }
        Gauge.builder("repository.http.dispatcher.calls", dispatcher) { it.queuedCallsCount().toDouble() }
            .tag("state", "queued")
            .description("Async repository SDK calls waiting for a free dispatcher slot")
            .register(meterRegistry)
        Gauge.builder("repository.http.dispatcher.calls", dispatcher) { it.runningCallsCount().toDouble() }
            .tag("state", "running")
            .description("Async repository SDK calls currently executing")
            .register(meterRegistry)
        return dispatcher
    }
}
