package org.edu_sharing.rendering.integration

import org.edu_sharing.rendering.renderingJob.queue.PriorityPostProcessor
import org.edu_sharing.rendering.renderingJob.queue.QueueSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.QueueBuilder
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

/**
 * Against a real RabbitMQ 4.3 broker: every business queue exists as a **quorum** queue and has a consumer, and a
 * quorum queue orders by the message priority the way the AV queue relies on.
 *
 * The consumer check is what catches a mismatch between the two declarations of a queue (`QueueTopologyConfig` and the
 * receiver's `@RabbitListener`): RabbitMQ answers the second one with `406` and `mismatchedQueuesFatal=false` swallows
 * it, so the queue would exist but never be consumed.
 */
class QuorumQueueIntegrationTest(
    @param:Autowired private val queueSpecs: List<QueueSpec>,
    @param:Autowired private val amqpAdmin: AmqpAdmin,
    @param:Autowired private val amqpTemplate: AmqpTemplate,
) : AbstractIntegrationTest() {

    private val mapper = JsonMapper.builder().build()
    private val http = HttpClient.newHttpClient()

    private fun queuesByName(): Map<String, JsonNode> {
        val credentials = Base64.getEncoder().encodeToString("${rabbitAdminUser()}:${rabbitAdminPassword()}".toByteArray())
        val request = HttpRequest.newBuilder(URI.create("${rabbitManagementUrl()}/api/queues/%2F"))
            .header("Authorization", "Basic $credentials").GET().build()
        val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()
        return mapper.readTree(body).associateBy { it.get("name").asString() }
    }

    @Test
    fun everyBusinessQueueIsAQuorumQueueWithAConsumer() {
        assertEquals(15, queueSpecs.size, "the 15 business queues, found ${queueSpecs.map { it.name }}")
        // consumers are registered asynchronously after the context starts
        val deadline = System.currentTimeMillis() + 60_000
        var queues = queuesByName()
        while (System.currentTimeMillis() < deadline &&
            queueSpecs.any { (queues[it.name]?.get("consumers")?.asInt() ?: 0) == 0 }
        ) {
            Thread.sleep(500)
            queues = queuesByName()
        }
        queueSpecs.forEach { spec ->
            val queue = queues[spec.name]
            assertTrue(queue != null, "queue ${spec.name} was not declared")
            assertEquals("quorum", queue!!.get("type").asString(), "${spec.name} must be a quorum queue")
            assertTrue(queue.get("durable").asBoolean(), "${spec.name} must be durable")
            assertTrue(
                queue.get("consumers").asInt() > 0,
                "${spec.name} has no consumer - its @RabbitListener declaration probably differs from QueueTopologyConfig",
            )
        }
    }

    @Test
    fun aQuorumQueueDeliversByMessagePriorityUpTo31() {
        val queue = "quorum-priority-probe"
        amqpAdmin.declareQueue(QueueBuilder.durable(queue).quorum().build())
        // published in this order; audio uses 255, which a quorum queue clamps to its highest level (31)
        listOf(0 to "p0", 1 to "p1", 2 to "p2", 255 to "p255").forEach { (priority, payload) ->
            amqpTemplate.convertAndSend("", queue, payload, PriorityPostProcessor(priority))
        }
        val delivered = (1..4).map { amqpTemplate.receiveAndConvert(queue) }
        assertEquals(listOf("p255", "p2", "p1", "p0"), delivered)
        amqpAdmin.deleteQueue(queue)
    }
}
