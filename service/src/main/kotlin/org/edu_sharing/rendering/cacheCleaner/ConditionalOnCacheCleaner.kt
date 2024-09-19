package org.edu_sharing.rendering.cacheCleaner

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression

@ConditionalOnExpression("T(org.springframework.util.StringUtils).isEmpty('\${app.roles:}') || T(java.util.Arrays).asList('\${app.roles:}').contains('cache-cleaner')")
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnCacheCleaner()
