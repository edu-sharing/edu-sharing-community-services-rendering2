package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class LumiConfig(
    @param:Value($$"${app.lumi.host}")
    private val lumiHost: String,
    @param:Value($$"${app.public.path}")
    private val publicPath: String,
    @param:Value($$"${spring.http.codecs.max-in-memory-size}")
    private val maxInMemorySize: DataSize
) {


    @Bean
    fun lumiWebClient(): WebClient {
        val lumiApiUrl = getLumiBaseUrl()
        return WebClient.builder().baseUrl(lumiApiUrl)
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySize.toBytes().toInt()) }.build()
    }

    @Bean
    fun getLumiBaseUrl(): String {
        return "${lumiHost.removeSuffix("/")}/${publicPath.removeSuffix("/")}/$H5P_BASE_PATH"
    }
}
