package org.edu_sharing.rendering.modules.av.video

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.converter.video")
class VideoConverterConfig {

    lateinit var resolutions: Map<String, VideoResolutionItemConfig>

    fun getResolutions() = resolutions.keys.map { it.toInt() }
    fun getMaxPriority() = resolutions.values.maxOfOrNull { it.priority }
    fun getPriority(resolution: Int, default: Int) = resolutions[resolution.toString()]?.priority ?: default
    fun isEmpty(): Boolean = resolutions.isEmpty()
    fun getMaxResolution() = getResolutions().max()
    fun getMinResolution() = getResolutions().min()
    fun getPossibleResolutions(originalHeight: Int?) = getResolutions().filter { it <= (originalHeight ?: Int.MAX_VALUE) }
}
