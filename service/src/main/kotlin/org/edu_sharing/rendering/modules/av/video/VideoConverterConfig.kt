package org.edu_sharing.rendering.modules.av.video

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.converter.video")
class VideoConverterConfig {

    /**
     * Target heights a video is converted to, e.g. `480,720,1080` - the comma-separated value Helm
     * (`config.converter.video.resolutions`) and compose deliver.
     */
    var resolutions: List<Int> = emptyList()

    /**
     * RabbitMQ message priority of a height's sub-job (higher is converted first); a height without an entry gets
     * priority 0. The AV queue is a quorum queue, which orders strictly by the message `priority` over 0-31 and needs no
     * queue argument. Every AV message must carry an explicit priority: on a quorum queue a message without one counts
     * as 4 and would overtake all heights configured below that ([PriorityPostProcessor] always sets it).
     */
    var priorities: Map<Int, Int> = emptyMap()

    @PostConstruct
    fun validate() {
        require(resolutions.isNotEmpty() && resolutions.all { it > 0 }) {
            "app.converter.video.resolutions must list at least one positive height, was $resolutions"
        }
    }

    fun getPriority(resolution: Int, default: Int) = priorities[resolution] ?: default
    fun isEmpty(): Boolean = resolutions.isEmpty()
    fun getMaxResolution() = resolutions.max()
    fun getMinResolution() = resolutions.min()
    fun getPossibleResolutions(originalHeight: Int?): List<Int> {
        if (originalHeight != null && originalHeight < getMinResolution()) {
            return listOf(originalHeight)
        }
        return resolutions.distinct().sorted().filter { it <= (originalHeight ?: Int.MAX_VALUE) }
    }
}
