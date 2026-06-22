package org.edu_sharing.rendering.renderingJob.queue

import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QueueConfig {

    @Bean
    fun singlePrefetchConnectionFactory(rabbitConnectionFactory: ConnectionFactory, messageConverter: MessageConverter): RabbitListenerContainerFactory<SimpleMessageListenerContainer> {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setDefaultRequeueRejected(false);
        factory.setConnectionFactory(rabbitConnectionFactory)
        factory.setPrefetchCount(1)
        factory.setConcurrentConsumers(1)
        factory.setMessageConverter(messageConverter)
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
