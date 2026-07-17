package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.config.WebClientConfig
import org.edu_sharing.rendering.renderingJob.queue.RemoteListenerContainerFactorySupport
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.resources.ConnectionProvider

/**
 * Module-local wiring of DDB's dedicated remote HTTP pool and its decoupled listener container. Sized from
 * the queue's own [org.edu_sharing.rendering.renderingJob.queue.QueueSpec] (`app.queue.ddb.*`, `mode=REMOTE`;
 * URL config lives in [DdbApiProperties]). `maxConnections` is applied per remote host, so DDB's REST and
 * IIIF hosts each get K connections from the one pool.
 */
@Configuration
class DdbRemoteConfig(
    private val webClientConfig: WebClientConfig,
    private val remoteListenerContainerFactorySupport: RemoteListenerContainerFactorySupport,
    private val spec: DdbQueueProperties,
) {
    private val connectionProvider: ConnectionProvider =
        webClientConfig.remoteConnectionProvider("ddb", spec.concurrency)

    @Bean("ddbRemoteWebClientBuilder")
    fun ddbRemoteWebClientBuilder(): WebClient.Builder =
        webClientConfig.remoteWebClientBuilder(connectionProvider)

    @Bean("ddbRemoteListenerContainerFactory")
    fun ddbRemoteListenerContainerFactory(): RabbitListenerContainerFactory<DirectMessageListenerContainer> =
        remoteListenerContainerFactorySupport.create(spec.effectiveRemotePrefetch)
}
