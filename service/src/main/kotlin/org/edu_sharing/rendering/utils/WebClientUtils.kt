package org.edu_sharing.rendering.utils

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import reactor.core.publisher.Mono


class WebClientUtils {

    companion object {
        private val log = LoggerFactory.getLogger(javaClass)

        fun logRequest(): ExchangeFilterFunction {
            return ExchangeFilterFunction.ofRequestProcessor { request ->
                log.info("Request: {} {}", request.method(), request.url())
                request.headers().forEach { name, values -> values.forEach { value -> log.info("$name: $value") } }
                Mono.just(request)
            }
        }

        fun logResponse(): ExchangeFilterFunction {
            return ExchangeFilterFunction.ofResponseProcessor { response ->
                log.info("Response Status: {}", response.statusCode())
                response.headers().asHttpHeaders()
                    .forEach { name, values -> values.forEach { value -> log.info("$name: $value") } }
                Mono.just(response)
            }
        }
    }
}