package org.edu_sharing.rendering.modules.ddb

import tools.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.core.ErrorStrings.GENERIC_CONVERSION_ERROR
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.ThirdPartyModule
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus
import org.edu_sharing.rendering.renderingJob.repository.RenderingJobRepository
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class DdbApiService(
    private val renderingJobRepository: RenderingJobRepository,
    private val moduleRegistry: ModuleRegistry,
    private val subJobRepository: SubJobRepository
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private data class DdbRestData(
        val binaryRef: String,
        val institution: String,
        val licenseGroup: String,
        val licenseLink: String
    )

    fun process(
        remoteId: String,
        renderingJob: RenderingJob
    ) {
        log.info("Starting DDB API communication for node")
        if (renderingJob.subJobs.isEmpty()) {
            renderingJob.status = RenderingJobStatus.FAILED
            renderingJobRepository.save(renderingJob)
            return
        }
        var subJob = renderingJob.subJobs.first()
        try {
            subJob.status = SubJobStatus.PROCESSING
            subJob.message = "Calling DDB API"
            subJob = subJobRepository.save(subJob)
            subJob.additionalData = callApis(
                remoteId = remoteId,
                renderingJob = renderingJob
            )
            subJob.status = SubJobStatus.FINISHED
        } catch (exception: Exception) {
            log.error("Error while processing DDB communication: ${exception.message}", exception)
            subJob.errorMessage = GENERIC_CONVERSION_ERROR
            subJob.status = SubJobStatus.FAILED
        } finally {
            subJobRepository.save(subJob)
        }
    }

    private fun callApis(remoteId: String, renderingJob: RenderingJob): Map<String, String> {
        val apiToken = getToken(renderingJob)
        val restResponse = callRestApi(remoteId, apiToken)
        val sizes = callIiifApi(restResponse.binaryRef, apiToken)
        val additionalData = mutableMapOf(
            "linkTemplate" to "${DdbRenderModule.IIIF_API_BASE_URL}/${restResponse.binaryRef}/full/!${DdbRenderModule.Companion.WIDTH_PLACEHOLDER},${DdbRenderModule.Companion.HEIGHT_PLACEHOLDER}/0/default.jpg",
            "widthPlaceHolder" to DdbRenderModule.WIDTH_PLACEHOLDER,
            "heightPlaceHolder" to DdbRenderModule.HEIGHT_PLACEHOLDER,
            "institution" to restResponse.institution,
            "licenseLink" to restResponse.licenseLink,
            "licenseGroup" to restResponse.licenseGroup
        )
        sizes.forEachIndexed { index, size ->
            additionalData.put("size_$index", "${size.first},${size.second}")
        }
        return additionalData
    }

    private fun callRestApi(remoteId: String, apiToken: String): DdbRestData {
        val webClient = WebClient.create(DdbRenderModule.REST_API_BASE_URL)

        val response = webClient
            .get()
            .uri {
                it.path("/items/${remoteId}").queryParam("oauth_consumer_key", apiToken).build()
            }.retrieve().bodyToMono(String::class.java).block()

        response?.let {
            val objectMapper = ObjectMapper()
            val rootNode = objectMapper.readTree(it)
            val ref = rootNode.path("binaries").path("binary").path("@ref").asText("")
            val licenseLink = rootNode.path("binaries").path("binary").path("@kind").asText("")
            val licenseGroup = rootNode.path("binaries").path("binary").path("@license_group").asText("")
            if (ref.isNullOrBlank()) {
                throw Exception("DDB API did not return valid response containing remote ref")
            }
            val institution = rootNode.path("view").path("item").path("institution").path("name").asText("")
            return DdbRestData(
                binaryRef = ref,
                institution = institution,
                licenseGroup = licenseGroup,
                licenseLink = licenseLink
            )
        }
        throw Exception("Missing DDB REST API response")
    }

    private fun callIiifApi(binaryRef: String, apiToken: String): List<Pair<Int, Int>> {
        val webClient = WebClient.create(DdbRenderModule.Companion.IIIF_API_BASE_URL)
        val response = webClient
            .get()
            .uri {
                it.path("/${binaryRef}/info.json").queryParam("oauth_consumer_key", apiToken).build()
            }.retrieve().bodyToMono(String::class.java).block()

        response?.let {
            val objectMapper = ObjectMapper()
            val rootNode = objectMapper.readTree(it)
            val sizesNode = rootNode.path("sizes")
            val sizes = sizesNode.mapNotNull { sizeNode ->
                val width = sizeNode.path("width").asInt()
                val height = sizeNode.path("height").asInt()
                if (width > 0 && height > 0) Pair(width, height) else null
            }
            if (sizes.isEmpty()) {
                throw Exception("No sizes returned from DDB IIIF API. Cannot build image source")
            }
            return sizes
        }
        throw Exception("Missing DDB IIIF API response")

    }

    private fun getToken(renderingJob: RenderingJob): String {
        val module = moduleRegistry.getRenderModule<RenderModule>(renderingJob.module)
        if (module !is ThirdPartyModule) {
            throw IllegalArgumentException("Unexpected module type: ${module::class.java}")
        }
        val config = module.getCredentials(renderingJob.repoId)
        return config.getOrDefault("apiToken", "")
    }
}
