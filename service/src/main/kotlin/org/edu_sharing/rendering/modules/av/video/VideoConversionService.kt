package org.edu_sharing.rendering.modules.av.video

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.modules.av.AvConversionListener
import org.edu_sharing.rendering.modules.av.AvConversionService
import org.edu_sharing.rendering.modules.av.AvFileHelper
import org.edu_sharing.rendering.modules.av.ConditionalOnAvConverter
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.info.VideoSize

@ConditionalOnAvConverter
@Service
class VideoConversionService(
    private val listenerFactory: ObjectFactory<AvConversionListener>,
    private val encoder: Encoder,
    private val avFileHelperFactory: ObjectFactory<AvFileHelper>,
) : AvConversionService {

    @Value("\${app.converter.video.format}")
    lateinit var videoFormat: String

    companion object {
        const val AUDIO_BITRATE = 160000
        const val AUDIO_CODEC = "libmp3lame"
        const val VIDEO_CODEC = "libx264"
        const val VIDEO_CRF = 24
    }

    override fun convert(cacheObject: CacheObject, subJob: SubJob) {
        val listener = listenerFactory.`object`
        listener.subJob = subJob

        val fileHelper = avFileHelperFactory.`object`
        fileHelper.use {
            fileHelper.initOutputTempFile(videoFormat)
            fileHelper.fetchOriginalTempFile(cacheObject)
            val multiMediaObject = MultimediaObject(fileHelper.originalFile)
            val (targetWidth, targetHeight) = calculateTargetDimensions(multiMediaObject, subJob.quality)
            val encodingAttributes = initEncodingAttributes(targetWidth, targetHeight)
            encoder.encode(multiMediaObject, fileHelper.outputFile, encodingAttributes, listener)
            val outputCacheObject = cacheObject.deepCopy()
            outputCacheObject.quality = subJob.quality
            outputCacheObject.size = fileHelper.outputFile.length()
            outputCacheObject.mimeType = "video/$videoFormat"
            fileHelper.uploadToCache(outputCacheObject, mapOf(
                "height" to targetHeight.toString(),
                "width" to targetWidth.toString()
            ))
        }
    }

    /**
     * Returns Pair<targetWidth, targetHeight>
     */
    @Throws(Exception::class)
    private fun calculateTargetDimensions(
        multiMediaObject: MultimediaObject,
        targetResolution: Int
    ): Pair<Int, Int> {
        val originalHeight = multiMediaObject.info.video.size.height
        if (originalHeight < targetResolution) {
            throw ConversionException("No Upscaling from $originalHeight to $targetResolution.")
        }
        val originalWidth = multiMediaObject.info.video.size.width
        val ratio = originalWidth.toFloat() / originalHeight
        var targetWidth = (targetResolution * ratio).toInt()
        if (targetWidth % 2 != 0) targetWidth -= 1
        return targetWidth to targetResolution
    }

    private fun initEncodingAttributes(targetWidth: Int, targetHeight: Int): EncodingAttributes {
        val audio = AudioAttributes()
        audio.setCodec(AUDIO_CODEC)
        audio.setBitRate(AUDIO_BITRATE)
        val video = VideoAttributes()
        video.setCodec(VIDEO_CODEC)
        video.setSize(VideoSize(targetWidth, targetHeight))
        video.setCrf(VIDEO_CRF)
        val attrs = EncodingAttributes()
        attrs.setAudioAttributes(audio)
        attrs.setVideoAttributes(video)
        return attrs
    }
}
