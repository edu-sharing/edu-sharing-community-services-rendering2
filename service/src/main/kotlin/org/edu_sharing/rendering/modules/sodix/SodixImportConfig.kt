package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.config.WebClientConfig
import org.edu_sharing.rendering.renderingJob.queue.ImportListenerContainerFactorySupport
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.resources.ConnectionProvider

/**
 * Module-local wiring of Sodix's dedicated import HTTP pool and its decoupled listener container. Sized
 * from the queue's own [org.edu_sharing.rendering.renderingJob.queue.QueueSpec] (`app.queue.sodix.*`,
 * `mode=IMPORT`): `concurrency` (K) sizes the HTTP pool, `effectiveImportPrefetch` the broker prefetch —
 * so each import queue is tuned independently. Sodix talks to a single SNI-enabled host, so one pool +
 * one builder suffices.
 */
@Configuration
class SodixImportConfig(
    private val webClientConfig: WebClientConfig,
    private val importListenerContainerFactorySupport: ImportListenerContainerFactorySupport,
    private val spec: SodixQueueProperties,
) {
    private val connectionProvider: ConnectionProvider =
        webClientConfig.importConnectionProvider("sodix", spec.concurrency)

    @Bean("sodixImportWebClientBuilder")
    fun sodixImportWebClientBuilder(): WebClient.Builder =
        webClientConfig.importWebClientBuilder(connectionProvider)

    @Bean("sodixImportListenerContainerFactory")
    fun sodixImportListenerContainerFactory(): RabbitListenerContainerFactory<DirectMessageListenerContainer> =
        importListenerContainerFactorySupport.create(spec.effectiveImportPrefetch)
}
