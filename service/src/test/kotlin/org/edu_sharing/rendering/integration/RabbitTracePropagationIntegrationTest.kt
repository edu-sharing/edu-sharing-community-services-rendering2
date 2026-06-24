package org.edu_sharing.rendering.integration

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TRACE_TEST_QUEUE = "trace_test_queue"
private const val TRACE_TEST_EXCHANGE = "trace_test_exchange"
private const val TRACE_TEST_KEY = "trace_test_key"

/**
 * Verifies that a trace started before publishing flows through RabbitMQ and is continued by the
 * consumer (so a job keeps the originating request's trace id across the async queue boundary).
 *
 * The probe listener deliberately omits an explicit `containerFactory`, so it runs on the
 * auto-configured (default) listener factory — the same one [org.edu_sharing.rendering.renderingJob.queue.JobReceiver]
 * uses. This guards the fix that enables observation on that factory
 * (`spring.rabbitmq.listener.simple.observation-enabled=true`); without it the listener would start a
 * brand-new trace instead of continuing the publisher's.
 */
@Import(TraceProbeListener::class)
class RabbitTracePropagationIntegrationTest(
    @param:Autowired private val amqpTemplate: AmqpTemplate,
    @param:Autowired private val tracer: Tracer,
    @param:Autowired private val observationRegistry: ObservationRegistry,
    @param:Autowired private val probe: TraceProbeListener,
) : AbstractIntegrationTest() {

    @Test
    fun tracePropagatesThroughRabbitToDefaultFactoryListener() {
        val publishedTraceId = arrayOfNulls<String>(1)

        // Publish inside an observation scope, mirroring a request-driven publish (e.g. MainJobCreationService).
        Observation.createNotStarted("test-publish", observationRegistry).observe(Runnable {
            publishedTraceId[0] = tracer.currentSpan()?.context()?.traceId()
            amqpTemplate.convertAndSend(TRACE_TEST_EXCHANGE, TRACE_TEST_KEY, TraceTestMessage("ping"))
        })

        assert(publishedTraceId[0] != null) { "Expected an active span (trace id) while publishing" }
        assert(probe.latch.await(10, TimeUnit.SECONDS)) { "Listener did not receive the message in time" }
        assert(probe.receivedTraceId == publishedTraceId[0]) {
            "Listener should continue the publisher's trace ${publishedTraceId[0]}, but got ${probe.receivedTraceId}"
        }
    }
}

data class TraceTestMessage(val value: String)

/**
 * Test-only listener on the default (auto-configured) container factory that records the trace id
 * active while the message is being handled.
 */
class TraceProbeListener(private val tracer: Tracer) {

    val latch = CountDownLatch(1)

    @Volatile
    var receivedTraceId: String? = null

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = TRACE_TEST_QUEUE, durable = "false"),
                exchange = Exchange(name = TRACE_TEST_EXCHANGE, type = "topic"),
                key = [TRACE_TEST_KEY],
            )
        ]
    )
    fun receive(message: TraceTestMessage) {
        receivedTraceId = tracer.currentSpan()?.context()?.traceId()
        latch.countDown()
    }
}
