package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.asset.AssetController
import org.edu_sharing.rendering.asset.AssetService
import org.edu_sharing.rendering.core.RenderController
import org.edu_sharing.rendering.core.RenderDataService
import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.edu_sharing.rendering.modules.ModuleInfoController
import org.edu_sharing.rendering.modules.h5p.lumi.LumiProxyController
import org.edu_sharing.rendering.modules.h5p.lumi.LumiProxyService
import org.edu_sharing.rendering.renderingJob.JobInfoController
import org.edu_sharing.rendering.renderingJob.JobInfoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(
    properties = [
        "app.roles=controller",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class ControllerRoleTest(@Autowired val context: ApplicationContext): AbstractIntegrationTest() {

    companion object {
        private val roleSpecificBeans = setOf(
            AssetController::class,
            AssetService::class,
            RenderController::class,
            RenderDataService::class,
            ModuleInfoController::class,
            LumiProxyController::class,
            LumiProxyService::class,
            JobInfoController::class,
            JobInfoService::class,
        )
    }

    /*
    @Test
    fun testBeanConfiguration() {
        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        val expectedBeans = roleSpecificBeans
            .map { ClassUtils.getShortNameAsProperty(it.java) } union SharedBeans.all

        assert(expectedBeans == allEdusharingBeans)
    }

     */
}