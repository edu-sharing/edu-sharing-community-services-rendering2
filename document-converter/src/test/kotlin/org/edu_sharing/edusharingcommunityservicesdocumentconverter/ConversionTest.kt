package org.edu_sharing.edusharingcommunityservicesdocumentconverter

import org.edu_sharing.edusharingcommunityservicesdocumentconverter.config.ConverterProperties
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.exception.ResultTooLargeException
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.io.DeleteOnCloseResource
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.io.TempFiles
import org.edu_sharing.edusharingcommunityservicesdocumentconverter.service.ConversionService
import org.jodconverter.local.LocalConverter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.util.unit.DataSize
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ConversionTest {
    @TempDir
    lateinit var dir: Path

    private fun tempFiles(props: ConverterProperties = ConverterProperties(workDir = dir.toString())) =
        TempFiles(props)

    private fun service(props: ConverterProperties) =
        ConversionService(org.mockito.Mockito.mock(LocalConverter::class.java), tempFiles(props), props)

    @Test
    fun `html style is injected before head end and the rest is streamed unchanged`() {
        val props = ConverterProperties(workDir = dir.toString(), htmlStyleScanWindow = DataSize.ofBytes(64))
        val html = dir.resolve("in.html")
        val tail = "<body>" + "x".repeat(500) + "</body></html>"
        Files.writeString(html, "<html><head><title>t</title></HEAD>$tail")
        val out = service(props).addHtmlStyle(html)
        val text = Files.readString(out)
        assertTrue(text.startsWith("<html><head><title>t</title><style type=\"text/css\""))
        assertTrue(text.endsWith("</HEAD>$tail"))
    }

    @Test
    fun `html without head falls back to jsoup and respects the size limit`() {
        val html = dir.resolve("in.html")
        Files.writeString(html, "<p>hello</p>")
        val ok = service(ConverterProperties(workDir = dir.toString())).addHtmlStyle(html)
        assertTrue(Files.readString(ok).contains("<style"))
        val tiny = ConverterProperties(workDir = dir.toString(), maxHtmlDomBytes = DataSize.ofBytes(3))
        assertFailsWith<ResultTooLargeException> { service(tiny).addHtmlStyle(html) }
    }

    @Test
    fun `deleteOnClose resource removes the file when the stream is closed`() {
        val tf = tempFiles()
        val file = tf.create(".pdf").also { Files.writeString(it, "data") }
        val resource = DeleteOnCloseResource(file, tf)
        assertEquals(4, resource.contentLength())
        resource.inputStream.use { assertEquals("data", String(it.readAllBytes())) }
        assertFalse(Files.exists(file))
    }

    @Test
    fun `startup sweep removes only stale entries`() {
        val tf = tempFiles()
        val old = tf.create(".tmp").also { Files.setLastModifiedTime(it, FileTime.from(Instant.now().minusSeconds(7 * 3600))) }
        val fresh = tf.create(".tmp")
        val foreign = Files.createFile(dir.resolve("keep.txt")).also {
            Files.setLastModifiedTime(it, FileTime.from(Instant.now().minusSeconds(7 * 3600)))
        }
        tf.run(org.mockito.Mockito.mock(org.springframework.boot.ApplicationArguments::class.java))
        assertFalse(Files.exists(old))
        assertTrue(Files.exists(fresh))
        assertTrue(Files.exists(foreign))
    }
}
