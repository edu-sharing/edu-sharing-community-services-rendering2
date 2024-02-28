package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.queue.AvReceiver
import org.edu_sharing.rendering.queue.ImageReceiver
import org.edu_sharing.rendering.queue.JobReceiver
import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QueueConfig {

    /**
     * Variables from app props
     */

    @Value("\${edu_sharing.jobQueueName}")
    lateinit var jobQueueName: String

    @Value("\${edu_sharing.imageQueueName}")
    lateinit var imageQueueName: String

    @Value("\${edu_sharing.avQueueName}")
    lateinit var avQueueName: String

    @Value("\${edu_sharing.topicExchangeName}")
    lateinit var topicExchangeName: String

    @Value("\${edu_sharing.jobRoutingKey}")
    lateinit var jobRoutingKey: String

    @Value("\${edu_sharing.imageRoutingKey}")
    lateinit var imageRoutingKey: String

    @Value("\${edu_sharing.avRoutingKey}")
    lateinit var avRoutingKey: String


    /**
     * Queue definitions
     */

    @Bean
    fun renderingJobQueue(): Queue {
        return Queue(jobQueueName, false)
    }

    @Bean
    fun imageJobQueue(): Queue {
        return Queue(imageQueueName, false)
    }

    @Bean
    fun avJobQueue(): Queue {
        return Queue(avQueueName, false)
    }

    /**
     * Topic exchange
     */

    @Bean
    fun topicExchange(): TopicExchange {
        return TopicExchange(topicExchangeName)
    }

    /**
     * Bindings: Binding queues to exchanges with routing keys
     */

    @Bean
    fun renderingJobBinding(renderingJobQueue: Queue, topicExchange: TopicExchange): Binding {
        return BindingBuilder.bind(renderingJobQueue).to(topicExchange).with(jobRoutingKey)
    }

    @Bean
    fun imageJobBinding(imageJobQueue: Queue, topicExchange: TopicExchange): Binding {
        return BindingBuilder.bind(imageJobQueue).to(topicExchange).with(imageRoutingKey)
    }

    @Bean
    fun avJobBinding(avJobQueue: Queue, topicExchange: TopicExchange): Binding {
        return BindingBuilder.bind(avJobQueue).to(topicExchange).with(avRoutingKey)
    }

    /**
     * Listener container
     *
     * And baby you can turn me on! And off, for that matter...
     */

    @Bean
    fun jobContainer(connectionFactory: ConnectionFactory, jobListenerAdapter: MessageListenerAdapter): SimpleMessageListenerContainer {
        val container = SimpleMessageListenerContainer()
        container.connectionFactory = connectionFactory
        container.setQueueNames(jobQueueName)
        container.setMessageListener(jobListenerAdapter)
        return container
    }

    @Bean
    fun imageJobContainer(connectionFactory: ConnectionFactory, imageListenerAdapter: MessageListenerAdapter): SimpleMessageListenerContainer  {
        val container = SimpleMessageListenerContainer()
        container.connectionFactory = connectionFactory
        container.setQueueNames(imageQueueName)
        container.setMessageListener(imageListenerAdapter)
        return container
    }

    @Bean
    fun avJobContainer(connectionFactory: ConnectionFactory, avListenerAdapter: MessageListenerAdapter): SimpleMessageListenerContainer {
        val container = SimpleMessageListenerContainer()
        container.connectionFactory = connectionFactory
        container.setQueueNames(imageQueueName)
        container.setMessageListener(avListenerAdapter)
        return container
    }

    /**
     * Listener: Specifying listener classes and methods
     */

    @Bean
    fun jobListenerAdapter(receiver: JobReceiver): MessageListenerAdapter {
        val adapter = MessageListenerAdapter(receiver, "receiveMessage")
        adapter.setMessageConverter(messageConverter())
        return adapter
    }

    @Bean
    fun imageListenerAdapter(receiver: ImageReceiver): MessageListenerAdapter {
        val adapter = MessageListenerAdapter(receiver, "receiveMessage")
        adapter.setMessageConverter(messageConverter())
        return adapter
    }

    @Bean
    fun avListenerAdapter(receiver: AvReceiver): MessageListenerAdapter {
        val adapter = MessageListenerAdapter(receiver, "receiveMessage")
        adapter.setMessageConverter(messageConverter())
        return adapter
    }

    /**
     * Template config
     */

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
