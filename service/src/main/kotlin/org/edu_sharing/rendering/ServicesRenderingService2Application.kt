package org.edu_sharing.rendering

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.runApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.stereotype.Component


@SpringBootApplication
class ServicesRenderingService2Application

fun main(args: Array<String>) {
    runApplication<ServicesRenderingService2Application>(*args)
}

@Component
@ConditionalOnProperty(name = ["debugging.env.enabled"])
class EnvironmentPropertiesPrinter(private val env: ConfigurableEnvironment) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun logApplicationProperties() {
        env.propertySources.stream()
            .filter { ps -> ps is MapPropertySource }
            .map { ps -> (ps as MapPropertySource).source.keys }
            .flatMap { obj: Collection<*> -> obj.stream() }
            .distinct()
            .sorted()
            .forEach { key -> log.info("{}={}", key, env.getProperty(key.toString())) }
    }
}
