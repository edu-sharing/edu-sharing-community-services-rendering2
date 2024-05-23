package org.edu_sharing.rendering.processing.av

import org.edu_sharing.rendering.config.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import ws.schild.jave.info.MultimediaInfo
import ws.schild.jave.progress.EncoderProgressListener

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ConditionalOnConverter
class AVConversionListener(
    private val subJobRepository: SubJobRepository
) : EncoderProgressListener {

    lateinit var subJob: SubJob
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun sourceInfo(p0: MultimediaInfo?) {
        // This is executed when the mm object was analyzed
        return
    }

    override fun progress(p0: Int) {
        if (p0 % 10 == 0 && ::subJob.isInitialized) {
            subJob.progress = p0 / 10
            subJobRepository.save(subJob)
        }
    }

    override fun message(p0: String?) {
        logger.warn(p0)
    }
}