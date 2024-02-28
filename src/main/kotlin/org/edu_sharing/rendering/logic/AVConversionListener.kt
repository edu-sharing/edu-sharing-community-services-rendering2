package org.edu_sharing.rendering.logic

import org.edu_sharing.rendering.entity.SubJob
import org.edu_sharing.rendering.repository.mongo.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import ws.schild.jave.info.MultimediaInfo
import ws.schild.jave.progress.EncoderProgressListener

class AVConversionListener(
    @Autowired private val subJobRepository: SubJobRepository
): EncoderProgressListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    private lateinit var subJob: SubJob

    override fun sourceInfo(p0: MultimediaInfo?) {
        // This is executed when the mm object was analyzed
        return
    }

    override fun progress(p0: Int) {
        if (this::subJob.isInitialized && p0%10 == 0) {
            subJob.progress = p0/10
            subJobRepository.save(subJob)
        }
        // This is called everytime progress changes (0-1000)
    }

    override fun message(p0: String?) {
        logger.warn(p0)
    }
}