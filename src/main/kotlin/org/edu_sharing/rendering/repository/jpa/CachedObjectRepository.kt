package org.edu_sharing.rendering.repository.jpa

import org.edu_sharing.rendering.entity.CachedObject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CachedObjectRepository: JpaRepository<CachedObject, Long> {
    fun findAllByHash(hash: String): List<CachedObject>
}