package org.edu_sharing.rendering.modules.jupyter

import io.mockk.every
import io.mockk.mockk
import org.bson.types.ObjectId
import org.edu_sharing.rendering.core.ErrorStrings
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.modules.ConverterCallException
import org.edu_sharing.rendering.modules.ConverterErrorStrings
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.renderingJob.SubJobHeartbeat
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

/** The sub-job's error message distinguishes a document problem from an operational one. */
class JupyterConversionFailureTest {
    private val subJobRepository = mockk<SubJobRepository>(relaxed = true)
    private val serviceCaller = mockk<ConverterWebServiceCaller>()
    private val module = mockk<JupyterRenderModule> { every { getTargetMimetype() } returns "text/html" }
    private val underTest = JupyterConversionService(
        mockk<WebClient>(), module, subJobRepository, serviceCaller, SubJobHeartbeat(subJobRepository)
    )

    private fun run(failure: Exception): SubJob {
        val subJob = mockk<SubJob>(relaxed = true)
        every { subJob.id } returns ObjectId()
        var message: String? = null
        every { subJob.errorMessage = any() } answers { message = firstArg() }
        every { subJobRepository.save(any<SubJob>()) } returns subJob
        val renderingJob = mockk<RenderingJob>(relaxed = true) {
            every { subJobs } returns mutableListOf(subJob)
            every { esObjectId } returns "node"
        }
        every { serviceCaller.callConverterService(any()) } throws failure
        underTest.process(mockk<CacheObject>(relaxed = true), renderingJob)
        every { subJob.errorMessage } returns message
        return subJob
    }

    @Test
    fun documentProblemGetsItsOwnUserMessage() {
        val failure = ConverterCallException(ConverterErrorStrings.DOCUMENT_INVALID, true, 422, "invalid", null)
        assertEquals(ConverterErrorStrings.DOCUMENT_INVALID, run(failure).errorMessage)
    }

    @Test
    fun operationalProblemGetsTheGenericMessage() {
        assertEquals(ErrorStrings.GENERIC_CONVERSION_ERROR, run(RuntimeException("connection refused")).errorMessage)
        val failure = ConverterCallException(ErrorStrings.GENERIC_CONVERSION_ERROR, false, 503, "down", null)
        assertEquals(ErrorStrings.GENERIC_CONVERSION_ERROR, run(failure).errorMessage)
    }
}
