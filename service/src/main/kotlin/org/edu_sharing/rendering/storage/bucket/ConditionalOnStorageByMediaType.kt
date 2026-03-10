package org.edu_sharing.rendering.storage.bucket

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@ConditionalOnProperty(name=["app.s3.bucket.name"], havingValue = "byType", matchIfMissing = true)
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnStorageByMediaType()
