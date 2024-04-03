package org.edu_sharing.rendering.repository.redis

import org.edu_sharing.rendering.entity.MoodleCourse
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MoodleCourseRepository: CrudRepository<MoodleCourse, String> {
}