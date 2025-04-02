package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class LumiConfig() {
    @Value("\${app.lumi.host}")
    lateinit var lumiHost: String

    @Value("\${app.public.path}")
    lateinit var publicPath: String

    @Bean
    fun lumiWebClient(): WebClient {
        val lumiApiUrl = getLumiBaseUrl()
        return WebClient.builder().baseUrl(lumiApiUrl).build()
    }

    @Bean
    fun getLumiBaseUrl(): String {
        return "${lumiHost.removeSuffix("/")}/${publicPath.removeSuffix("/")}/$H5P_BASE_PATH"
    }
}
