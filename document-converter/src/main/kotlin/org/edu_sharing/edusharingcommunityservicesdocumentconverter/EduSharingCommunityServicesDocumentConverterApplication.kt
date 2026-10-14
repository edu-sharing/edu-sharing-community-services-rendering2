package org.edu_sharing.edusharingcommunityservicesdocumentconverter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class EduSharingCommunityServicesDocumentConverterApplication

fun main(args: Array<String>) {
    runApplication<EduSharingCommunityServicesDocumentConverterApplication>(*args)
}
