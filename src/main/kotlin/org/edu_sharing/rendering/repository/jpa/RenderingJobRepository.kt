package org.edu_sharing.rendering.repository.jpa

import org.edu_sharing.rendering.entity.RenderingJob
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface RenderingJobRepository: CrudRepository<RenderingJob, String>