package org.edu_sharing.rendering.modules.av

import ws.schild.jave.encode.ArgType
import ws.schild.jave.encode.EncodingArgument
import ws.schild.jave.encode.EncodingAttributes
import java.util.stream.Stream

/**
 * Global ffmpeg `-threads` argument shared by audio and video encoding.
 * 0 = ffmpeg decides how many threads to use, else the number of threads.
 */
fun ffmpegThreadsArg(threads: Int): EncodingArgument = object : EncodingArgument {
    override fun getArguments(var1: EncodingAttributes): Stream<String> {
        return Stream.of("-threads", threads.toString())
    }

    override fun getArgType(): ArgType {
        return ArgType.GLOBAL
    }
}
