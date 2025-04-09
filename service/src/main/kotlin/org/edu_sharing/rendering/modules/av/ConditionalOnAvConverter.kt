package org.edu_sharing.rendering.modules.av

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression

@ConditionalOnExpression("T(org.springframework.util.StringUtils).isEmpty('\${app.roles:}') || T(java.util.Arrays).asList('\${app.roles:}'.split(',')).contains('avconverter')")
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnAvConverter ()
