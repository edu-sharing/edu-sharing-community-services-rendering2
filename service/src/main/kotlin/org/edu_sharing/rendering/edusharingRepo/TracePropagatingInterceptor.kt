package org.edu_sharing.rendering.edusharingRepo

import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import okhttp3.Interceptor
import okhttp3.Response
import org.springframework.stereotype.Component

/**
 * Propagates the current trace context onto outbound edu-sharing repository requests as b3 headers.
 *
 * The repository calls go through the generated SDK's OkHttp [okhttp3.OkHttpClient], which Micrometer
 * cannot auto-instrument (unlike the WebClient and RabbitMQ paths). This interceptor injects the active
 * trace context via the configured [Propagator] (b3 format), keeping these calls part of the same trace.
 * It is a no-op when there is no active trace context.
 */
@Component
class TracePropagatingInterceptor(
    private val tracer: Tracer,
    private val propagator: Propagator,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val context = tracer.currentTraceContext().context()
            ?: return chain.proceed(chain.request())
        val builder = chain.request().newBuilder()
        propagator.inject(context, builder) { carrier, key, value -> carrier?.header(key, value) }
        return chain.proceed(builder.build())
    }
}
