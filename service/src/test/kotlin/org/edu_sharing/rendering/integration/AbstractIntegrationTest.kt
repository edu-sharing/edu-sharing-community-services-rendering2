package org.edu_sharing.rendering.integration

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.rabbitmq.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@DirtiesContext
@Testcontainers
abstract class AbstractIntegrationTest() {

    companion object {

        @JvmStatic
        @Container
        private val mongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:6.0.6"))

        @JvmStatic
        @Container
        // MinIO no longer publishes container images (Docker Hub and quay.io), so use Adobe's S3Mock as the
        // S3-compatible backend, pinned to a fixed release for reproducible builds. It accepts any credentials.
        private val s3Container = GenericContainer(DockerImageName.parse("adobe/s3mock:5.2.3"))
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200))

        @JvmStatic
        @Container
        private val rabbitMQContainer = RabbitMQContainer("rabbitmq:3.11-management")

        @JvmStatic
        @Container
        private val redisContainer: RedisContainer = RedisContainer(DockerImageName.parse("redis:7.0.10"))
            .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            // MongoDB
            registry.add("spring.mongodb.uri", mongoDBContainer::getReplicaSetUrl)

            // S3 (S3Mock)
            val s3Url = "http://${s3Container.host}:${s3Container.getMappedPort(9090)}"
            registry.add("app.minio.url") { s3Url }
            registry.add("app.minio.user") { "minioadmin" }
            registry.add("app.minio.password") { "minioadmin" }

            // S3 client (S3Config binds app.s3.*) – point it at the S3Mock testcontainer so that
            // storage operations (e.g. admin asset deletion) actually hit a real S3 backend.
            registry.add("app.s3.url") { s3Url }
            registry.add("app.s3.accessKeyId") { "minioadmin" }
            registry.add("app.s3.secretAccessKey") { "minioadmin" }

            // RabbitMQ
            registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost)
            registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort)

            // Redis
            registry.add("spring.redis.standalone.host", redisContainer::getHost)
            registry.add("spring.redis.standalone.port") { redisContainer.getMappedPort(6379) }
        }
    }
}
