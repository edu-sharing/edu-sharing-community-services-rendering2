package org.edu_sharing.rendering.modules.h5p.lumi

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LumiCacheRepository: CrudRepository<LumiNodeInfo, String> {
    fun findByNodeIdAndHash(nodeId: String, hash: String): LumiNodeInfo?
}