package org.edu_sharing.rendering.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
class S3Config {

    @Value("\${app.s3.accessKeyId}")
    var accessKeyId: String? = null

    @Value("\${app.s3.secretAccessKey}")
    var secretAccessKey: String? = null

    @Value("\${app.s3.region}")
    var region: String? = null

    @Value("\${app.s3.url}")
    var url: String? = null

    @Bean
    fun s3Client(): S3Client {
        val s3 = S3Client.builder()
            .endpointOverride(URI.create(url ?: ""))
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                )
            )
            .forcePathStyle(true)
            .build()
        return s3
    }
}
