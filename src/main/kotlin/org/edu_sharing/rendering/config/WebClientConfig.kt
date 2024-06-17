package org.edu_sharing.rendering.config

import org.edu_sharing.rendering.exception.WebClientClientException
import org.edu_sharing.rendering.exception.WebClientNotFoundException
import org.edu_sharing.rendering.exception.WebClientServerException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import reactor.core.publisher.Mono
import java.util.function.Function


@Configuration
class WebClientConfig {

    @Bean
    fun errorHandler(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofResponseProcessor { clientResponse: ClientResponse ->
            if (clientResponse.statusCode().isSameCodeAs(HttpStatusCode.valueOf(200))) {
                return@ofResponseProcessor clientResponse.bodyToMono<String>(String::class.java)
                    .flatMap<ClientResponse>(Function<String, Mono<out ClientResponse>> { errorBody: String? ->
                        Mono.error(
                            WebClientNotFoundException(errorBody ?: "")
                        )
                    })
            } else if (clientResponse.statusCode().is5xxServerError) {
                return@ofResponseProcessor clientResponse.bodyToMono<String>(String::class.java)
                    .flatMap<ClientResponse>(Function<String, Mono<out ClientResponse>> { errorBody: String? ->
                        Mono.error(
                            WebClientServerException(errorBody ?: "")
                        )
                    })
            } else if (clientResponse.statusCode().is4xxClientError) {
                return@ofResponseProcessor clientResponse.bodyToMono<String>(String::class.java)
                    .flatMap<ClientResponse>(Function<String, Mono<out ClientResponse>> { errorBody: String? ->
                        Mono.error(
                            WebClientClientException(errorBody ?: "")
                        )
                    })
            } else {
                return@ofResponseProcessor Mono.just<ClientResponse>(clientResponse)
            }
        }
    }

}