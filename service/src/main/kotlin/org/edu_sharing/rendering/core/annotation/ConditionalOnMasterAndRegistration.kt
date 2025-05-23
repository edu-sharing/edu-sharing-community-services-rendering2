package org.edu_sharing.rendering.core.annotation

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression

@ConditionalOnExpression("\${app.repository.registration.enabled:false} == true " +
        "and " +
        "T(java.util.Arrays).asList('\${app.roles:}'.split(',')).contains('master')")
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnMasterAndRegistration()
