package org.edu_sharing.rendering.repository.jpa

import org.edu_sharing.rendering.entity.RenderingJob
import org.springframework.data.jpa.repository.JpaRepository

interface RenderingJobRepository: JpaRepository<RenderingJob, Long> {
    fun findAllByHash(hash: String): List<RenderingJob>
    fun findAllByStatus(status: String): List<RenderingJob>
}