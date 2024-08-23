package org.edu_sharing.rendering.config.annotation

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@ConditionalOnProperty(name=["app.storage.minio.bucket.mode"], havingValue = "byCustomer")
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnStorageByCustomer()
