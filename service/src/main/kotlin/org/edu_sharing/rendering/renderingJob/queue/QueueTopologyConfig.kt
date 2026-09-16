package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarable
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares every business queue's topology (queue + binding to the topic exchange) unconditionally,
 * regardless of `app.roles` — unlike the per-queue declaration on each `@RabbitListener` (role-gated,
 * e.g. `@ConditionalOnConverter`), which only exists on pods that actually consume that queue.
 *
 * Without this, a pod that only *publishes* (e.g. a `master`/`controller`-only deployment serving
 * `POST /public/renderdata`, or any pod during a rolling-deploy window before a consumer role has
 * reconnected) can send a message to the topic exchange before any queue is bound to that routing key.
 * A topic exchange silently drops a message that matches no binding — the publish "succeeds" (`mandatory`
 * + the `ReturnsCallback` in [QueueConfig.amqpTemplate] will at least log it now), but the message and the
 * job behind it are still gone. Declaring the topology from every pod closes that window: whichever pod
 * opens the AMQP connection first, consumer or publisher, ensures every queue and its binding exist.
 *
 * Fully generic: [queueSpecs] collects every [QueueSpec] bean in the context (one per module,
 * `@ConfigurationProperties("app.queue.<module>")`, unconditional like all the others), so adding a new
 * module's queue needs no change here — declaring its `*QueueProperties` bean is enough. Per-queue extra
 * broker arguments (`x-max-priority`, `x-single-active-consumer`) come from [QueueSpec.declareArguments],
 * not from this class — see its doc for how that stays in sync with the `@RabbitListener` side.
 *
 * Redundant with (not a replacement for) the annotation-based declarations: on a pod that also runs the
 * consuming role, both this bean and the `@RabbitListener` declare the *same* queue/binding on connection
 * open. RabbitMQ's `queue.declare`/`exchange.declare`/`queue.bind` are idempotent for identical
 * parameters, so the redundant declaration is a harmless no-op, not a `406 PRECONDITION_FAILED` (that only
 * triggers on a *mismatched* redeclare) — durability/exclusive/auto-delete here match every receiver's
 * `@Queue` annotation exactly, and arguments match as long as [QueueSpec.declareArguments] does.
 *
 * Deliberately excludes [org.edu_sharing.rendering.edusharingRepo.cors.CorsAllowedOriginsReceiver]'s
 * broadcast queue: it is an anonymous, per-connection `exclusive`+`autoDelete` queue (not backed by a
 * [QueueSpec]), and its only publisher ([org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService])
 * shares the exact same role gate (`@ConditionalOnMasterOrController`) as the receiver, so the race this
 * class fixes cannot occur for it — a queue that only ever exists on one specific connection can't be
 * usefully pre-declared from another pod anyway.
 */
@Configuration
class QueueTopologyConfig(
    private val queueProperties: QueueProperties,
    private val queueSpecs: List<QueueSpec>,
) {

    @Bean
    fun queueTopology(): Declarables {
        val exchange = TopicExchange(queueProperties.topicExchange)
        val declarables = mutableListOf<Declarable>(exchange)
        queueSpecs.forEach { spec ->
            val queue = Queue(spec.name, true, false, false, spec.declareArguments)
            declarables += queue
            declarables += BindingBuilder.bind(queue).to(exchange).with(spec.key)
        }
        return Declarables(declarables)
    }
}
