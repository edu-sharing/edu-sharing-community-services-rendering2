package org.edu_sharing.rendering.utils

import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.mime.MimeTypes
import org.springframework.http.MediaTypeFactory

/**
 * Resolves a mime type from a file name alone (no content sniffing).
 *
 * Deliberately **not** [java.net.URLConnection.guessContentTypeFromName]: that returns `null`
 * for everything missing from the JDK's small `content-types.properties` table — notably web
 * fonts (`.woff`, `.woff2`, `.ttf`, `.otf`, `.eot`), `.ico`, `.map` and `.h5p` — which used to
 * abort eduhtml archive extraction with a Kotlin null-assertion failure.
 *
 * Spring's `mime.types` is consulted first because it carries the modern web types
 * (`font/woff2`, `text/javascript`, `application/wasm`); Tika fills the remaining gaps
 * (`.vtt`, `.webmanifest`, `.md`, extension-less files) and already falls back to
 * `application/octet-stream`, so the result is never blank.
 *
 * Getting this right is not cosmetic: public asset responses carry Spring Security's default
 * `X-Content-Type-Options: nosniff`, so a script or stylesheet stored as
 * `application/octet-stream` is *blocked* by the browser rather than sniffed.
 */
object MimeTypeResolver {
    private val tikaMimeTypes = MimeTypes.getDefaultMimeTypes()

    fun fromFileName(fileName: String): String =
        MediaTypeFactory.getMediaType(fileName).map { it.toString() }.orElse(null)
            ?: tikaMimeTypes.detect(null, Metadata().apply {
                set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName)
            }).toString()
}
