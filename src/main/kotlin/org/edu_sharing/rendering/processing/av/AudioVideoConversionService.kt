package org.edu_sharing.rendering.processing.av

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.logic.AVConversionListener
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.info.VideoSize
import java.io.File
import java.nio.file.Files
import java.util.*

@ConditionalOnConverter
@Service
class AudioVideoConversionService (
    private val storageImplementation: StorageService,
    private val subJobRepository: SubJobRepository
){
    @Value("\${edu_sharing.video_format}")
    lateinit var videoFormat: String

    @Value("\${edu_sharing.video_resolutions}")
    lateinit var videoResolutions: List<Int>

    @Value("\${edu_sharing.audio_bitrate}")
    lateinit var audioBitrate: String

    fun convert(cacheObject: CacheObject, subJob: SubJob) {
        val isVideo = cacheObject.mimeType.substringBefore("/") == "video"
        val originalFile = downloadOriginalFile(cacheObject)
        val outputFile = File(buildString {
            append(UUID.randomUUID().toString())
            append(".")
            append(if (isVideo) videoFormat else "mp3")
        })
        val multiMediaObject = MultimediaObject(originalFile)
        val (encodingAttributes, metadata) = if (isVideo) {
            initVideoConversion(multiMediaObject, subJob.quality)
        } else {
            initAudioConversion()
        }
        val encoder = Encoder()
        val listener = AVConversionListener(subJob, subJobRepository)
        encoder.encode(multiMediaObject, outputFile, encodingAttributes, listener)
        cacheObject.quality = if (isVideo) subJob.quality else audioBitrate.toInt()
        cacheObject.size = outputFile.length()
        cacheObject.mimeType = if (isVideo) "video/$videoFormat" else "audio/mpeg"
        storageImplementation.putObject(cacheObject, outputFile.inputStream(), metadata)
        originalFile.delete()
        outputFile.delete()
    }

    private fun downloadOriginalFile(cacheObject: CacheObject): File {
        val originalFileName = buildString {
            append(UUID.randomUUID().toString())
            append(".")
            append(cacheObject.mimeType.substringAfter('/'))
        }
        val originalFile = File(originalFileName)
        val fileInputStream = storageImplementation.getObjectStream(cacheObject, true)
        Files.copy(fileInputStream, originalFile.toPath())
        fileInputStream.close()
        return originalFile
    }

    private fun checkIsHighestResolution(targetHeight: Int, originalHeight: Int): Boolean {
        val maxResolution = videoResolutions.maxOrNull() ?: 0
        if (targetHeight == maxResolution && originalHeight >= maxResolution) {
            return true
        }
        if (originalHeight < maxResolution) {
            return targetHeight == videoResolutions.sorted().last { it < originalHeight}
        }
        return false
    }

    private fun initVideoConversion(
        multimediaObject: MultimediaObject,
        targetResolution: Int):
            Pair<EncodingAttributes, Map<String, String>>
    {
        val originalHeight = multimediaObject.info.video.size.height
        if (originalHeight < targetResolution) {
            throw Exception("No Upscaling from $originalHeight to $targetResolution")
        }
        val originalWidth = multimediaObject.info.video.size.width
        val ratio = originalWidth.toFloat()/originalHeight
        var targetWidth = (targetResolution*ratio).toInt()
        if (targetWidth % 2 != 0) targetWidth -= 1
        val audio = AudioAttributes()
        audio.setCodec("libmp3lame")
        audio.setBitRate(160000)
        val video = VideoAttributes()
        video.setCodec("libx264")
        video.setSize(VideoSize(targetWidth,targetResolution))
        video.setCrf(24)
        val attrs = EncodingAttributes()
        attrs.setAudioAttributes(audio)
        attrs.setVideoAttributes(video)
        return attrs to mapOf(
            "isHighestResolution" to checkIsHighestResolution(targetResolution, originalHeight).toString(),
            "height" to targetResolution.toString(),
            "width" to targetWidth.toString()
        )
    }

    private fun initAudioConversion(): Pair<EncodingAttributes, Map<String, String>> {
        val audioAttributes = AudioAttributes()
        audioAttributes.setCodec("libmp3lame")
        audioAttributes.setBitRate(audioBitrate.toInt())
        audioAttributes.setChannels(2)
        audioAttributes.setSamplingRate(44100)
        val encodingAttributes = EncodingAttributes()
        encodingAttributes.setOutputFormat("mp3")
        encodingAttributes.setAudioAttributes(audioAttributes)
        return encodingAttributes to emptyMap()
    }
}
