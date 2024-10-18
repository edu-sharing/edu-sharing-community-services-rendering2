package org.edu_sharing.rendering.roles

import org.edu_sharing.rendering.modules.h5p.H5pReceiver
import org.edu_sharing.rendering.modules.h5p.H5pUploadService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(
    properties = [
        "app.roles=h5p",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class H5pRoleTest(@Autowired val context: ApplicationContext) {
    @Test
    fun testBeanConfiguration() {
        val roleSpecificBeans = setOf(
            H5pReceiver::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            H5pUploadService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() }
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