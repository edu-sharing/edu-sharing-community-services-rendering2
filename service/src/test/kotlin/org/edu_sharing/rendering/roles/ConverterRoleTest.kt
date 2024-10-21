package org.edu_sharing.rendering.roles

import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.modules.av.AvConfig
import org.edu_sharing.rendering.modules.av.AvConversionListener
import org.edu_sharing.rendering.modules.av.AvFileHelperFactory
import org.edu_sharing.rendering.modules.av.AvReceiver
import org.edu_sharing.rendering.modules.av.audio.AudioConversionService
import org.edu_sharing.rendering.modules.av.video.VideoConversionService
import org.edu_sharing.rendering.modules.document.DocumentConversionService
import org.edu_sharing.rendering.modules.document.DocumentConverterConfig
import org.edu_sharing.rendering.modules.document.DocumentReceiver
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlConversionService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlReceiver
import org.edu_sharing.rendering.modules.image.ImageConversionService
import org.edu_sharing.rendering.modules.image.ImageReceiver
import org.edu_sharing.rendering.modules.jupyter.JupyterConversionService
import org.edu_sharing.rendering.modules.jupyter.JupyterConverterConfig
import org.edu_sharing.rendering.modules.jupyter.JupyterReceiver
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

@SpringBootTest(
    properties = [
        "app.roles=converter",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class ConverterRoleTest(@Autowired val context: ApplicationContext) {
    
    companion object {
        private val roleSpecificClassBeans = setOf(
            AvConversionListener::class,
            AvConfig::class,
            AvFileHelperFactory::class,
            AvReceiver::class,
            AudioConversionService::class,
            ConverterWebServiceCaller::class,
            VideoConversionService::class,
            DocumentConversionService::class,
            DocumentConverterConfig::class,
            DocumentReceiver::class,
            EduHtmlConversionService::class,
            EduHtmlReceiver::class,
            ImageConversionService::class,
            ImageReceiver::class,
            JupyterReceiver::class,
            JupyterConversionService::class,
            JupyterConverterConfig::class,
        )

        private val roleSpecificFunctionalBeans = setOf(
            "createAvFileHelper"
        )
    }

    @Test
    fun testBeanConfiguration() {
        val roleSpecificBeans = roleSpecificClassBeans
            .map {ClassUtils.getShortNameAsProperty(it.java)} union roleSpecificFunctionalBeans

        val expectedBeans = roleSpecificBeans union SharedBeans.all

        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        assert(expectedBeans == allEdusharingBeans)
    }
}