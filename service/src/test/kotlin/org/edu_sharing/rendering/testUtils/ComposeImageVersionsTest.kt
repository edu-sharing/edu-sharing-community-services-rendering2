package org.edu_sharing.rendering.testUtils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The root `compose.yml` (local infrastructure for the IDE service and the load tests) cannot read the root `pom.xml`, and
 * running Maven before every `docker compose up` was ruled out. So it carries literal image versions, and this test makes
 * a drift impossible to miss: every edu-sharing infrastructure image in it (`...-common-docker-<name>:<tag>`) must have
 * exactly the tag of `docker.edu_sharing.community.common.<name>.tag` in the pom - the property the deployment and the
 * integration tests (see [TestImages]) use too. Images that float on `latest` fail as well.
 *
 * Not covered: the converter/lumi images, whose tag is the git-derived `docker.tag` of the current branch.
 */
class ComposeImageVersionsTest {

    private val prefix = "docker.edu_sharing.community.common."

    /** The repository root: the service tests run in `service/`, an IDE may start them from the root. */
    private fun repositoryRoot(): Path =
        listOf(Path.of(".."), Path.of("."))
            .map { it.toAbsolutePath().normalize() }
            .first { Files.exists(it.resolve("compose.yml")) && Files.exists(it.resolve("pom.xml")) }

    /** `<name>` -> tag, from the `docker.edu_sharing.community.common.<name>.name` / `.tag` pairs of the root pom. */
    private fun pomTagsByRepository(root: Path): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val properties = factory.newDocumentBuilder().parse(root.resolve("pom.xml").toFile())
            .getElementsByTagName("properties").item(0).childNodes
        val values = (0 until properties.length).map { properties.item(it) }.filterIsInstance<Element>()
            .associate { it.tagName to it.textContent.trim() }
        return values.keys.filter { it.startsWith(prefix) && it.endsWith(".name") }.associate { nameKey ->
            val base = nameKey.removeSuffix(".name")
            values.getValue(nameKey) to values.getValue("$base.tag")
        }
    }

    private fun composeImages(root: Path): List<String> =
        Regex("""^\s*image:\s*["']?([^"'\s#]+)""", RegexOption.MULTILINE)
            .findAll(Files.readString(root.resolve("compose.yml"))).map { it.groupValues[1] }.toList()

    @Test
    fun `the edu-sharing infrastructure images of the root compose file have the tags of the root pom`() {
        val root = repositoryRoot()
        val tags = pomTagsByRepository(root)
        val checked = composeImages(root).mapNotNull { image ->
            val repository = image.substringAfterLast('/').substringBefore(':')
            tags[repository]?.let { expected ->
                assertEquals(expected, image.substringAfter(':', ""), "compose.yml: $image must use tag $expected from the root pom.xml")
                repository
            }
        }
        // guards the guard: a broken regex or an emptied compose file would otherwise pass vacuously
        assertTrue(checked.size >= 4, "expected redis, rabbitmq, mongodb and rustfs to be checked, got $checked")
    }

    @Test
    fun `no image of the root compose file floats on latest`() {
        composeImages(repositoryRoot()).forEach {
            assertFalse(it.endsWith(":latest") || !it.substringAfterLast('/').contains(':'), "compose.yml: $it has no fixed version")
        }
    }
}
