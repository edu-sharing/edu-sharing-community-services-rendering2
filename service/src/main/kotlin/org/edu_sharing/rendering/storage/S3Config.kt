package org.edu_sharing.rendering.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.http.TlsTrustManagersProvider
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Configuration
class S3Config {

    @Value($$"${app.s3.accessKeyId}")
    var accessKeyId: String? = null

    @Value($$"${app.s3.secretAccessKey}")
    var secretAccessKey: String? = null

    @Value($$"${app.s3.region}")
    var region: String? = null

    @Value($$"${app.s3.url}")
    var url: String? = null

    @Value($$"${app.s3.trustAllCertificates:false}")
    var trustAllCertificates: Boolean = false

    @Value($$"${app.s3.checksumCalculationWhenRequired:false}")
    var checksumCalculationWhenRequired: Boolean = false

    private val trustAllCertificatesProvider = TlsTrustManagersProvider {
        arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
    }

    @Bean
    fun s3Client(): S3Client {
        return S3Client.builder()
            .endpointOverride(URI.create(url ?: ""))
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                )
            )
            .forcePathStyle(true)
            .apply {
                if (trustAllCertificates) {
                    httpClientBuilder(
                        ApacheHttpClient.builder()
                            .tlsTrustManagersProvider(trustAllCertificatesProvider)
                    )
                }

                if (checksumCalculationWhenRequired) {
                    requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                }
            }
            .build()
    }
}
