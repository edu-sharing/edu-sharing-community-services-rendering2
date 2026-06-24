package org.edu_sharing.rendering.integration

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
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
        private val minioContainer = MinIOContainer("minio/minio")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")

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

            // MinIO
            registry.add("app.minio.url") { "http://${minioContainer.host}:${minioContainer.getMappedPort(9000)}" }
            registry.add("app.minio.user") { "minioadmin" }
            registry.add("app.minio.password") { "minioadmin" }

            // S3 client (S3Config binds app.s3.*) – point it at the MinIO testcontainer so that
            // storage operations (e.g. admin asset deletion) actually hit a real S3 backend.
            registry.add("app.s3.url") { "http://${minioContainer.host}:${minioContainer.getMappedPort(9000)}" }
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
