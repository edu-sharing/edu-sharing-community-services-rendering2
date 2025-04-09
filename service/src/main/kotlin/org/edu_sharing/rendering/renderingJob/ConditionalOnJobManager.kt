package org.edu_sharing.rendering.renderingJob

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression

@ConditionalOnExpression("T(org.springframework.util.StringUtils).isEmpty('\${app.roles:}') || T(java.util.Arrays).asList('\${app.roles:}'.split(',')).contains('job-manager')")
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnJobManager()
