package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.config.WebClientConfig
import org.edu_sharing.rendering.renderingJob.queue.RemoteListenerContainerFactorySupport
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.resources.ConnectionProvider

/**
 * Module-local wiring of Omega's dedicated remote HTTP pool and its decoupled listener container. Sized
 * from the queue's own [org.edu_sharing.rendering.renderingJob.queue.QueueSpec] (`app.queue.omega.*`,
 * `mode=REMOTE`). Omega uses two builders sharing one pool: `maxConnections` is applied per remote host, so
 * the SNI-hostile API host (cp.sodis.de) and the SNI-required edupool validation host each get K connections.
 */
@Configuration
class OmegaRemoteConfig(
    private val webClientConfig: WebClientConfig,
    private val remoteListenerContainerFactorySupport: RemoteListenerContainerFactorySupport,
    private val spec: OmegaQueueProperties,
) {
    private val connectionProvider: ConnectionProvider =
        webClientConfig.remoteConnectionProvider("omega", spec.concurrency)

    @Bean("omegaRemoteWebClientBuilder")
    fun omegaRemoteWebClientBuilder(): WebClient.Builder =
        webClientConfig.remoteWebClientBuilder(connectionProvider)

    @Bean("omegaNoSniRemoteWebClientBuilder")
    fun omegaNoSniRemoteWebClientBuilder(): WebClient.Builder =
        webClientConfig.remoteWebClientBuilder(connectionProvider, disableSni = true)

    @Bean("omegaRemoteListenerContainerFactory")
    fun omegaRemoteListenerContainerFactory(): RabbitListenerContainerFactory<DirectMessageListenerContainer> =
        remoteListenerContainerFactorySupport.create(spec.effectiveRemotePrefetch)
}
