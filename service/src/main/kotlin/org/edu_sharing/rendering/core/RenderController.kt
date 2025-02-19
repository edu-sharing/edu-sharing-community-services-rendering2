package org.edu_sharing.rendering.core

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryPublicKeyService
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Signature
import java.util.*

@RestController
@ConditionalOnController
@RequestMapping("/public/renderdata")
class RenderController (
    private val service: RenderDataService,
    private val repositoryPublicKeyService: RepositoryPublicKeyService,
    private val nodeSessionContextRepository: NodeSessionContextRepository,
){
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRenderData(
        @RequestBody @Valid body: RenderDataRequest
    ): ResponseEntity<RenderDataResponse> {
        val decodedNode = Base64.getDecoder().decode(body.securedNode)
        val decodedSignature = Base64.getDecoder().decode(body.signature)
        verifySignedNode(decodedNode, decodedSignature, body.repoId)
        val node = Node.fromJson(decodedNode.toString(Charsets.UTF_8))
        nodeSessionContextRepository.saveNode(node)

        return ResponseEntity
            .ok()
            .body(service.getRenderModule(body, node).handle(node))
    }

    private fun verifySignedNode(nodeData: ByteArray, signature: ByteArray, repoId: String) {
        val repoPublicKey = repositoryPublicKeyService.getRepositoryKey(repoId)
        val verify = Signature.getInstance("SHA1withRSA")
        verify.initVerify(repoPublicKey)
        verify.update(nodeData)
        val result = verify.verify(signature)
        if (!result) {
            throw IllegalStateException("Signature verification failed")
        }
    }
}
