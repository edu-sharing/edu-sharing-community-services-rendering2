package org.edu_sharing.rendering.utils

import org.edu_sharing.generated.repository.backend.services.rest.client.model.Node

const val COLLECTION_REFERENCE_ASPECT = "ccm:collection_io_reference"

/**
 * The id of the original node a collection reference points to, or null if this node is
 * not a collection reference. The generated Node model has no originalId field, so the
 * id is derived from the ccm:original (fallback cm:original) property.
 */
fun Node.collectionRefOriginalId(): String? {
    if (aspects?.contains(COLLECTION_REFERENCE_ASPECT) != true) return null
    return (properties?.get("ccm:original") ?: properties?.get("cm:original"))
        ?.firstOrNull()?.takeIf { it.isNotBlank() && it != ref?.id }
}

/**
 * The id everything persistent (jobs, S3 keys, tracking entries, lumi content, repo
 * content fetch) is keyed by: the original node's id for collection references, so
 * references share the original's cache and leave no traces of their own id.
 * Session state (node/permission lookup) stays keyed by [Node.getRef].id.
 */
fun Node.storageNodeId(): String = collectionRefOriginalId() ?: ref.id
