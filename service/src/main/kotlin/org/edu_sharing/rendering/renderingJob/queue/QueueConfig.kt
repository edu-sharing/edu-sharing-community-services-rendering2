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
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.SimpleAsyncTaskExecutor
import java.util.concurrent.Executors

@Configuration
class QueueConfig {

    /**
     * Shared executor for every queue-listener invocation, backed by **virtual threads**. A consumer
     * only occupies a (cheap) thread *while* it processes a message and parks for free during the
     * blocking waits that dominate this service's work — the off-heap ffmpeg process (av), remote
     * converter/API HTTP calls (document, jupyter, sodix, omega, ddb, …), and Mongo/S3 I/O. Idle
     * consumers therefore hold no thread at all.
     *
     * Deliberately a dedicated executor and **not** the global `spring.threads.virtual.enabled` flag:
     * the latter would also switch Tomcat and every other pool to virtual threads.
     */
    @Bean
    fun rabbitConsumerExecutor(): AsyncTaskExecutor =
        SimpleAsyncTaskExecutor("rabbit-consumer-").apply { setVirtualThreads(true) }

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
     * `null`) executor explicitly. Left unconfigured, the RabbitMQ client falls back to its own
     * `ConsumerWorkService.DEFAULT_NUM_THREADS`, sized from `Runtime.availableProcessors()` — on a
     * cgroup-limited pod (`jobmanager`/`master` at 250m, `controller`/`converter` at ≤1000m CPU) that
     * rounds up to exactly 1, so every queue's carefully tuned `app.queue.<x>.concurrency` collapses to
     * one message processed at a time across the *entire* connection, independent of which queue or
     * module it belongs to. A `BeanPostProcessor` is used (rather than a `@Bean AbstractConnectionFactory`
     * override) because Spring Boot's auto-configured `CachingConnectionFactory` is the one that must be
     * mutated, and this runs regardless of which bean method produced it. Declared via the companion
     * object (a real `static` method) as Spring recommends for `BeanPostProcessor` `@Bean` methods — a
     * non-static one forces early instantiation of this whole `@Configuration` class, before all
     * `BeanPostProcessor`s are registered — logging a "not eligible for getting processed by all
     * BeanPostProcessors" warning for it. Harmless here (`QueueConfig`'s other beans need no such
     * post-processing) and left as an instance method: a `@JvmStatic` companion-object method would
     * silence it, but Spring also registers the companion object itself as a bean in that case, which
     * this module's role tests (exact per-role bean-set assertions) would then need to special-case.
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
        return template
    }
}
