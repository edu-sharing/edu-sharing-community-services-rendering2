package org.edu_sharing.rendering.modules.omega

import org.edu_sharing.rendering.config.WebClientConfig
import org.edu_sharing.rendering.renderingJob.queue.ImportListenerContainerFactorySupport
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.resources.ConnectionProvider

/**
 * Module-local wiring of Omega's dedicated import HTTP pool and its decoupled listener container. Sized
 * from the queue's own [org.edu_sharing.rendering.renderingJob.queue.QueueSpec] (`app.queue.omega.*`,
 * `mode=IMPORT`). Omega uses two builders sharing one pool: `maxConnections` is applied per remote host, so
 * the SNI-hostile API host (cp.sodis.de) and the SNI-required edupool validation host each get K connections.
 */
@Configuration
class OmegaImportConfig(
    private val webClientConfig: WebClientConfig,
    private val importListenerContainerFactorySupport: ImportListenerContainerFactorySupport,
    private val spec: OmegaQueueProperties,
) {
    private val connectionProvider: ConnectionProvider =
        webClientConfig.importConnectionProvider("omega", spec.concurrency)

    @Bean("omegaImportWebClientBuilder")
    fun omegaImportWebClientBuilder(): WebClient.Builder =
        webClientConfig.importWebClientBuilder(connectionProvider)

    @Bean("omegaNoSniImportWebClientBuilder")
    fun omegaNoSniImportWebClientBuilder(): WebClient.Builder =
        webClientConfig.importWebClientBuilder(connectionProvider, disableSni = true)

    @Bean("omegaImportListenerContainerFactory")
    fun omegaImportListenerContainerFactory(): RabbitListenerContainerFactory<DirectMessageListenerContainer> =
        importListenerContainerFactorySupport.create(spec.effectiveImportPrefetch)
}
