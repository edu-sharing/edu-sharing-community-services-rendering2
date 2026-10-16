package org.edu_sharing.rendering.edusharingRepo.services

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.edu_sharing.rendering.config.AppInfo
import org.edu_sharing.rendering.edusharingRepo.RestClientProvider
import org.edu_sharing.rendering.edusharingRepo.dto.RegisterRepositoryRequest
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.storage.StorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import java.io.File
import java.util.Optional

/**
 * H9: with `force`, existing registrations must only be touched after all remote calls succeeded,
 * so a failing remote query cannot destroy a working registration.
 */
class RepositoryRegistrationServiceForceOrderTest {

    private val storage = mockk<RepositoryRegistrationStorageService>()
    private val storageService = mockk<StorageService>()
    private val restClientProvider = mockk<RestClientProvider>()
    private val metadataService = mockk<MetadataService>()
    private lateinit var server: MockWebServer
    private lateinit var underTest: RepositoryRegistrationService

    private val metadataXml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
<entry key="appid">newRepo</entry>
<entry key="public_key">PK</entry>
<entry key="clientprotocol">http</entry>
<entry key="domain">repo.example</entry>
<entry key="clientport">80</entry>
</properties>"""

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        every { storageService.isStoringByRepoId() } returns false
        every { storage.getRegistrationCount() } returns 1
        every { storage.getRegistrations() } returns listOf(RepositoryRegistration(id = "1", repoId = "oldRepo", url = "http://old", publicKey = "x"))
        every { storage.removeRegistration(any()) } returns Optional.empty()
        every { storage.getRegistrationByRepoIdFresh(any()) } returns Optional.empty()
        every { storage.storeRegistration(any()) } answers { firstArg() }
        underTest = RepositoryRegistrationService(
            storage, storageService, mockk<AppInfo>(relaxed = true), mockk<ModuleRegistry>(),
            metadataService, restClientProvider, WebClient.builder()
        )
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun request() = RegisterRepositoryRequest(url = server.url("/").toString().trimEnd('/'), username = "a", password = "b")

    private fun aboutWorks() {
        every { restClientProvider.getAboutApiClient(any()).about().defaultSignatureAlgorithm } returns "SHA256withRSA"
        every { restClientProvider.getAdminV1Client(any(), any(), any()).addApplication(any()) } returns mockk(relaxed = true)
        val file = File.createTempFile("meta", ".xml")
        every { metadataService.generateMetadataFile(useInternal = any()) } returns mockk(relaxed = true) {
            every { this@mockk.file } returns file
        }
    }

    @Test
    fun `failing metadata call leaves existing registrations untouched`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows<Exception> { underTest.registerWithRepository(request(), force = true, useInternal = true) }

        verify(exactly = 0) { storage.removeRegistration(any()) }
        verify(exactly = 0) { storage.storeRegistration(any()) }
        verify(exactly = 0) { storage.clearRegistrations() }
    }

    @Test
    fun `failing about call leaves existing registrations untouched`() {
        server.enqueue(MockResponse().setBody(metadataXml))
        every { restClientProvider.getAboutApiClient(any()) } throws IllegalStateException("about unreachable")

        assertThrows<IllegalStateException> { underTest.registerWithRepository(request(), force = true, useInternal = true) }

        verify(exactly = 0) { storage.removeRegistration(any()) }
        verify(exactly = 0) { storage.storeRegistration(any()) }
    }

    @Test
    fun `old registration is removed only after the remote calls and then the new one is stored`() {
        server.enqueue(MockResponse().setBody(metadataXml))
        aboutWorks()

        val result = underTest.registerWithRepository(request(), force = true, useInternal = true)

        assertEquals("newRepo", result.repoId)
        verifyOrder {
            restClientProvider.getAboutApiClient(any())
            storage.removeRegistration("oldRepo")
            storage.storeRegistration(match { it.repoId == "newRepo" })
        }
        verify(exactly = 0) { storage.clearRegistrations() }
    }

    @Test
    fun `same repoId is upserted in place without removing it`() {
        server.enqueue(MockResponse().setBody(metadataXml))
        aboutWorks()
        val existing = RepositoryRegistration(id = "42", repoId = "newRepo", url = "http://old", publicKey = "x")
        every { storage.getRegistrations() } returns listOf(existing)
        every { storage.getRegistrationByRepoIdFresh("newRepo") } returns Optional.of(existing)

        underTest.registerWithRepository(request(), force = true, useInternal = true)

        verify(exactly = 0) { storage.removeRegistration(any()) }
        verify { storage.storeRegistration(match { it.id == "42" }) }
    }

    @Test
    fun `without force a second repository is rejected before any remote call`() {
        assertThrows<IllegalArgumentException> { underTest.registerWithRepository(request(), force = false) }

        assertEquals(0, server.requestCount)
    }
}
