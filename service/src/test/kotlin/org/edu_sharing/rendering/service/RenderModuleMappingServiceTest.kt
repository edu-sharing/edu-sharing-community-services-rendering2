package org.edu_sharing.rendering.service

import io.mockk.junit5.MockKExtension
import org.edu_sharing.rendering.core.RenderModuleMappingService
import org.edu_sharing.rendering.modules.RenderModules
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RenderModuleMappingServiceTest {

    private val officeMimeTypes = listOf(
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.presentation"
    )

    private val spreadsheetMimeTypes = listOf(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.oasis.opendocument.spreadsheet"
    )

    private val underTestWithHtml = RenderModuleMappingService(true)
    private val underTestWithoutHtml = RenderModuleMappingService(false)

    @Test
    fun testGetModuleReturnsMoodleModuleForMoodleFile() {
        assert(underTestWithHtml.getModule("file-moodle", "") == RenderModules.MOODLE)
    }

    @Test
    fun testGetModuleReturnsScormModuleForScormFile() {
        assert(underTestWithHtml.getModule("file-scorm", "") == RenderModules.SCORM)
    }

    @Test
    fun testGetModuleReturnsEduHtmlModuleForEduHtmlFile() {
        assert(underTestWithHtml.getModule("file-eduhtml", "") == RenderModules.EDUHTML)
    }

    @Test
    fun testGetModuleReturnsH5pModuleForH5pFile() {
        assert(underTestWithHtml.getModule("file-h5p", "") == RenderModules.H5P)
    }

    @Test
    fun testGetModuleReturnsAudioModuleForAudioMimeType() {
        assert(underTestWithHtml.getModule("", "audio/mpeg") == RenderModules.AUDIO)
    }

    @Test
    fun testGetModuleReturnsVideoModuleForVideoMimeType() {
        assert(underTestWithHtml.getModule("", "video/mp4") == RenderModules.VIDEO)
    }

    @Test
    fun testGetModuleReturnsImageModuleForImageMimeType() {
        assert(underTestWithHtml.getModule("", "image/jpeg") == RenderModules.IMAGE)
    }

    @Test
    fun testGetModuleThrowsExceptionIfMimeTypeCannotBeMapped() {
        assertThrows<ObjectTypeNotSupportedException> { underTestWithoutHtml.getModule("", "some/mime") }
    }

    @Test
    fun testGetModuleReturnsPdfModuleForPdfMimeType() {
        assert(underTestWithHtml.getModule("", "application/pdf") == RenderModules.PDF)
    }

    @Test
    fun testGetModuleReturnsOfficeModuleForNonSpreadsheetOfficeFiles() {
        officeMimeTypes.forEach {
            assert( underTestWithoutHtml.getModule("", it) == RenderModules.DOCUMENT)
        }
    }

    @Test
    fun testGetModuleReturnsSpreadsheetModuleForSpreadsheetFilesIfOptionSet() {
        spreadsheetMimeTypes.forEach {
            assert( underTestWithHtml.getModule("", it) == RenderModules.SPREADSHEET )
        }
    }

    @Test
    fun testGetModuleReturnsDocumentModuleForSpreadsheetFilesIfOptionNotSet() {
        spreadsheetMimeTypes.forEach {
            assert( underTestWithoutHtml.getModule("", it) == RenderModules.DOCUMENT )
        }
    }

    @Test
    fun testGetModuleThrowsExceptionIfApplicationMimetypeCannotBeMapped() {
        assertThrows<ObjectTypeNotSupportedException> { underTestWithoutHtml.getModule("", "application/mime") }
    }
}