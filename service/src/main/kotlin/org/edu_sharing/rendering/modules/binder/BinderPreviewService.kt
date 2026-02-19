package org.edu_sharing.rendering.modules.binder

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.dto.ObjectLink
import org.edu_sharing.rendering.core.exception.ResourceNotFoundException
import org.edu_sharing.rendering.modules.*
import org.edu_sharing.rendering.modules.binder.dto.GitDetails
import org.edu_sharing.rendering.modules.binder.git.GitService
import org.edu_sharing.rendering.modules.binder.git.GitServiceRegistry
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class BinderPreviewService(
    private val gitServiceRegistry: GitServiceRegistry,
    private val storageService: StorageService,
    private val moduleRegistry: ModuleRegistry,
    private val serviceCaller: ConverterWebServiceCaller,
    @Autowired(required = false)
    @Qualifier("jupyterConverterWebClient")
    private val jupyterConverterWebClient: WebClient?
) {
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun process(
        cacheObject: CacheObject,
        module: String
    ) {
        val gitHubToken = getGitHubApiKey(module = module, repoId = cacheObject.repoId)
        val gitService = gitServiceRegistry.getService(cacheObject.externalUrl ?: "")
            ?: throw IllegalStateException("Now GitService registered for ${cacheObject.externalUrl}. This should NOT happen at this point")
        val gitDetails = gitService.getGitDetailsFromUrl(cacheObject.externalUrl ?: "")
        val (objectLink, lastModifiedInCache) = getObjectLink(cacheObject)
        if (objectLink == null || !gitService.checkIfObjectLinkIsUpToDate(lastModifiedInCache, gitDetails, gitHubToken)) {
            createAndCachePreviewHtml(
                gitDetails = gitDetails,
                cacheObject = cacheObject,
                gitHubToken = gitHubToken,
                gitService = gitService,
            )
        }
    }

    private fun createAndCachePreviewHtml(
        gitDetails: GitDetails,
        cacheObject: CacheObject,
        gitHubToken: String,
        gitService: GitService
    ) {
        if (gitDetails.filePath.isNullOrBlank()) {
            throw IllegalArgumentException("Could not parse file path from GitHub URL")
        }
        if (jupyterConverterWebClient == null) {
            throw IllegalStateException("Illegal state: No jupyterConverterWebClient bean available. This should not happen at this point.")
        }
        val inputStream = gitService.getFile(gitDetails, gitHubToken)
        val callerArguments = ConverterWebServiceArguments(
            client = jupyterConverterWebClient,
            originalFileExtension = "ipynb",
            targetMimeType = MediaType.TEXT_HTML_VALUE,
            externalServiceMethodPath = "convert",
            cacheObject = cacheObject,
            inputStream = inputStream
        )
        serviceCaller.callConverterService(callerArguments)
    }


    private fun getObjectLink(cacheObject: CacheObject): Pair<ObjectLink?, Long> {
        cacheObject.mimeType = MediaType.TEXT_HTML_VALUE
        return try {
            storageService.getObjectLink(cacheObject)
        } catch (_: ResourceNotFoundException) {
            Pair(null, 0)
        }
    }

    private fun getGitHubApiKey(module: String, repoId: String): String {
        val module = moduleRegistry.getRenderModule<RenderModule>(module)
        if (module !is ThirdPartyModule) {
            throw IllegalArgumentException("Unexpected module type: ${module::class.java}")
        }
        val config = module.getCredentials(repoId)
        return config["githubtoken"] ?: throw IllegalArgumentException("GitHub token must be provided")
    }
}
