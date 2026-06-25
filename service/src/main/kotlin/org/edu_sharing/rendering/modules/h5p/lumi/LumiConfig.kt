package org.edu_sharing.rendering.modules.h5p.lumi

import org.edu_sharing.rendering.config.H5P_BASE_PATH
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class LumiConfig(
    @param:Value($$"${app.lumi.host}")
    private val lumiHost: String,
    @param:Value($$"${app.public.path}")
    private val publicPath: String,
) {


    @Bean
    fun lumiWebClient(
        @Qualifier("longRunningWebClientBuilder") webClientBuilder: WebClient.Builder
    ): WebClient {
        // Buffer limit is inherited from the shared builder (spring.http.codecs.max-in-memory-size).
        val lumiApiUrl = getLumiBaseUrl()
        return webClientBuilder.clone().baseUrl(lumiApiUrl).build()
    }

    @Bean
    fun getLumiBaseUrl(): String {
        return "${lumiHost.removeSuffix("/")}/${publicPath.removeSuffix("/")}/$H5P_BASE_PATH"
    }
}
