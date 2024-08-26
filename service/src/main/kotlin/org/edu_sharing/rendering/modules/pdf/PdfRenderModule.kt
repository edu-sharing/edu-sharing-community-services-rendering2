package org.edu_sharing.rendering.modules.pdf

import org.edu_sharing.rendering.dto.RenderDataRequest
import org.edu_sharing.rendering.dto.RenderDataResponse
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.dto.mapper.Mapper
import org.edu_sharing.rendering.modules.DirectStorageHandler
import org.edu_sharing.rendering.modules.RenderModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PdfRenderModule(
    @Value("\${app.session.pdf.nodePermissionExpirationTime}")
    private val nodePermissionExpirationTime: Long?,
    private val directStorageHandler: DirectStorageHandler,
    private val mapper: Mapper
) : RenderModule {
    override fun module() = RenderModules.PDF

    override fun handle(request: RenderDataRequest): RenderDataResponse {
        val cacheObject = mapper.renderDataRequestToCacheObject(request)
        val links = directStorageHandler.getObjectLinkList(cacheObject)
        return RenderDataResponse(
            objectLinks = links,
            module = module()
        )
    }

    override fun getNodePermissionExpirationTime() = nodePermissionExpirationTime
}
