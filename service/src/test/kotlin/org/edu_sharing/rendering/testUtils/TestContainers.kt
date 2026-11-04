package org.edu_sharing.rendering.testUtils

import com.github.dockerjava.api.command.InspectContainerResponse
import com.redis.testcontainers.RedisContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.rabbitmq.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

/**
 * The Testcontainers on the edu-sharing images ([TestImages]). Those images follow the Bitnami layout, not the conventions of
 * the upstream images the Testcontainers modules assume, so each container is adapted here - the same settings the compose
 * files and Helm charts give them:
 * - RabbitMQ: the default user may only log in from localhost unless `RABBITMQ_MANAGEMENT_ALLOW_WEB_ACCESS` is set, and it is
 *   created from `RABBITMQ_DEFAULT_USER`/`_PASS`, not the upstream `guest`.
 * - Redis: refuses to start without a password unless `ALLOW_EMPTY_PASSWORD` is set.
 * - MongoDB: its entrypoint executes any argument as a command, so `MongoDBContainer`'s `--replSet` start argument fails
 *   (`exec: --: invalid option`); [ReplicaSetMongoContainer] passes it through `MONGODB_EXTRA_FLAGS` instead.
 */
object TestContainers {

    const val RABBITMQ_USER = "test"
    const val RABBITMQ_PASSWORD = "test"

    fun rabbitmq(): RabbitMQContainer =
        RabbitMQContainer(TestImages.rabbitmq().asCompatibleSubstituteFor("rabbitmq"))
            .withAdminUser(RABBITMQ_USER)
            .withAdminPassword(RABBITMQ_PASSWORD)
            .withEnv("RABBITMQ_MANAGEMENT_ALLOW_WEB_ACCESS", "true")

    fun redis(): RedisContainer =
        RedisContainer(TestImages.redis().asCompatibleSubstituteFor("redis"))
            .withEnv("ALLOW_EMPTY_PASSWORD", "yes")

    fun mongodb(): ReplicaSetMongoContainer = ReplicaSetMongoContainer(TestImages.mongodb())
}

/**
 * A single-node MongoDB replica set on an edu-sharing (Bitnami) image, what `MongoDBContainer` provides for the upstream image:
 * the integration tests need a replica set (transactions). The set is initiated with the container's own hostname and the
 * internal port, like `MongoDBContainer` does; the client reaches the node through the mapped host port with a direct
 * connection (a single host in the URL and no `replicaSet` parameter).
 */
class ReplicaSetMongoContainer(image: DockerImageName) : GenericContainer<ReplicaSetMongoContainer>(image) {

    init {
        addExposedPort(MONGODB_PORT)
        withEnv("ALLOW_EMPTY_PASSWORD", "yes")
        withEnv("MONGODB_EXTRA_FLAGS", "--replSet $REPLICA_SET")
        waitingFor(Wait.forLogMessage("(?is).*waiting for connections.*", 1))
    }

    fun getReplicaSetUrl(database: String = "test"): String = "mongodb://$host:${getMappedPort(MONGODB_PORT)}/$database"

    override fun containerIsStarted(containerInfo: InspectContainerResponse) {
        val member = "${containerInfo.config.hostName}:$MONGODB_PORT"
        evaluate("rs.initiate({_id: '$REPLICA_SET', members: [{_id: 0, host: '$member'}]})")
        // the node elects itself primary a moment after the initiation
        val deadline = System.currentTimeMillis() + 30_000
        while (evaluate("db.hello().isWritablePrimary").trim() != "true") {
            check(System.currentTimeMillis() < deadline) { "MongoDB replica set $REPLICA_SET did not elect a primary within 30 seconds" }
            Thread.sleep(500)
        }
    }

    private fun evaluate(script: String): String {
        val result = execInContainer("mongosh", "--quiet", "--eval", script)
        check(result.exitCode == 0) { "mongosh '$script' failed with ${result.exitCode}: ${result.stderr}" }
        return result.stdout
    }

    private companion object {
        const val MONGODB_PORT = 27017
        const val REPLICA_SET = "docker-rs"
    }
}
