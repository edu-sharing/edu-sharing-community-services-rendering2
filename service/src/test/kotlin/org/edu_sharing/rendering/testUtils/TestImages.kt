package org.edu_sharing.rendering.testUtils

import org.testcontainers.utility.DockerImageName
import java.util.Properties

/**
 * Docker images of the Testcontainers, with their versions taken from the root `pom.xml`: `docker-images.properties` is
 * filtered by Maven (service/pom.xml, `testResources`) with the same `docker.edu_sharing.community.common.*` properties
 * the Helm charts and compose files use. Bumping a version there moves deployment and tests together; before, the
 * integration tests carried their own copy of every version.
 *
 * The tags in the pom are the upstream versions, so the tests use the upstream images (`mongo`, `redis`, `rabbitmq`):
 * Testcontainers' modules rely on their conventions, which the edu-sharing images (Bitnami layout) do not follow.
 *
 * A whole image can be replaced for one run with a system property, e.g. `-Dtest.image.mongodb=mongo:8.0.12`. That is for
 * machines where the pom's version cannot run (MongoDB 8.0.34 refuses to start on Linux kernel 6.19 or newer).
 */
object TestImages {

    private val versions: Properties = Properties().apply {
        val stream = TestImages::class.java.getResourceAsStream("/docker-images.properties")
            ?: error("docker-images.properties is missing from the test classpath")
        stream.use { load(it) }
    }

    private fun tag(key: String): String {
        val value = versions.getProperty(key)?.trim()
        check(!value.isNullOrEmpty() && !value.startsWith("@")) {
            "docker-images.properties: '$key' was not filled in by Maven (placeholder @...@ left) (is the test run through Maven's resource filtering?)"
        }
        return value
    }

    private fun image(system: String, default: () -> String): DockerImageName =
        DockerImageName.parse(System.getProperty(system)?.takeIf { it.isNotBlank() } ?: default())

    fun mongodb(): DockerImageName = image("test.image.mongodb") { "mongo:${tag("mongodb.tag")}" }

    fun redis(): DockerImageName = image("test.image.redis") { "redis:${tag("redis.tag")}" }

    fun rabbitmq(): DockerImageName = image("test.image.rabbitmq") { "rabbitmq:${tag("rabbitmq.tag")}-management" }
}
