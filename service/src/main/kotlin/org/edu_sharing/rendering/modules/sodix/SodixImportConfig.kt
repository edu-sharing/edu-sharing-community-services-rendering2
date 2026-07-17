package org.edu_sharing.rendering.modules.sodix

import org.edu_sharing.rendering.config.WebClientConfig
import org.edu_sharing.rendering.renderingJob.queue.ImportConcurrencyProperties
import org.edu_sharing.rendering.renderingJob.queue.ImportListenerContainerFactorySupport
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.resources.ConnectionProvider

/**
 * Per-pod import concurrency for the Sodix queue, bound from `app.module.sodix.import.*`.
 * See [ImportConcurrencyProperties] for the concurrency/prefetch contract.
 */
@Component
@ConfigurationProperties("app.module.sodix.import")
class SodixImportProperties : ImportConcurrencyProperties()

/**
 * Module-local wiring of Sodix's dedicated import HTTP pool and its decoupled listener container, kept
 * out of the central config so each import queue is tuned independently ([SodixImportProperties]). Sodix
 * talks to a single SNI-enabled host, so one pool + one builder suffices.
 */
@Configuration
class SodixImportConfig(
    private val webClientConfig: WebClientConfig,
    private val importListenerContainerFactorySupport: ImportListenerContainerFactorySupport,
    private val properties: SodixImportProperties,
) {
    private val connectionProvider: ConnectionProvider =
        webClientConfig.importConnectionProvider("sodix", properties.concurrency)

    @Bean("sodixImportWebClientBuilder")
    fun sodixImportWebClientBuilder(): WebClient.Builder =
        webClientConfig.importWebClientBuilder(connectionProvider)

    @Bean("sodixImportListenerContainerFactory")
    fun sodixImportListenerContainerFactory(): RabbitListenerContainerFactory<DirectMessageListenerContainer> =
        importListenerContainerFactorySupport.create(properties.effectivePrefetch)
}
