package org.edu_sharing.rendering.storage

/**
 * A storage area a [StorageManager] manages for a repository.
 *
 * [name] identifies the scope inside the service (tracking `bucket`, owner check, quota key); [bucket]
 * and [keyPrefix] are the physical S3 location, used only for S3 calls. [keyPrefix] is normalized
 * (`""` or `a/b/`). For a scope without a prefix, [name] equals [bucket].
 */
data class ManagedScope(val name: String, val bucket: String, val keyPrefix: String)
