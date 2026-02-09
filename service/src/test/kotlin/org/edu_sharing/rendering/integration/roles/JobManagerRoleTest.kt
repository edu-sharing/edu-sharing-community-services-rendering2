package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.edu_sharing.rendering.renderingJob.queue.JobReceiver
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

@SpringBootTest(
    properties = [
        "app.roles=job-manager",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class JobManagerRoleTest(
    @param:Autowired val context: ApplicationContext
): AbstractIntegrationTest() {

    companion object {
        private val roleSpecificBeans = setOf(
            JobReceiver::class
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
