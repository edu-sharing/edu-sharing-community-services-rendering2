package org.edu_sharing.rendering.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogSanitizerTest {

    @Test
    fun `masks query parameters`() {
        val input = "404 from GET https://api.example.org/items/1?oauth_consumer_key=SECRET123&foo=bar"
        assertEquals(
            "404 from GET https://api.example.org/items/1?oauth_consumer_key=***&foo=bar",
            LogSanitizer.sanitize(input)
        )
    }

    @Test
    fun `masks all known keys case-insensitively`() {
        listOf("wstoken", "password", "token", "oauth_consumer_key", "authToken", "apikey", "TOKEN").forEach { key ->
            val result = LogSanitizer.sanitize("a=1&$key=abc123&b=2")!!
            assertFalse(result.contains("abc123"), "key $key leaked")
            assertTrue(result.contains("$key=***"), "key $key not masked: $result")
            assertTrue(result.contains("b=2"))
        }
    }

    @Test
    fun `masks value up to whitespace or closing bracket`() {
        assertEquals("uri [x?token=***] failed", LogSanitizer.sanitize("uri [x?token=abc] failed"))
        assertEquals("GET x?token=*** failed", LogSanitizer.sanitize("GET x?token=abc failed"))
    }

    @Test
    fun `does not mask unrelated keys or partial names`() {
        val input = "https://x/y?name=bob&mytokenizer=1&page=2"
        assertEquals(input, LogSanitizer.sanitize(input))
    }

    @Test
    fun `masks json pairs`() {
        assertEquals(
            """{"user":"a","password":"***"}""",
            LogSanitizer.sanitize("""{"user":"a","password":"hunter2"}""")
        )
    }

    @Test
    fun `masks authorization and cookie header lines`() {
        val result = LogSanitizer.sanitize("Authorization: Bearer abc.def\nCookie: JSESSIONID=1; x=2\nAccept: */*")!!
        assertFalse(result.contains("abc.def"))
        assertFalse(result.contains("JSESSIONID"))
        assertTrue(result.contains("Accept: */*"))
    }

    @Test
    fun `null and empty pass through`() {
        assertNull(LogSanitizer.sanitize(null as String?))
        assertEquals("", LogSanitizer.sanitize(""))
    }

    @Test
    fun `describe sanitizes message and root cause`() {
        val ex = RuntimeException("failed GET /a?wstoken=AAA", IllegalStateException("inner password=BBB"))
        val described = LogSanitizer.describe(ex)
        assertFalse(described.contains("AAA"))
        assertFalse(described.contains("BBB"))
        assertTrue(described.contains("RuntimeException"))
        assertTrue(described.contains("IllegalStateException"))
    }

    @Test
    fun `sanitized throwable keeps stack trace but not secrets`() {
        val original = RuntimeException("boom ?token=SECRET", RuntimeException("cause token=SECRET"))
        val sanitized = LogSanitizer.sanitize(original)
        assertFalse(sanitized.message!!.contains("SECRET"))
        assertNull(sanitized.cause)
        assertTrue(sanitized.stackTrace.contentEquals(original.stackTrace))
    }
}
