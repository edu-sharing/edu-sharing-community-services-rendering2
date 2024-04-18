package org.edu_sharing.rendering.config.annotation

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression

@ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('\${app.moodle.host:}')")
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnMoodle()
