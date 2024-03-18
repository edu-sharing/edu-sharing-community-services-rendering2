package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.blobStorage.StorageService
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


@Service
class AudioVideoConversionService (
    private val storageImplementation: StorageService,
    private val subJobRepository: SubJobRepository
){
    @Value("\${edu_sharing.video_format}")
    lateinit var videoFormat: String

    @Value("\${edu_sharing.video_resolutions}")
    lateinit var videoResolutions: List<Int>
    fun convert(cacheObject: CacheObject, subJob: SubJob) {
        val targetHeight = subJob.quality
        val originalFile = downloadOriginalFile(cacheObject)
        val outputFile = File(java.util.UUID.randomUUID().toString() + "." + videoFormat)
        val multiMediaObject = MultimediaObject(originalFile)
        val originalHeight = multiMediaObject.info.video.size.height
        if (originalHeight < targetHeight) {
            throw Exception("No Upscaling from $originalHeight to $targetHeight")
        }
        val originalWidth = multiMediaObject.info.video.size.width
        val ratio = originalWidth.toFloat()/originalHeight
        var targetWidth = (targetHeight*ratio).toInt()
        if (targetWidth % 2 != 0) targetWidth -= 1
        val audio = AudioAttributes()
        audio.setCodec("libmp3lame")
        audio.setBitRate(160000)
        val video = VideoAttributes()
        video.setCodec("libx264")
        video.setSize(VideoSize(targetWidth,targetHeight))
        video.setCrf(24)
        val attrs = EncodingAttributes()
        attrs.setAudioAttributes(audio)
        attrs.setVideoAttributes(video)
        val encoder = Encoder()
        val listener = AVConversionListener(subJob, subJobRepository)
        encoder.encode(multiMediaObject, outputFile, attrs, listener)
        cacheObject.quality = targetHeight
        cacheObject.size = outputFile.length()
        cacheObject.mimeType = "video/$videoFormat"
        val metadata = mapOf(
            "isHighestResolution" to checkIsHighestResolution(targetHeight, originalHeight).toString()
        )
        storageImplementation.putObject(cacheObject, outputFile.inputStream(), metadata)
        originalFile.delete()
        outputFile.delete()
    }

    private fun downloadOriginalFile(cacheObject: CacheObject): File {
        val originalFileName = java.util.UUID.randomUUID().toString() + "." + cacheObject.mimeType.substringAfter('/')
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
}