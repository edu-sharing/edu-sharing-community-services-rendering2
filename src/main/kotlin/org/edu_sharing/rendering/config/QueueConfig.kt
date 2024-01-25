package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.queue.FileJobReceiver
import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QueueConfig {

    val fileQueueName = "file_transfer_queue"

    val topicExchangeName = "rendering-exchange"

    val fileRoutingKey = "file"

    @Bean
    fun fileTransferQueue(): Queue {
        return Queue(fileQueueName, false)
    }

    @Bean
    fun topicExchange(): TopicExchange {
        return TopicExchange(topicExchangeName)
    }

    @Bean
    fun fileBinding(fileTransferQueue: Queue, topicExchange: TopicExchange): Binding {
        return BindingBuilder.bind(fileTransferQueue).to(topicExchange).with(fileRoutingKey)
    }

    @Bean
    fun fileContainer(connectionFactory: ConnectionFactory, fileListenerAdapter: MessageListenerAdapter): SimpleMessageListenerContainer {
        val container = SimpleMessageListenerContainer()
        container.connectionFactory = connectionFactory
        container.setQueueNames(fileQueueName)
        container.setMessageListener(fileListenerAdapter)
        return container
    }

    @Bean
    fun fileListenerAdapter(receiver: FileJobReceiver): MessageListenerAdapter {
        val adapter = MessageListenerAdapter(receiver, "receiveMessage")
        adapter.setMessageConverter(messageConverter())
        return adapter
    }

    @Bean
    fun messageConverter(): MessageConverter {
        return Jackson2JsonMessageConverter()
    }

    @Bean
    fun amqpTemplate(connectionFactory: ConnectionFactory): AmqpTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = messageConverter()
        return template
    }
}
