package org.edu_sharing.rendering.blobStorage

import org.edu_sharing.rendering.config.annotation.ConditionalOnStorageByMediaType
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByMediaType
class BucketPerMediaTypeStrategy : BucketStrategy {

}
