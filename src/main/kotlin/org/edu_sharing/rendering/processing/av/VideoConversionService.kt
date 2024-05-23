package org.edu_sharing.rendering.processing.av

import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.entity.SubJob
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
class VideoConversionService (
    private val listenerFactory: ObjectFactory<AVConversionListener>,
    private val encoder: Encoder,
    private val avFileHelperFactory: ObjectFactory<AvFileHelper>,
    @Value("\${edu_sharing.video_format}")
    private val videoFormat: String,
    @Value("\${edu_sharing.video_resolutions}")
    private val videoResolutions: List<Int>
): AvConversionService {

    companion object {
        const val AUDIO_CODEC = "libmp3lame"
        const val VIDEO_CODEC = "libx264"
        const val VIDEO_CRF = 24
        const val BITRATE_FOR_AUDIO_IN_VIDEO = 160000
    }

    override fun convert(cacheObject: CacheObject, subJob: SubJob) {
        val listener = listenerFactory.`object`
        val fileHelper = avFileHelperFactory.`object`
        listener.subJob = subJob
        fileHelper.initOutputTempFile(videoFormat)
        try {
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
        } catch (exception: Exception) {
            throw exception
        } finally {
            fileHelper.cleanup()
        }
    }

    /**
     * Returns Pair<targetWidth, targetHeight>
     */
    private fun calculateTargetDimensions(
        multiMediaObject: MultimediaObject,
        targetResolution: Int
    ): Pair<Int, Int> {
        val originalHeight = multiMediaObject.info.video.size.height
        if (originalHeight < targetResolution) {
            throw Exception("No Upscaling from $originalHeight to $targetResolution")
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
        audio.setBitRate(BITRATE_FOR_AUDIO_IN_VIDEO)
        val video = VideoAttributes()
        video.setCodec(VIDEO_CODEC)
        video.setSize(VideoSize(targetWidth, targetHeight))
        video.setCrf(VIDEO_CRF)
        val attrs = EncodingAttributes()
        attrs.setAudioAttributes(audio)
        attrs.setVideoAttributes(video)
        return attrs
    }

    private fun checkIsHighestResolution(targetHeight: Int, originalHeight: Int): Boolean {
        val maxResolution = videoResolutions.maxOrNull() ?: 0
        if (targetHeight == maxResolution && originalHeight >= maxResolution) {
            return true
        }
        if (originalHeight < maxResolution) {
            return targetHeight == videoResolutions.sorted().last { it < originalHeight }
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