package org.edu_sharing.rendering.roles

import org.edu_sharing.rendering.asset.AssetController
import org.edu_sharing.rendering.asset.AssetService
import org.edu_sharing.rendering.core.RenderController
import org.edu_sharing.rendering.core.RenderDataService
import org.edu_sharing.rendering.edusharingRepo.MetadataController
import org.edu_sharing.rendering.modules.ModuleInfoController
import org.edu_sharing.rendering.modules.h5p.lumi.LumiProxyController
import org.edu_sharing.rendering.modules.h5p.lumi.LumiProxyService
import org.edu_sharing.rendering.renderingJob.JobInfoController
import org.edu_sharing.rendering.renderingJob.JobInfoService
import org.junit.jupiter.api.Test
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
class ControllerRoleTest(@Autowired val context: ApplicationContext) {

    @Test
    fun testBeanConfiguration() {
        val roleSpecificBeans = setOf(
            AssetController::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            AssetService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            RenderController::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            RenderDataService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ModuleInfoController::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            LumiProxyController::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            LumiProxyService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            JobInfoController::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            JobInfoService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MetadataController::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
        )

        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }

        roleSpecificBeans.forEach {
            assert(allEdusharingBeans.contains(it))
        }

        val remainingBeans = allEdusharingBeans.filter {
            ! roleSpecificBeans.contains(it) && ! SharedBeans.set.contains(it) && ! SharedBeans.functionalBeans.contains(it)
        }

       assert(remainingBeans.isEmpty())
    }
}