package org.edu_sharing.rendering.renderingJob.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory

/**
 * Every queue is declared twice: by [QueueTopologyConfig] from [QueueSpec.declareArguments], and by each receiver's
 * `@RabbitListener`. RabbitMQ rejects the second declaration with `406 PRECONDITION_FAILED` when the arguments differ, and
 * with `mismatchedQueuesFatal=false` that failure is swallowed - the consumers would silently never start. This test keeps
 * the listener side in step: every named queue must declare `x-queue-type` from its own properties bean and must not
 * carry `x-max-priority`, which quorum queues reject.
 */
class QueueDeclarationConsistencyTest {

    private data class Declaration(val owner: String, val name: String, val arguments: Map<String, String>)

    /**
     * Reads the classes straight from the classpath. Component scanning would evaluate the role conditions
     * (`@ConditionalOnConverter` etc.) and skip every receiver that is not active in the test profile.
     */
    private fun declarations(): List<Declaration> {
        val resolver = PathMatchingResourcePatternResolver()
        val readers = CachingMetadataReaderFactory(resolver)
        return resolver.getResources("classpath*:org/edu_sharing/rendering/**/*.class")
            .map { readers.getMetadataReader(it).classMetadata.className }
            .filter { it.endsWith("Receiver") }
            .map { Class.forName(it) }
            .flatMap { c -> c.declaredMethods.map { c to it } }
            .flatMap { (c, m) ->
                AnnotatedElementUtils.findAllMergedAnnotations(m, RabbitListener::class.java).flatMap { l ->
                    l.bindings.map { b ->
                        Declaration(c.simpleName, b.value.name, b.value.arguments.associate { it.name to it.value })
                    }
                }
            }
    }

    @Test
    fun `every queue listener declares x-queue-type from its own properties bean`() {
        // the two anonymous broadcast queues have an empty name and stay classic (exclusive/auto-delete is not allowed on quorum)
        val named = declarations().filter { it.name.isNotEmpty() }
        assertTrue(named.size >= 15, "expected the 15 business queues, found ${named.map { it.owner }}")
        named.forEach { d ->
            val bean = Regex("""#\{(\w+)\.name\}""").matchEntire(d.name)?.groupValues?.get(1)
            assertTrue(bean != null, "${d.owner}: queue name is not a #{bean.name} expression: ${d.name}")
            assertEquals(
                "#{$bean.queueTypeArgument}", d.arguments["x-queue-type"],
                "${d.owner}: x-queue-type must come from the same properties bean as the queue name",
            )
            assertFalse(d.arguments.containsKey("x-max-priority"), "${d.owner}: quorum queues reject x-max-priority")
        }
    }

    @Test
    fun `the single active consumer queue still declares single-active-consumer`() {
        val h5pImport = declarations().single { it.owner == "H5pImportReceiver" }
        assertEquals("#{h5pQueueProperties.singleActiveConsumer}", h5pImport.arguments["x-single-active-consumer"])
    }
}
