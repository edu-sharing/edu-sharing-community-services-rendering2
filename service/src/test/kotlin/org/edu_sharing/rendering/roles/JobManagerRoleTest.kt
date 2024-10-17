package org.edu_sharing.rendering.roles

import org.edu_sharing.rendering.renderingJob.queue.JobReceiver
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import kotlin.text.substringAfterLast

@SpringBootTest(
    properties = [
        "app.roles=job-manager",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class JobManagerRoleTest(@Autowired val context: ApplicationContext) {
    @Test
    fun testBeanConfiguration() {
        val roleSpecificBeans = setOf(
            JobReceiver::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() }
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