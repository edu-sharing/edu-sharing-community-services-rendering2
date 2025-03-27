package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.integration.AbstractIntegrationTest
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.modules.document.DocumentConversionService
import org.edu_sharing.rendering.modules.document.DocumentConverterConfig
import org.edu_sharing.rendering.modules.document.DocumentReceiver
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlConversionService
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlReceiver
import org.edu_sharing.rendering.modules.h5p.H5pReceiver
import org.edu_sharing.rendering.modules.h5p.H5pUploadService
import org.edu_sharing.rendering.modules.image.ImageConversionService
import org.edu_sharing.rendering.modules.image.ImageReceiver
import org.edu_sharing.rendering.modules.jupyter.JupyterConversionService
import org.edu_sharing.rendering.modules.jupyter.JupyterConverterConfig
import org.edu_sharing.rendering.modules.jupyter.JupyterReceiver
import org.edu_sharing.rendering.modules.moodle.MoodleReceiver
import org.edu_sharing.rendering.modules.moodle.MoodleUploadService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(
    properties = [
        "app.roles=converter",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false"
    ]
)
class ConverterRoleTest(@Autowired val context: ApplicationContext): AbstractIntegrationTest() {
    
    companion object {
        private val roleSpecificClassBeans = setOf(
            ConverterWebServiceCaller::class,
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
            H5pReceiver::class,
            H5pUploadService::class,
            MoodleReceiver::class,
            MoodleUploadService::class
        )
    }

    /*
    @Test
    fun testBeanConfiguration() {
        val roleSpecificBeans = roleSpecificClassBeans
            .map {ClassUtils.getShortNameAsProperty(it.java)}

        val expectedBeans = roleSpecificBeans union SharedBeans.all

        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }.toSet()

        assert(expectedBeans == allEdusharingBeans)
    }

     */
}