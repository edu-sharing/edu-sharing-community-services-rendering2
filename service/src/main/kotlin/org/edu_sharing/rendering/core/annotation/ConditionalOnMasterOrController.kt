package org.edu_sharing.rendering.core.annotation

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression

@ConditionalOnExpression("T(java.util.Arrays).asList('\${app.roles:}'.split(',')).contains('master')" +
" || T(java.util.Arrays).asList('\${app.roles:}'.split(',')).contains('controller')" +
" || T(org.springframework.util.StringUtils).isEmpty('\${app.roles:}')")
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnMasterOrController()
