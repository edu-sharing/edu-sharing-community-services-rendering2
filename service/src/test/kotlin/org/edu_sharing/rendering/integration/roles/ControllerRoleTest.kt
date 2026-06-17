package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.asset.AssetController
import org.edu_sharing.rendering.asset.AssetService
import org.edu_sharing.rendering.core.RenderController
import org.edu_sharing.rendering.core.RenderDataService
import org.edu_sharing.rendering.core.SessionController
import org.edu_sharing.rendering.edusharingRepo.EduTrackingController
import org.edu_sharing.rendering.edusharingRepo.EduTrackingService
import org.edu_sharing.rendering.edusharingRepo.SessionTicketRepository
import org.edu_sharing.rendering.edusharingRepo.UserBasedRestClientProvider
import org.edu_sharing.rendering.edusharingRepo.cors.CorsAllowedOriginsReceiver
import org.edu_sharing.rendering.edusharingRepo.cors.CorsSyncService
import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.edu_sharing.rendering.modules.ModuleInfoController
import org.edu_sharing.rendering.modules.h5p.lumi.LumiProxyController
import org.edu_sharing.rendering.modules.h5p.lumi.LumiProxyService
import org.edu_sharing.rendering.renderingJob.JobInfoController
import org.edu_sharing.rendering.renderingJob.JobInfoService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

@SpringBootTest(
    properties = [
        "app.roles=controller",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class ControllerRoleTest(
    @param:Autowired val context: ApplicationContext
): AbstractIntegrationTest() {

    companion object {
        private val roleSpecificBeans = setOf(
            AssetController::class,
            AssetService::class,
            CorsAllowedOriginsReceiver::class,
            CorsSyncService::class,
            EduTrackingController::class,
            EduTrackingService::class,
            SessionTicketRepository::class,
            UserBasedRestClientProvider::class,
            RenderController::class,
            RenderDataService::class,
            SessionController::class,
            ModuleInfoController::class,
            LumiProxyController::class,
            LumiProxyService::class,
            JobInfoController::class,
            JobInfoService::class,
        )
    }

    @Test
    fun testBeanConfiguration() {
        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        val expectedBeans = roleSpecificBeans
            .map { ClassUtils.getShortNameAsProperty(it.java) } union SharedBeans.all

        val beanMatch = allEdusharingBeans == expectedBeans
        if (! beanMatch) {
            val missingBeans = expectedBeans subtract allEdusharingBeans
            val unexpectedBeans = allEdusharingBeans subtract expectedBeans

            println("Missing beans (expected but not found): $missingBeans")
            println("Unexpected beans (found but not expected): $unexpectedBeans")
        }

        assert(beanMatch)
    }
}
