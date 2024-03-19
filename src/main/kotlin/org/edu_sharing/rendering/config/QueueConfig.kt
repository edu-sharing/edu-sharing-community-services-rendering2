package org.edu_sharing.rendering.config

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class QueueConfig {

    @Bean
    fun singlePrefetchConnectionFactory(rabbitConnectionFactory: ConnectionFactory?): RabbitListenerContainerFactory<SimpleMessageListenerContainer?>? {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(rabbitConnectionFactory)
        factory.setPrefetchCount(1)
        factory.setConcurrentConsumers(1)
        return factory
    }
}
