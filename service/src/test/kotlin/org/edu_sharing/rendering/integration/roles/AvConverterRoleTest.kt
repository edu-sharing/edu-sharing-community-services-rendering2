package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.edu_sharing.rendering.modules.av.AvConfig
import org.edu_sharing.rendering.modules.av.AvConversionListener
import org.edu_sharing.rendering.modules.av.AvFileHelperFactory
import org.edu_sharing.rendering.modules.av.AvReceiver
import org.edu_sharing.rendering.modules.av.audio.AudioConversionService
import org.edu_sharing.rendering.modules.av.video.VideoConversionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(
    properties = [
        "app.roles=avconverter",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class AvConverterRoleTest(@Autowired val context: ApplicationContext): AbstractIntegrationTest() {
    companion object {
        private val roleSpecificClassBeans = setOf(
            AvConfig::class,
            AvConversionListener::class,
            AvFileHelperFactory::class,
            AvReceiver::class,
            AudioConversionService::class,
            VideoConversionService::class
        )

        private val roleSpecificFunctionalBeans = setOf(
            "createAvFileHelper"
        )
    }

    /*
    @Test
    fun testBeanConfiguration() {

        val roleSpecificBeans = roleSpecificClassBeans
            .map {ClassUtils.getShortNameAsProperty(it.java)} union roleSpecificFunctionalBeans

        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        val expectedBeans = roleSpecificBeans union SharedBeans.all

        assert(expectedBeans == allEdusharingBeans)
    }

     */
}