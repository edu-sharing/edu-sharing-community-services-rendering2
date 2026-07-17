package org.edu_sharing.rendering.modules.ddb

import org.edu_sharing.rendering.config.WebClientConfig
import org.edu_sharing.rendering.renderingJob.queue.ImportListenerContainerFactorySupport
import org.edu_sharing.rendering.renderingJob.queue.QueueProperties
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.resources.ConnectionProvider

/**
 * Module-local wiring of DDB's dedicated import HTTP pool and its decoupled listener container. Sized from
 * the queue's own [org.edu_sharing.rendering.renderingJob.queue.QueueSpec] (`app.queue.ddb.*`, `mode=IMPORT`;
 * URL config lives in [DdbApiProperties]). `maxConnections` is applied per remote host, so DDB's REST and
 * IIIF hosts each get K connections from the one pool.
 */
@Configuration
class DdbImportConfig(
    private val webClientConfig: WebClientConfig,
    private val importListenerContainerFactorySupport: ImportListenerContainerFactorySupport,
    queueProperties: QueueProperties,
) {
    private val spec = queueProperties.ddb
    private val connectionProvider: ConnectionProvider =
        webClientConfig.importConnectionProvider("ddb", spec.concurrency)

    @Bean("ddbImportWebClientBuilder")
    fun ddbImportWebClientBuilder(): WebClient.Builder =
        webClientConfig.importWebClientBuilder(connectionProvider)

    @Bean("ddbImportListenerContainerFactory")
    fun ddbImportListenerContainerFactory(): RabbitListenerContainerFactory<DirectMessageListenerContainer> =
        importListenerContainerFactorySupport.create(spec.effectiveImportPrefetch)
}
