package org.edu_sharing.rendering.renderingJob.queue

import org.edu_sharing.rendering.modules.av.AvQueueProperties
import org.edu_sharing.rendering.modules.av.video.VideoConverterConfig
import org.edu_sharing.rendering.modules.binder.BinderPreviewQueueProperties
import org.edu_sharing.rendering.modules.binder.BinderQueueProperties
import org.edu_sharing.rendering.modules.ddb.DdbQueueProperties
import org.edu_sharing.rendering.modules.document.DocumentQueueProperties
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlQueueProperties
import org.edu_sharing.rendering.modules.h5p.H5pLookupQueueProperties
import org.edu_sharing.rendering.modules.h5p.H5pQueueProperties
import org.edu_sharing.rendering.modules.image.ImageQueueProperties
import org.edu_sharing.rendering.modules.jupyter.JupyterQueueProperties
import org.edu_sharing.rendering.modules.moodle.MoodleQueueProperties
import org.edu_sharing.rendering.modules.omega.OmegaQueueProperties
import org.edu_sharing.rendering.modules.onyx.OnyxQueueProperties
import org.edu_sharing.rendering.modules.sodix.SodixQueueProperties
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
 * Redundant with (not a replacement for) the annotation-based declarations: on a pod that also runs the
 * consuming role, both this bean and the `@RabbitListener` declare the *same* queue/binding on connection
 * open. RabbitMQ's `queue.declare`/`exchange.declare`/`queue.bind` are idempotent for identical
 * parameters, so the redundant declaration is a harmless no-op, not a `406 PRECONDITION_FAILED` (that only
 * triggers on a *mismatched* redeclare) — durability/exclusive/auto-delete here match every receiver's
 * `@Queue` annotation exactly. Per-queue extra arguments (`x-max-priority` for av,
 * `x-single-active-consumer` for h5p import) are duplicated here from
 * [AvReceiver][org.edu_sharing.rendering.modules.av.AvReceiver] and
 * [H5pImportReceiver][org.edu_sharing.rendering.modules.h5p.H5pImportReceiver] and **must be kept in
 * sync** with them, or the two declarations would mismatch and the annotation-based one would start
 * failing with `406` on whichever pod runs both.
 *
 * Deliberately excludes [org.edu_sharing.rendering.edusharingRepo.cors.CorsAllowedOriginsReceiver]'s
 * broadcast queue: it is an anonymous, per-connection `exclusive`+`autoDelete` queue, and its only
 * publisher ([org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService]) shares the exact same role
 * gate (`@ConditionalOnMasterOrController`) as the receiver, so the race this class fixes cannot occur for
 * it — a queue that only ever exists on one specific connection can't be usefully pre-declared from
 * another pod anyway.
 */
@Configuration
class QueueTopologyConfig(
    private val queueProperties: QueueProperties,
    private val jobQueueProperties: JobQueueProperties,
    private val imageQueueProperties: ImageQueueProperties,
    private val avQueueProperties: AvQueueProperties,
    private val videoConverterConfig: VideoConverterConfig,
    private val moodleQueueProperties: MoodleQueueProperties,
    private val eduHtmlQueueProperties: EduHtmlQueueProperties,
    private val h5pLookupQueueProperties: H5pLookupQueueProperties,
    private val h5pQueueProperties: H5pQueueProperties,
    private val documentQueueProperties: DocumentQueueProperties,
    private val jupyterQueueProperties: JupyterQueueProperties,
    private val onyxQueueProperties: OnyxQueueProperties,
    private val binderQueueProperties: BinderQueueProperties,
    private val binderPreviewQueueProperties: BinderPreviewQueueProperties,
    private val sodixQueueProperties: SodixQueueProperties,
    private val omegaQueueProperties: OmegaQueueProperties,
    private val ddbQueueProperties: DdbQueueProperties,
) {

    @Bean
    fun queueTopology(): Declarables {
        val exchange = TopicExchange(queueProperties.topicExchange)
        val declarables = mutableListOf<Declarable>(exchange)

        fun declare(spec: QueueSpec, arguments: Map<String, Any> = emptyMap()) {
            val queue = Queue(spec.name, true, false, false, arguments)
            declarables += queue
            declarables += BindingBuilder.bind(queue).to(exchange).with(spec.key)
        }

        declare(jobQueueProperties)
        declare(imageQueueProperties)
        // Must match AvReceiver's x-max-priority argument.
        declare(
            avQueueProperties,
            videoConverterConfig.getMaxPriority()?.let { mapOf("x-max-priority" to it) } ?: emptyMap()
        )
        declare(moodleQueueProperties)
        declare(eduHtmlQueueProperties)
        declare(h5pLookupQueueProperties)
        // Must match H5pImportReceiver's x-single-active-consumer argument.
        declare(h5pQueueProperties, mapOf("x-single-active-consumer" to h5pQueueProperties.singleActiveConsumer))
        declare(documentQueueProperties)
        declare(jupyterQueueProperties)
        declare(onyxQueueProperties)
        declare(binderQueueProperties)
        declare(binderPreviewQueueProperties)
        declare(sodixQueueProperties)
        declare(omegaQueueProperties)
        declare(ddbQueueProperties)

        return Declarables(declarables)
    }
}
