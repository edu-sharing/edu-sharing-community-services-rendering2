package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.repository.SubJobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import ws.schild.jave.info.MultimediaInfo
import ws.schild.jave.progress.EncoderProgressListener

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ConditionalOnAvConverter
class AvConversionListener(
    private val subJobRepository: SubJobRepository
) : EncoderProgressListener {

    lateinit var subJob: SubJob
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sourceInfo(p0: MultimediaInfo?) {
        log.info(p0.toString())
    }

    override fun progress(p0: Int) {
        if (p0 % 10 == 0 && ::subJob.isInitialized) {
            subJob.progress = p0 / 10
            subJob = subJobRepository.save(subJob)
        }
    }

    override fun message(p0: String?) {
        log.info(p0)
    }
}
