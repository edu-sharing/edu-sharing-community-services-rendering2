package org.edu_sharing.rendering.processing.av

import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.slf4j.LoggerFactory
import ws.schild.jave.info.MultimediaInfo
import ws.schild.jave.progress.EncoderProgressListener

class AVConversionListener(
    private val subJob: SubJob,
    private val subJobRepository: SubJobRepository
    ): EncoderProgressListener {


    private val logger = LoggerFactory.getLogger(javaClass)
    override fun sourceInfo(p0: MultimediaInfo?) {
        // This is executed when the mm object was analyzed
        return
    }

    override fun progress(p0: Int) {
        if (p0%10 == 0) {
            subJob.progress = p0/10
            subJobRepository.save(subJob)
        }
    }

    override fun message(p0: String?) {
        logger.warn(p0)
    }
}