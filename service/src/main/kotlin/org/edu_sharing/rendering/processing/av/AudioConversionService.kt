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

@ConditionalOnConverter
@Service
class AudioConversionService(
    private val listenerFactory: ObjectFactory<AVConversionListener>,
    private val encoder: Encoder,
    private val avFileHelperFactory: ObjectFactory<AvFileHelper>
): AvConversionService {
    companion object {
        const val OUTPUT_FORMAT = "mp3"
        const val CODEC = "libmp3lame"
        const val SAMPLING_RATE = 44100
        const val MIME_TYPE = "audio/mpeg"
    }

    @Value("\${app.converter.audio.bitrate}")
    lateinit var bitrate: String

    override fun convert(cacheObject: CacheObject, subJob: SubJob) {
        val fileHelper = avFileHelperFactory.`object`
        val listener = listenerFactory.`object`
        listener.subJob = subJob
        fileHelper.initOutputTempFile(OUTPUT_FORMAT)
        val attributes = initAttributes()
        try {
            fileHelper.fetchOriginalTempFile(cacheObject)
            val multiMediaObject = MultimediaObject(fileHelper.originalFile)
            encoder.encode(multiMediaObject, fileHelper.outputFile, attributes, listener)
            val outputCacheObject = cacheObject.deepCopy()
            outputCacheObject.quality = bitrate.toInt()
            outputCacheObject.size = fileHelper.outputFile.length()
            outputCacheObject.mimeType = MIME_TYPE
            fileHelper.uploadToCache(outputCacheObject)
        } catch (exception: Exception) {
            throw exception
        } finally {
            fileHelper.cleanup()
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