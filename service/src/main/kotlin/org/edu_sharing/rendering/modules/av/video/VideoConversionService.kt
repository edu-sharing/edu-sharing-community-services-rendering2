package org.edu_sharing.rendering.modules.av.video

import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.modules.av.AvConversionListener
import org.edu_sharing.rendering.modules.av.AvConversionService
import org.edu_sharing.rendering.modules.av.AvFileHelper
import org.edu_sharing.rendering.modules.av.ConditionalOnAvConverter
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.edu_sharing.rendering.storage.StorageService
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.*
import ws.schild.jave.info.VideoSize
import java.util.stream.Stream

@ConditionalOnAvConverter
@Service
class VideoConversionService(
    private val listenerFactory: ObjectFactory<AvConversionListener>,
    private val encoder: Encoder,
    private val avFileHelperFactory: ObjectFactory<AvFileHelper>,
    private val configuredResolutions: VideoConverterConfig,
    private val storageImplementation: StorageService,
    private val subJobRepository: SubJobRepository,
    @param:Value("\${app.converter.video.format}")
    private val videoFormat: String,
    @param:Value("\${app.converter.video.ffmpegThreads}")
    private val threads: Int,
    @param:Value("\${app.converter.video.ffmpegPreset}")
    private val preset: String
) : AvConversionService {

    companion object {
        const val AUDIO_BITRATE = 160000
        const val AUDIO_CODEC = "libmp3lame"
        const val VIDEO_CODEC = "libx264"
        const val VIDEO_CRF = 24
    }

    override fun convert(cacheObject: CacheObject, subJob: SubJob) {
        val fileHelper = avFileHelperFactory.`object`
        fileHelper.use {
            fileHelper.initOutputTempFile(videoFormat)
            fileHelper.fetchOriginalTempFile(cacheObject)
            val multiMediaObject = MultimediaObject(fileHelper.originalFile)
            val (targetWidth, targetHeight, recheckStorage) = calculateTargetDimensions(multiMediaObject, subJob.quality)
            val outputCacheObject = cacheObject.deepCopy()
            outputCacheObject.quality = targetHeight
            outputCacheObject.mimeType = "video/$videoFormat"
            subJob.quality = targetHeight
            if (recheckStorage && storageImplementation.objectExists(outputCacheObject)) {
                subJobRepository.save(subJob)
                return
            }
            val listener = listenerFactory.`object`
            listener.subJob = subJob
            val encodingAttributes = initEncodingAttributes(targetWidth, targetHeight)
            val threadArgument = object : EncodingArgument {
                override fun getArguments(var1: EncodingAttributes): Stream<String> {
                    return Stream.of("-threads", threads.toString())
                }

                override fun getArgType(): ArgType {
                    return ArgType.GLOBAL
                }
            }
            encoder.encode(listOf(multiMediaObject), fileHelper.outputFile, encodingAttributes, listener, listOf(threadArgument))
            outputCacheObject.size = fileHelper.outputFile.length()
            fileHelper.uploadToCache(outputCacheObject, mapOf(
                "height" to targetHeight.toString(),
                "width" to targetWidth.toString()
            ))
        }
    }

    /**
     * Returns Triple<targetWidth, targetHeight, recheckStorage>
     */
    @Throws(Exception::class)
    private fun calculateTargetDimensions(
        multiMediaObject: MultimediaObject,
        targetResolution: Int
    ): Triple<Int, Int, Boolean> {
        val originalHeight = multiMediaObject.info.video.size.height
        val originalWidth = multiMediaObject.info.video.size.width
        if (originalHeight < targetResolution) {
            if (targetResolution == configuredResolutions.getMinResolution()) {
                return Triple(originalWidth, originalHeight, true)
            }
            throw ConversionException("No Upscaling from $originalHeight to $targetResolution.")
        }
        val ratio = originalWidth.toFloat() / originalHeight
        var targetWidth = (targetResolution * ratio).toInt()
        if (targetWidth % 2 != 0) targetWidth -= 1
        return Triple(targetWidth, targetResolution, false)
    }

    private fun initEncodingAttributes(targetWidth: Int, targetHeight: Int): EncodingAttributes {
        val audio = AudioAttributes()
        audio.setCodec(AUDIO_CODEC)
        audio.setBitRate(AUDIO_BITRATE)
        val video = VideoAttributes()
        video.setCodec(VIDEO_CODEC)
        video.setSize(VideoSize(targetWidth, targetHeight))
        video.setCrf(VIDEO_CRF)
        video.setPreset(preset)
        val attrs = EncodingAttributes()
        attrs.setAudioAttributes(audio)
        attrs.setVideoAttributes(video)

        return attrs
    }
}
