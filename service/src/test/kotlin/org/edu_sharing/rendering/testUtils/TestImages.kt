package org.edu_sharing.rendering.testUtils

import org.testcontainers.utility.DockerImageName
import java.util.Properties

/**
 * The Docker images of the Testcontainers: the edu-sharing images the deployment runs, with name and version taken from the
 * root `pom.xml`. `docker-images.properties` is filtered by Maven (service/pom.xml, `testResources`) with the same
 * `docker.edu_sharing.community.common.*` properties the Helm charts and compose files use, so a version bumped there moves
 * the deployment **and** what the integration tests run against. Before, the tests carried their own copy of every version
 * and ran upstream images that differed from what is deployed.
 *
 * A whole image can be replaced for one run with a system property, e.g.
 * `-Dtest.image.mongodb=docker.edu-sharing.com/community/common/edu_sharing-community-common-docker-mongodb:8.0.12-1`.
 * That is for machines where the pom's version cannot run (MongoDB 8.0.34 refuses to start on Linux kernel 6.19 or newer).
 *
 * The containers that use these images are configured in [TestContainers].
 */
object TestImages {

    private val images: Properties = Properties().apply {
        val stream = TestImages::class.java.getResourceAsStream("/docker-images.properties")
            ?: error("docker-images.properties is missing from the test classpath")
        stream.use { load(it) }
    }

    private fun image(service: String): DockerImageName {
        val reference = System.getProperty("test.image.$service")?.takeIf { it.isNotBlank() }
            ?: images.getProperty("$service.image")?.trim()
        check(!reference.isNullOrEmpty() && !reference.contains('@')) {
            "docker-images.properties: '$service.image' was not filled in by Maven (placeholder @...@ left; is the test run through Maven's resource filtering?)"
        }
        return DockerImageName.parse(reference)
    }

    fun mongodb(): DockerImageName = image("mongodb")

    fun redis(): DockerImageName = image("redis")

    fun rabbitmq(): DockerImageName = image("rabbitmq")
}
