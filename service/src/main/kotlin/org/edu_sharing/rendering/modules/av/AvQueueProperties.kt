package org.edu_sharing.rendering.modules.av

import org.edu_sharing.rendering.modules.av.video.VideoConverterConfig
import org.edu_sharing.rendering.renderingJob.queue.StandardQueueProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Routing + scaling for the audio/video queue (`app.queue.av.*`). Consumed by [AvReceiver]. */
@Component
@ConfigurationProperties("app.queue.av")
class AvQueueProperties : StandardQueueProperties() {

    // Field injection, not a constructor param: this class is bound as a plain (property-setter) Spring
    // Boot @ConfigurationProperties bean, and a constructor parameter here would make Boot try to bind
    // VideoConverterConfig itself from `app.queue.av.*` instead of autowiring it.
    @Autowired
    private lateinit var videoConverterConfig: VideoConverterConfig

    /** Must match the `x-max-priority` argument [AvReceiver] declares on its `@RabbitListener`. */
    override val declareArguments: Map<String, Any>
        get() = videoConverterConfig.getMaxPriority()?.let { mapOf("x-max-priority" to it) } ?: emptyMap()
}
