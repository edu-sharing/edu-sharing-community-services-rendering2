package org.edu_sharing.rendering.roles

import org.edu_sharing.rendering.modules.moodle.MoodleConfig
import org.edu_sharing.rendering.modules.moodle.MoodleReceiver
import org.edu_sharing.rendering.modules.moodle.MoodleUploadService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(
    properties = [
        "app.roles=moodle",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class MoodleRoleTest(@Autowired val context: ApplicationContext) {
    @Test
    fun testBeanConfiguration() {
        val roleSpecificBeans = setOf(
            MoodleReceiver::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MoodleConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MoodleUploadService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() }
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