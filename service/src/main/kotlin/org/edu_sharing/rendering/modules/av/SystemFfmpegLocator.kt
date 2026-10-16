package org.edu_sharing.rendering.modules.av

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ws.schild.jave.process.ProcessLocator
import ws.schild.jave.process.ProcessWrapper
import java.util.stream.Stream

/**
 * Points jave at a system-installed ffmpeg instead of the binary bundled in `jave-nativebin-*`.
 * jave's [ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator] always extracts the bundled
 * executable and offers no path override, so a custom locator is the only way to use an
 * externally managed ffmpeg (installed in the container image / on the dev machine's PATH).
 *
 * With `app.converter.av.niceness` > 0 every ffmpeg process is started through `nice -n <niceness>`.
 * ffmpeg shares the pod's CPU quota with the JVM; at a lower OS priority the JVM (HTTP requests,
 * in-JVM conversions such as images, the queue consumers) gets the CPU first whenever it needs it,
 * and ffmpeg runs on what is left instead of starving everything else in the pod. Lowering the
 * own priority needs no privileges.
 */
@Component
@ConditionalOnAvConverter
class SystemFfmpegLocator(
    @param:Value($$"${app.converter.av.ffmpegPath}")
    private val ffmpegPath: String,
    @param:Value($$"${app.converter.av.niceness:0}")
    private val niceness: Int = 0
) : ProcessLocator {
    override fun getExecutablePath(): String = ffmpegPath

    override fun createExecutor(): ProcessWrapper =
        if (niceness > 0) NicedProcessWrapper(ffmpegPath, niceness) else ProcessWrapper(ffmpegPath)
}

/**
 * Prepends `nice -n <niceness>` to the command line. Done in [enhanceArguments] rather than by making
 * `nice` the executable, so jave still sees the real ffmpeg path (it probes the executable itself, e.g.
 * for `-fps_mode` support). `nice` execs ffmpeg in place, so [ProcessWrapper.destroy] still kills ffmpeg.
 */
private class NicedProcessWrapper(ffmpegPath: String, private val niceness: Int) : ProcessWrapper(ffmpegPath) {
    override fun enhanceArguments(execArgs: Stream<String>): Stream<String> =
        Stream.concat(Stream.of("nice", "-n", niceness.toString()), execArgs)
}
