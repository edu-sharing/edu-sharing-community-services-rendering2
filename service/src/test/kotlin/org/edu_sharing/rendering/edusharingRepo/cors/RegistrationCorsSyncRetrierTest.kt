package org.edu_sharing.rendering.edusharingRepo.cors

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.edu_sharing.generated.repository.backend.services.rest.client.ApiException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RegistrationCorsSyncRetrierTest {

    private val corsSyncService: CorsSyncService = mockk()
    private val underTest = RegistrationCorsSyncRetrier(corsSyncService)

    @Test
    fun `replication lag 400 is treated as retryable`() {
        every { corsSyncService.syncAllowedOriginsWithRepository("repo1") } throws
            ApiException(
                "Bad Request",
                400,
                null,
                """{"error":"400","message":"X-Edu-App-Id header was sent but the app/tool rendering2-staging-rlp was not found in the list of registered apps"}""",
            )

        assertThrows(RegistrationReplicationPendingException::class.java) {
            underTest.syncWithRetry("repo1")
        }
    }

    @Test
    fun `connection failure (code 0) is treated as retryable`() {
        every { corsSyncService.syncAllowedOriginsWithRepository("repo1") } throws
            ApiException(java.net.ConnectException("connection refused"))

        assertThrows(RegistrationReplicationPendingException::class.java) {
            underTest.syncWithRetry("repo1")
        }
    }

    @Test
    fun `server error (5xx) is treated as retryable`() {
        every { corsSyncService.syncAllowedOriginsWithRepository("repo1") } throws
            ApiException("Bad Gateway", 502, null, "")

        assertThrows(RegistrationReplicationPendingException::class.java) {
            underTest.syncWithRetry("repo1")
        }
    }

    @Test
    fun `auth failure (401) fails fast as fatal`() {
        every { corsSyncService.syncAllowedOriginsWithRepository("repo1") } throws
            ApiException("Unauthorized", 401, null, "")

        assertThrows(RegistrationSyncFatalException::class.java) {
            underTest.syncWithRetry("repo1")
        }
    }

    @Test
    fun `forbidden (403) fails fast as fatal`() {
        every { corsSyncService.syncAllowedOriginsWithRepository("repo1") } throws
            ApiException("Forbidden", 403, null, "")

        assertThrows(RegistrationSyncFatalException::class.java) {
            underTest.syncWithRetry("repo1")
        }
    }

    @Test
    fun `generic 400 without replication marker fails fast as fatal`() {
        every { corsSyncService.syncAllowedOriginsWithRepository("repo1") } throws
            ApiException("Bad Request", 400, null, """{"message":"something else"}""")

        assertThrows(RegistrationSyncFatalException::class.java) {
            underTest.syncWithRetry("repo1")
        }
    }

    @Test
    fun `successful sync returns normally`() {
        every { corsSyncService.syncAllowedOriginsWithRepository("repo1") } returns true

        underTest.syncWithRetry("repo1")

        verify { corsSyncService.syncAllowedOriginsWithRepository("repo1") }
    }
}
