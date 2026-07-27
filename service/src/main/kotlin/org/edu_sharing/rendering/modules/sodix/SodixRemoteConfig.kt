package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.config.WebClientConfig
import org.edu_sharing.rendering.renderingJob.queue.RemoteListenerContainerFactorySupport
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.resources.ConnectionProvider

/**
 * Module-local wiring of Sodix's dedicated remote HTTP pool and its decoupled listener container. Sized
 * from the queue's own [org.edu_sharing.rendering.renderingJob.queue.QueueSpec] (`app.queue.sodix.*`,
 * `mode=REMOTE`): `concurrency` (K) sizes the HTTP pool, `effectiveRemotePrefetch` the broker prefetch —
 * so each remote queue is tuned independently. Sodix talks to a single SNI-enabled host, so one pool +
 * one builder suffices.
 */
@Configuration
class SodixRemoteConfig(
    private val webClientConfig: WebClientConfig,
    private val remoteListenerContainerFactorySupport: RemoteListenerContainerFactorySupport,
    private val spec: SodixQueueProperties,
) {
    private val connectionProvider: ConnectionProvider =
        webClientConfig.remoteConnectionProvider("sodix", spec.concurrency)

    @Bean("sodixRemoteWebClientBuilder")
    fun sodixRemoteWebClientBuilder(): WebClient.Builder =
        webClientConfig.remoteWebClientBuilder(connectionProvider)

    @Bean("sodixRemoteListenerContainerFactory")
    fun sodixRemoteListenerContainerFactory(): RabbitListenerContainerFactory<DirectMessageListenerContainer> =
        remoteListenerContainerFactorySupport.create(spec.effectiveRemotePrefetch)
}
