package org.edu_sharing.rendering.modules.av.video

import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.exception.ConversionException
import org.edu_sharing.rendering.modules.av.AVConversionListener
import org.edu_sharing.rendering.modules.av.AvConversionService
import org.edu_sharing.rendering.modules.av.AvFileHelper
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.info.VideoSize

@ConditionalOnConverter
@Service
class VideoConversionService(
    private val listenerFactory: ObjectFactory<AVConversionListener>,
    private val encoder: Encoder,
    private val avFileHelperFactory: ObjectFactory<AvFileHelper>,
) : AvConversionService {

    @Value("\${app.converter.video.format}")
    lateinit var videoFormat: String

    @Value("\${app.converter.video.resolutions}")
    lateinit var videoResolutions: List<Int>

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
            val metadata = metaData(multiMediaObject.info.video.size.height, targetWidth, targetHeight)
            outputCacheObject.quality = subJob.quality
            outputCacheObject.size = fileHelper.outputFile.length()
            outputCacheObject.mimeType = "video/$videoFormat"
            fileHelper.uploadToCache(outputCacheObject, metadata)
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
            throw ConversionException("No Upscaling from $originalHeight to $targetResolution")
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

    /**
     * We need to tag the rendered video with the highest resolution in order to
     * prevent that a new job for a potentially impossible resolution is created all
     * over again.
     *
     * This method checks if the currently rendered video has the highest possible resolution.
     */
    private fun checkIsHighestResolution(targetHeight: Int, originalHeight: Int): Boolean {
        if (videoResolutions.isEmpty()) {
            throw ConversionException("Video target resolutions are not set")
        }
        val maxResolution = videoResolutions.max()
        if (targetHeight == maxResolution) {
            return true
        }
        if (originalHeight < maxResolution) {
            return targetHeight == videoResolutions.sorted().last { it <= originalHeight }
        }
        return false
    }

    private fun metaData(originalHeight: Int, targetWidth: Int, targetHeight: Int): Map<String, String> {
        return mapOf(
            "isHighestResolution" to checkIsHighestResolution(targetHeight, originalHeight).toString(),
            "height" to targetHeight.toString(),
            "width" to targetWidth.toString()
        )
    }
}
