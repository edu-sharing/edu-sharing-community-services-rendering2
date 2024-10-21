package org.edu_sharing.rendering.roles

import org.edu_sharing.rendering.modules.moodle.MoodleConfig
import org.edu_sharing.rendering.modules.moodle.MoodleReceiver
import org.edu_sharing.rendering.modules.moodle.MoodleUploadService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

@SpringBootTest(
    properties = [
        "app.roles=moodle",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class MoodleRoleTest(@Autowired val context: ApplicationContext) {
    
    companion object {
        private val roleSpecificBeans = setOf(
            MoodleReceiver::class,
            MoodleConfig::class,
            MoodleUploadService::class
        )
    }
    
    @Test
    fun testBeanConfiguration() {
        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        val expectedBeans = roleSpecificBeans
            .map {ClassUtils.getShortNameAsProperty(it.java)} union SharedBeans.all

        assert(expectedBeans == allEdusharingBeans)
    }
}