package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.SimpleAsyncTaskExecutor

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
     * [DirectMessageListenerContainer] factory for all queue consumers. Each queue's
     * `consumersPerQueue` (from the `@RabbitListener` `concurrency` attribute, bound to
     * `app.queue.<queue>.consumersPerQueue`) consumers are registered at the broker up front, so a
     * burst is fanned out to all of them immediately — there is no `SimpleMessageListenerContainer`
     * auto-scale ramp. The consumers share [rabbitConsumerExecutor], so an idle queue costs a single
     * channel but zero busy threads. CPU-bound work (image, eduHtml inflate, av's ffmpeg process) is
     * bounded per queue by `consumersPerQueue` and physically by the role split, not by a thread pool.
     */
    @Bean
    fun queueListenerContainerFactory(
        rabbitConnectionFactory: ConnectionFactory,
        messageConverter: MessageConverter,
        rabbitConsumerExecutor: AsyncTaskExecutor,
        queueConsumerMetrics: QueueConsumerMetrics,
        @Value($$"${app.queue.prefetch:1}") prefetch: Int,
    ): RabbitListenerContainerFactory<DirectMessageListenerContainer> {
        val factory = DirectRabbitListenerContainerFactory()
        factory.setConnectionFactory(rabbitConnectionFactory)
        factory.setMessageConverter(messageConverter)
        factory.setDefaultRequeueRejected(false)
        factory.setTaskExecutor(rabbitConsumerExecutor)
        factory.setPrefetchCount(prefetch)
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
