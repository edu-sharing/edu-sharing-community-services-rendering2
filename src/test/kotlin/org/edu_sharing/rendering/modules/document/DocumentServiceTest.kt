package org.edu_sharing.rendering.modules.document

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.ObjectLink
import org.edu_sharing.rendering.modules.MainJobCreationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class DocumentServiceTest (
    @Mock
    private val storageService: StorageService,
    @Mock
    private val mainJobCreationService: MainJobCreationService
) {
    private lateinit var service: DocumentService

    @BeforeEach
    fun setUp() {
        service = DocumentService(storageImplementation = storageService, mainJobCreationService = mainJobCreationService)
    }

    @Test
    fun getObjectLinks() {
    }

    @Test
    fun retrieveOrCreateJob() {
        Mockito.`when`(storageService.getObjectLink(ArgumentMatchers.anyString())).thenReturn(ObjectLink(link = "test"))
    }
}