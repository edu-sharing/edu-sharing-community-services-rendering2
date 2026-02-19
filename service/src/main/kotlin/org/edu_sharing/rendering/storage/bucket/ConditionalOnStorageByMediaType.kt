package org.edu_sharing.rendering.storage.bucket

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@ConditionalOnProperty(name=["app.storage.s3.bucket.mode"], havingValue = "byType", matchIfMissing = true)
@Target(allowedTargets = [AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS])
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnStorageByMediaType()
