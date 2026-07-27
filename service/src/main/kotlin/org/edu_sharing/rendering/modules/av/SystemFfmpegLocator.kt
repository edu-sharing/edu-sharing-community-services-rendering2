package org.edu_sharing.rendering.modules.av

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ws.schild.jave.process.ProcessLocator

/**
 * Points jave at a system-installed ffmpeg instead of the binary bundled in `jave-nativebin-*`.
 * jave's [ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator] always extracts the bundled
 * executable and offers no path override, so a custom locator is the only way to use an
 * externally managed ffmpeg (installed in the container image / on the dev machine's PATH).
 * The default interface [ProcessLocator.createExecutor] is fine — only the path differs.
 */
@Component
@ConditionalOnAvConverter
class SystemFfmpegLocator(
    @param:Value($$"${app.converter.av.ffmpegPath}")
    private val ffmpegPath: String
) : ProcessLocator {
    override fun getExecutablePath(): String = ffmpegPath
}
