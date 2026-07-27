package org.edu_sharing.rendering.core

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node
import org.edu_sharing.rendering.core.annotation.ConditionalOnController
import org.edu_sharing.rendering.core.dto.RenderDataRequest
import org.edu_sharing.rendering.core.dto.RenderDataResponse
import org.edu_sharing.rendering.core.exception.ObjectTypeNotSupportedException
import org.edu_sharing.rendering.edusharingRepo.EduTrackingService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryPublicKeyService
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.security.NodeSessionContextRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.security.Signature
import java.util.*

@RestController
@ConditionalOnController
@RequestMapping("/public/renderdata")
class RenderController (
    private val service: RenderDataService,
    private val trackingService: EduTrackingService,
    private val repositoryPublicKeyService: RepositoryPublicKeyService,
    private val nodeSessionContextRepository: NodeSessionContextRepository,
    private val moduleRegistry: ModuleRegistry,
    @param:Value($$"${app.security.enabled}")
    private val securityEnabled: Boolean,
    @param:Value($$"#{'\${app.security.allowed-signature-algorithms:SHA256withRSA,SHA512withRSA}'.split(',')}")
    private val allowedSignatureAlgorithms: List<String>,
){

    private val log = LoggerFactory.getLogger(javaClass)

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRenderData(
        @RequestBody @Valid body: RenderDataRequest
    ): ResponseEntity<RenderDataResponse> {
        log.debug("Render data request received: nodeId=${body.nodeId}, repoId=${body.repoId}, eventType=${body.eventType}, securityEnabled=$securityEnabled")
        val decodedNode = Base64.getDecoder().decode(body.securedNode)
        val objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()
        val node = objectMapper.readValue(decodedNode.toString(Charsets.UTF_8), Node::class.java)

        // Frontend-only remote repositories (pixabay, youtube, …) are rendered client-side and
        // are intentionally not registered here, so their public keys can't be resolved. Reject
        // them before signature verification, which would otherwise fail with a confusing error.
        if (moduleRegistry.isFrontendRemoteRepository(node)) {
            log.debug("Node is from frontend-only remote repository, rendering handled in frontend only")
            throw ObjectTypeNotSupportedException()
        }

        if (securityEnabled) {
            val decodedSignature = Base64.getDecoder().decode(body.signature)
            val signatureAlgorithm = body.signatureAlgorithm
            require(signatureAlgorithm in allowedSignatureAlgorithms) {
                "Signature algorithm '$signatureAlgorithm' is not allowed"
            }
            log.debug("Verifying node signature: repoId=${body.repoId}, algorithm=$signatureAlgorithm, nodeDataLength=${decodedNode.size}")
            verifySignedNode(decodedNode, decodedSignature, body.repoId, signatureAlgorithm)
        }
        nodeSessionContextRepository.saveNode(node)
        trackingService.trackObject(objectId = node.ref.id, event = body.eventType, repoId = node.ref.repo)

        val renderModule = service.getRenderModule(body, node)
        log.debug("Dispatching to render module: ${renderModule::class.simpleName}, nodeId=${node.ref.id}")
        return ResponseEntity
            .ok()
            .body(renderModule.handle(node))
    }

    private fun verifySignedNode(nodeData: ByteArray, signature: ByteArray, repoId: String, signatureAlgorithm: String) {
        val repoPublicKey = repositoryPublicKeyService.getRepositoryKey(repoId)
        val verify = Signature.getInstance(signatureAlgorithm)
        verify.initVerify(repoPublicKey)
        verify.update(nodeData)
        val result = verify.verify(signature)
        if (!result) {
            log.debug("Signature verification failed")
            throw IllegalStateException("Signature verification failed")
        }
        log.debug("Signature verification passed: repoId=$repoId, algorithm=$signatureAlgorithm")
    }
}
