package org.edu_sharing.rendering.renderingJob.queue

import org.edu_sharing.rendering.testUtils.TestImages
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Exchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

/**
 * The broker facts the quorum-queue setup relies on, checked against a real RabbitMQ 4.3 - without the rest of the
 * application (and so without MongoDB), which is why this is not an `AbstractIntegrationTest`.
 */
@Testcontainers
class QuorumQueueBrokerTest {

    companion object {
        @JvmStatic
        @Container
        private val rabbit = RabbitMQContainer(TestImages.rabbitmq())
    }

    private lateinit var connectionFactory: CachingConnectionFactory
    private lateinit var admin: RabbitAdmin
    private lateinit var template: RabbitTemplate

    @BeforeEach
    fun connect() {
        connectionFactory = CachingConnectionFactory(rabbit.host, rabbit.amqpPort)
        connectionFactory.username = rabbit.adminUsername
        connectionFactory.setPassword(rabbit.adminPassword)
        admin = RabbitAdmin(connectionFactory)
        template = RabbitTemplate(connectionFactory)
    }

    @AfterEach
    fun disconnect() = connectionFactory.destroy()

    private fun queueInfo(name: String): tools.jackson.databind.JsonNode {
        val credentials = Base64.getEncoder().encodeToString("${rabbit.adminUsername}:${rabbit.adminPassword}".toByteArray())
        val request = HttpRequest.newBuilder(URI.create("${rabbit.httpUrl}/api/queues/%2F/$name"))
            .header("Authorization", "Basic $credentials").GET().build()
        return JsonMapper.builder().build().readTree(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body())
    }

    @Test
    fun `a quorum queue delivers by message priority and clamps audio's 255 to the top level`() {
        val queue = "priority-probe"
        admin.declareQueue(QueueBuilder.durable(queue).quorum().build())
        // published in this order: heights 1080/720/480 use 0/1/2, audio uses 255
        listOf(0 to "p0", 1 to "p1", 2 to "p2", 255 to "p255").forEach { (priority, payload) ->
            template.convertAndSend("", queue, payload, PriorityPostProcessor(priority))
        }
        val delivered = (1..4).map { String(template.receive(queue, 5_000)!!.body) }
        assertEquals(listOf("p255", "p2", "p1", "p0"), delivered)
    }

    @Test
    fun `a quorum queue rejects x-max-priority`() {
        // RabbitMQ 4.3: "invalid arg 'x-max-priority' ... of queue type rabbit_quorum_queue" - the docs claim it is ignored
        val connection = connectionFactory.createConnection()
        val channel = connection.createChannel(false)
        assertThrows(IOException::class.java) {
            channel.queueDeclare("max-priority-probe", true, false, false, mapOf("x-queue-type" to "quorum", "x-max-priority" to 2))
        }
        connection.close()
    }

    @Test
    fun `QueueTopologyConfig declares quorum queues, including single-active-consumer`() {
        class Standard : StandardQueueProperties()
        class Single : SingleActiveQueueProperties()
        val standard = Standard().apply { name = "topology-standard"; key = "topology_standard" }
        val single = Single().apply { name = "topology-single"; key = "topology_single" }
        val classic = Standard().apply { name = "topology-classic"; key = "topology_classic"; type = QueueType.CLASSIC }
        val queueProperties = QueueProperties().apply {
            topicExchange = "topology-exchange"
            controllerBroadcastExchange = "topology-broadcast"
        }

        QueueTopologyConfig(queueProperties, listOf(standard, single, classic)).queueTopology().declarables.forEach {
            when (it) {
                is Exchange -> admin.declareExchange(it)
                is Queue -> admin.declareQueue(it)
                is Binding -> admin.declareBinding(it)
            }
        }

        assertEquals("quorum", queueInfo("topology-standard").get("type").asString())
        assertEquals("quorum", queueInfo("topology-single").get("type").asString())
        assertTrue(queueInfo("topology-single").get("arguments").get("x-single-active-consumer").asBoolean())
        assertEquals("classic", queueInfo("topology-classic").get("type").asString())
    }
}
