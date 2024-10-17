package org.edu_sharing.rendering.roles

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
import org.junit.jupiter.api.Test
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
class ConverterRoleTest(@Autowired val context: ApplicationContext) {

    @Test
    fun testBeanConfiguration() {
        val roleSpecificBeans = setOf(
            AvConversionListener::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            AvConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            AvFileHelperFactory::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            AvReceiver::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            AudioConversionService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            VideoConversionService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            DocumentConversionService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            DocumentConverterConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            DocumentReceiver::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            EduHtmlConversionService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            EduHtmlReceiver::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ImageConversionService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ImageReceiver::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
        )

        val roleSpecificFunctionalBeans = setOf(
            "createAvFileHelper"
        )

        val allEdusharingBeans = context.beanDefinitionNames.filter {
            context.getBean(it).javaClass.packageName.startsWith("org.edu_sharing.rendering")
        }

        roleSpecificBeans.forEach {
            assert(allEdusharingBeans.contains(it))
        }

        roleSpecificFunctionalBeans.forEach {
            assert(allEdusharingBeans.contains(it))
        }

        val remainingBeans = allEdusharingBeans.filter {
            ! roleSpecificBeans.contains(it)
                    && ! SharedBeans.set.contains(it)
                    && ! SharedBeans.functionalBeans.contains(it)
                    && ! roleSpecificFunctionalBeans.contains(it)
        }

        assert(remainingBeans.isEmpty())
    }
}