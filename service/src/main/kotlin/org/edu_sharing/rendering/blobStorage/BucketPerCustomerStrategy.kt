package org.edu_sharing.rendering.blobStorage

import org.edu_sharing.rendering.config.annotation.ConditionalOnStorageByCustomer
import org.springframework.stereotype.Component

@Component
@ConditionalOnStorageByCustomer
class BucketPerCustomerStrategy : BucketStrategy {
}
