package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics
import io.micrometer.context.ContextSnapshotFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.SimpleAsyncTaskExecutor
import java.time.Duration
import java.util.concurrent.Executors

@Configuration
class QueueConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Shared executor for every queue-listener invocation, backed by **virtual threads**. A consumer
     * only occupies a (cheap) thread *while* it processes a message and parks for free during the
     * blocking waits that dominate this service's work — the off-heap ffmpeg process (av), remote
     * converter/API HTTP calls (document, jupyter, sodix, omega, ddb, …), and Mongo/S3 I/O. Idle
     * consumers therefore hold no thread at all.
     *
     * Deliberately a dedicated executor and **not** the global `spring.threads.virtual.enabled` flag:
     * the latter would also switch Tomcat and every other pool to virtual threads.
     *
     * The `taskDecorator` re-attaches the trace/baggage context (MDC, current span) that
     * [AsyncAckDispatcher] would otherwise drop when it hands the REMOTE queues' (sodix/omega/ddb) work
     * off this executor: the decorator runs on the *submitting* (listener) thread, where the observation
     * opened by `queueListenerContainerFactory`'s/the REMOTE containers' `observationEnabled` is still
     * active, so [ContextSnapshotFactory.captureAll] captures it there and [io.micrometer.context.ContextSnapshot.wrap]
     * restores it on the virtual thread that actually runs the task.
     */
    @Bean
    fun rabbitConsumerExecutor(): AsyncTaskExecutor =
        SimpleAsyncTaskExecutor("rabbit-consumer-").apply {
            setVirtualThreads(true)
            val contextSnapshotFactory = ContextSnapshotFactory.builder().build()
            setTaskDecorator { runnable -> contextSnapshotFactory.captureAll().wrap(runnable) }
        }

    /**
     * Fixes a hidden, connection-wide concurrency ceiling underneath every queue's tuning.
     * `DirectMessageListenerContainer` only uses [rabbitConsumerExecutor] to bootstrap the initial
     * per-queue consumer registrations (`actualStart` → `startConsumers`) — each consumer's own message
     * handling runs synchronously inside `SimpleConsumer.handleDelivery`, called by the RabbitMQ Java
     * client on whichever thread its **own**, connection-wide `ConsumerWorkService` hands it.
     *
     * That executor comes from `AbstractConnectionFactory.executorService`
     * ([AbstractConnectionFactory.setExecutor]), which Spring passes explicitly into
     * `com.rabbitmq.client.ConnectionFactory#newConnection(ExecutorService, ...)` — **not** from
     * `com.rabbitmq.client.ConnectionFactory#setSharedExecutor`/a Boot `ConnectionFactoryCustomizer`,
     * which configure a field Spring's call bypasses entirely by always passing its own (by default
     * `null`) executor explicitly. **This was tried first and looked correct — `ConnectionFactoryCustomizer`
     * is Spring Boot's documented "fine-tune the auto-configured `ConnectionFactory`" hook — but a k6 load
     * test showed zero effect: `rendering_queue_consumers_active` still never exceeded 1 across all queues
     * combined.** Left unconfigured, the RabbitMQ client falls back to its own
     * `ConsumerWorkService.DEFAULT_NUM_THREADS`, sized from `Runtime.availableProcessors()` — on a
     * cgroup-limited pod (`jobmanager`/`master` at 250m, `controller`/`converter` at ≤1000m CPU) that
     * rounds up to exactly 1, so every queue's carefully tuned `app.queue.<x>.concurrency` collapses to
     * one message processed at a time across the *entire* connection, independent of which queue or
     * module it belongs to.
     *
     * A `BeanPostProcessor` (rather than a `@Bean CachingConnectionFactoryConfigurer` override, the other
     * available hook) is what actually reaches the bean Spring uses for the real connection: it matches
     * by type, so it works regardless of which bean method produced the `CachingConnectionFactory`, and
     * unlike overriding the configurer bean, it doesn't need to replicate Boot's constructor signature for
     * that bean (`RabbitProperties`, `RabbitConnectionDetails`, …) — which a Boot upgrade could change.
     *
     * Declared as a plain instance method, not a `@JvmStatic` companion-object one: Spring logs a "not
     * eligible for getting processed by all BeanPostProcessors" warning for a `BeanPostProcessor` `@Bean`
     * declared this way (it forces early instantiation of this whole `@Configuration` class, before all
     * `BeanPostProcessor`s are registered) and recommends `static` instead — harmless here, since
     * `QueueConfig`'s other beans need no such post-processing themselves. The `@JvmStatic` alternative was
     * tried and reverted: Spring then also registers the companion object itself as a bean, which this
     * module's role tests (exact per-role bean-set assertions) would need to special-case for no real gain.
     */
    @Bean
    fun rabbitConnectionFactoryExecutorPostProcessor(): BeanPostProcessor =
        object : BeanPostProcessor {
            override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
                if (bean is AbstractConnectionFactory) {
                    bean.setExecutor(Executors.newVirtualThreadPerTaskExecutor())
                }
                return bean
            }
        }

    /**
     * [DirectMessageListenerContainer] factory for all STANDARD/SINGLE_ACTIVE queue consumers. Each queue's
     * [org.edu_sharing.rendering.renderingJob.queue.QueueSpec.effectiveConcurrency] (from the
     * `@RabbitListener` `concurrency` attribute, resolved via SpEL against [QueueProperties]) consumers are
     * registered at the broker up front, so a burst is fanned out to all of them immediately — there is no
     * `SimpleMessageListenerContainer` auto-scale ramp. [rabbitConsumerExecutor] only runs that bootstrap;
     * actual message handling concurrency is governed by [rabbitConnectionFactoryExecutorPostProcessor]
     * instead (see its doc). CPU-bound work (image, eduHtml inflate, av's ffmpeg process) is bounded per queue by its
     * `concurrency` and physically by the role split, not by a thread pool. Prefetch is a single global
     * value ([QueueProperties.prefetch]) — a `DirectRabbitListenerContainerFactory`'s prefetch applies to
     * every container it builds, so it cannot be per-queue here; per-queue prefetch exists only for REMOTE
     * queues via their own factory.
     */
    @Bean
    fun queueListenerContainerFactory(
        rabbitConnectionFactory: ConnectionFactory,
        messageConverter: MessageConverter,
        rabbitConsumerExecutor: AsyncTaskExecutor,
        queueConsumerMetrics: QueueConsumerMetrics,
        queueProperties: QueueProperties,
    ): RabbitListenerContainerFactory<DirectMessageListenerContainer> {
        val factory = DirectRabbitListenerContainerFactory()
        factory.setConnectionFactory(rabbitConnectionFactory)
        factory.setMessageConverter(messageConverter)
        factory.setDefaultRequeueRejected(false)
        factory.setTaskExecutor(rabbitConsumerExecutor)
        factory.setPrefetchCount(queueProperties.prefetch)
        // Track messages currently being processed per queue → `rendering.queue.consumers.active`.
        factory.setAdviceChain(queueConsumerMetrics)
        // durable=false / anonymous (fanout) queues are re-declared on (re)connect; a transiently
        // missing queue must not tear the container down.
        factory.setMissingQueuesFatal(false)
        // Shared across every STANDARD/SINGLE_ACTIVE queue, so this must cover the slowest of them: av
        // (ffmpeg, guarded by app.converter.av.conversionTimeout=PT30M) — the Spring AMQP default (5s)
        // would otherwise abandon an in-flight conversion on every pod stop/rolling deploy instead of
        // letting it finish. Fast queues (h5p-lookup, image, ...) drain immediately regardless; this is
        // only an upper bound. K8s' terminationGracePeriod (deploy/.../values.yaml) must be >= this, or
        // the pod is SIGKILLed before the graceful drain completes.
        // shutdownTimeout lives on the container, not the factory - setContainerCustomizer is the hook
        // the factory exposes to configure every container it creates.
        factory.setContainerCustomizer { container -> container.setShutdownTimeout(Duration.ofMinutes(35).toMillis()) }
        // Continue the trace across the async queue boundary (reads trace context from message headers).
        factory.setObservationEnabled(true)
        return factory
    }

    /**
     * The high-fan-out **remote** queues (sodix, omega, ddb — [QueueMode.REMOTE]) do NOT use this factory.
     * They decouple consumption from processing via [AsyncAckDispatcher] on a per-module listener container
     * built by [org.edu_sharing.rendering.renderingJob.queue.RemoteListenerContainerFactorySupport] — each
     * remote module wires its own factory sized from its [QueueSpec] (K = `app.queue.<x>.concurrency`) so the
     * queues are tuned independently. See the `*RemoteConfig` classes in the sodix/omega/ddb modules.
     */

    /**
     * Template config
     */
    @Bean
    fun messageConverter(): MessageConverter {
        return JacksonJsonMessageConverter()
    }

    @Bean
    fun amqpTemplate(connectionFactory: ConnectionFactory, messageConverter: MessageConverter): AmqpTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = messageConverter
        // Inject the current trace context into message headers when publishing.
        template.setObservationEnabled(true)
        // `mandatory` + a ReturnsCallback surface an unroutable publish (no matching queue binding — e.g.
        // mid rolling-deploy, before any converter pod has (re-)declared the target queue) instead of the
        // message silently vanishing, leaving its job stuck QUEUED until StaleJobReaper's queued-timeout
        // safety net eventually catches it. Requires spring.rabbitmq.publisher-returns=true.
        template.setMandatory(true)
        template.setReturnsCallback { returned ->
            log.error(
                "Unroutable published message (no matching queue binding): exchange={}, routingKey={}, " +
                    "replyCode={}, replyText={}, body={}",
                returned.exchange, returned.routingKey, returned.replyCode, returned.replyText,
                String(returned.message.body, Charsets.UTF_8),
            )
        }
        // Publisher confirms surface a broker-side nack (e.g. the broker itself rejected the publish) that
        // mandatory/returns cannot catch — that only covers routing, not the publish reaching the broker
        // at all. Requires spring.rabbitmq.publisher-confirm-type=correlated.
        template.setConfirmCallback { correlationData, ack, cause ->
            if (!ack) {
                log.error("Publish not confirmed by broker (nacked): correlationData={}, cause={}", correlationData, cause)
            }
        }
        return template
    }
}
