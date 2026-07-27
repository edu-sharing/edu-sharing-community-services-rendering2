package org.edu_sharing.rendering.edusharingRepo.cors

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.generated.repository.backend.services.rest.client.ApiException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.net.ConnectException

/**
 * Verifies the `@Retryable` annotation actually engages through the AOP proxy — the classification
 * unit tests bypass the proxy, so this guards against a silent no-op (which would let the first
 * transient failure abort startup, defeating the retry). Exercises a single real retry cycle.
 */
@SpringJUnitConfig
class RegistrationCorsSyncRetryWiringTest {

    @Configuration
    @EnableResilientMethods
    class Config {
        @Bean
        fun corsSyncService(): CorsSyncService = mockk()

        @Bean
        fun retrier(corsSyncService: CorsSyncService) = RegistrationCorsSyncRetrier(corsSyncService)
    }

    @Autowired
    lateinit var retrier: RegistrationCorsSyncRetrier

    @Autowired
    lateinit var corsSyncService: CorsSyncService

    @Test
    fun `retrier bean is proxied so @Retryable can intercept`() {
        assertTrue(AopUtils.isAopProxy(retrier), "retrier must be an AOP proxy for @Retryable to engage")
    }

    @Test
    fun `retries a transient failure and then succeeds`() {
        var calls = 0
        every { corsSyncService.syncAllowedOriginsWithRepository("repo1") } answers {
            calls++
            if (calls == 1) throw ApiException(ConnectException("connection refused")) else true
        }

        retrier.syncWithRetry("repo1")

        verify(exactly = 2) { corsSyncService.syncAllowedOriginsWithRepository("repo1") }
    }
}
