package org.edu_sharing.rendering.processing.h5p

import org.edu_sharing.rendering.config.annotation.ConditionalOnH5p
import org.edu_sharing.rendering.service.ContentTransferService
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
@ConditionalOnH5p
class H5pUploadService (
    private val contentTransferService: ContentTransferService,
    private val lumiWebClient: WebClient,
){
    fun uploadPackage(nodeId: String): String {

        return ""
        // First check if already cached by calling the respective endpoint
        // if yes return contentId
        // else upload package
    }
}