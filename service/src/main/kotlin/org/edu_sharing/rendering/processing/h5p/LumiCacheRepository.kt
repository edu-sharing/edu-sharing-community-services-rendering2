package org.edu_sharing.rendering.processing.h5p

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LumiCacheRepository: CrudRepository<LumiNodeInfo, String> {}