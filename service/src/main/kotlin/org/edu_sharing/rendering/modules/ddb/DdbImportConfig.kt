package org.edu_sharing.rendering.modules.ddb

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
 * Per-pod import concurrency for the DDB queue, bound from `app.module.ddb.import.*` (sibling of the
 * URL config in [DdbApiProperties]). See [ImportConcurrencyProperties] for the concurrency/prefetch contract.
 */
@Component
@ConfigurationProperties("app.module.ddb.import")
class DdbImportProperties : ImportConcurrencyProperties()

/**
 * Module-local wiring of DDB's dedicated import HTTP pool and its decoupled listener container, kept out
 * of the central config so each import queue is tuned independently ([DdbImportProperties]). `maxConnections`
 * is applied per remote host, so DDB's REST and IIIF hosts each get K connections from the one pool.
 */
@Configuration
class DdbImportConfig(
    private val webClientConfig: WebClientConfig,
    private val importListenerContainerFactorySupport: ImportListenerContainerFactorySupport,
    private val properties: DdbImportProperties,
) {
    private val connectionProvider: ConnectionProvider =
        webClientConfig.importConnectionProvider("ddb", properties.concurrency)

    @Bean("ddbImportWebClientBuilder")
    fun ddbImportWebClientBuilder(): WebClient.Builder =
        webClientConfig.importWebClientBuilder(connectionProvider)

    @Bean("ddbImportListenerContainerFactory")
    fun ddbImportListenerContainerFactory(): RabbitListenerContainerFactory<DirectMessageListenerContainer> =
        importListenerContainerFactorySupport.create(properties.effectivePrefetch)
}
