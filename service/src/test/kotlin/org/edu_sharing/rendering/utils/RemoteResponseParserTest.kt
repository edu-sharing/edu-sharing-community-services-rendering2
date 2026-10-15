package org.edu_sharing.rendering.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class RemoteResponseParserTest {
    private data class Dto(val name: String)

    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    @Test
    fun `parses valid json`() {
        assertEquals(Dto("a"), RemoteResponseParser.parse("""{"name":"a"}""", Dto::class.java, mapper, "X API"))
    }

    @Test
    fun `null and blank bodies are reported as empty`() {
        listOf(null, "", "  \n").forEach {
            val e = assertThrows<IllegalStateException> { RemoteResponseParser.parse(it, Dto::class.java, mapper, "X API") }
            assertTrue(e.message!!.contains("X API returned an empty response"))
        }
    }

    @Test
    fun `non json body yields a short sanitized excerpt`() {
        val body = "<html>\n  <body>error ?token=SECRET " + "y".repeat(500) + "</body></html>"
        val e = assertThrows<IllegalStateException> { RemoteResponseParser.parse(body, Dto::class.java, mapper, "X API") }

        assertTrue(e.message!!.contains("X API"))
        assertTrue(e.message!!.contains("Dto"))
        assertFalse(e.message!!.contains("SECRET"))
        assertTrue(e.message!!.length < 300)
    }
}
