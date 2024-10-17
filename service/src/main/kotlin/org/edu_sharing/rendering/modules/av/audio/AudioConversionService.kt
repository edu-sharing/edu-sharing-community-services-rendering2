package org.edu_sharing.rendering.modules.av.audio

import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.modules.av.AvConversionListener
import org.edu_sharing.rendering.modules.av.AvConversionService
import org.edu_sharing.rendering.modules.av.AvFileHelper
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes

@ConditionalOnConverter
@Service
class AudioConversionService(
    private val listenerFactory: ObjectFactory<AvConversionListener>,
    private val encoder: Encoder,
    private val avFileHelperFactory: ObjectFactory<AvFileHelper>
) : AvConversionService {
    companion object {
        const val OUTPUT_FORMAT = "mp3"
        const val CODEC = "libmp3lame"
        const val SAMPLING_RATE = 44100
        const val MIME_TYPE = "audio/mpeg"
    }

    @Value("\${app.converter.audio.bitrate}")
    lateinit var bitrate: String

    override fun convert(cacheObject: CacheObject, subJob: SubJob) {
        val listener = listenerFactory.`object`
        listener.subJob = subJob
        val attributes = initAttributes()

        val fileHelper = avFileHelperFactory.`object`
        fileHelper.use {
            fileHelper.initOutputTempFile(OUTPUT_FORMAT)
            fileHelper.fetchOriginalTempFile(cacheObject)
            val multiMediaObject = MultimediaObject(fileHelper.originalFile)
            encoder.encode(multiMediaObject, fileHelper.outputFile, attributes, listener)
            val outputCacheObject = cacheObject.deepCopy()
            outputCacheObject.quality = bitrate.toInt()
            outputCacheObject.size = fileHelper.outputFile.length()
            outputCacheObject.mimeType = MIME_TYPE
            fileHelper.uploadToCache(outputCacheObject)
        }
    }

    private fun initAttributes(): EncodingAttributes {
        val audioAttributes = AudioAttributes()
        audioAttributes.setCodec(CODEC)
        audioAttributes.setBitRate(bitrate.toInt())
        audioAttributes.setChannels(2)
        audioAttributes.setSamplingRate(SAMPLING_RATE)
        val encodingAttributes = EncodingAttributes()
        encodingAttributes.setOutputFormat(OUTPUT_FORMAT)
        encodingAttributes.setAudioAttributes(audioAttributes)
        return encodingAttributes
    }
}
