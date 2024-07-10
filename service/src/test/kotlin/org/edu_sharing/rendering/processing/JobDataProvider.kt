package org.edu_sharing.rendering.processing

import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.RenderModules
import org.edu_sharing.rendering.entity.JobStatus
import org.edu_sharing.rendering.entity.RenderingJob
import org.edu_sharing.rendering.entity.SubJob

class JobDataProvider {
    companion object {
        const val DUMMY_JOB_ID = "507f191e810c19729de860eb"
        const val DUMMY_CREATION_TS: Long = 1716276009324
        const val HASH = "hash"
        const val ES_OBJECT_ID = "esobjectid"
        const val SUB_ID_1 = "507f191e810c19729de860ec"
        const val SUB_ID_2 = "507f191e810c19729de860ed"
    }

    fun getJobWithoutSubJobs(module: RenderModules = RenderModules.EDUHTML): RenderingJob {
        return RenderingJob(
            id = ObjectId(DUMMY_JOB_ID),
            esHash = "hash",
            esObjectId = ES_OBJECT_ID,
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = module,
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.PROCESSING
        )
    }

    fun getDummySubJob(
        subId: String,
        status: JobStatus,
        module: RenderModules,
        mimeType: String,
        quality: Int = 0
    ): SubJob {
        // Sub jobs need a fake parent
        val dummy = RenderingJob(
            id = ObjectId(DUMMY_JOB_ID),
            esHash = "hash",
            esObjectId = ES_OBJECT_ID,
            esObjectType = "esobjecttype",
            mimeType = mimeType,
            module = module,
            nodeVersion = "1.2",
            repoId = "repoid",
            status = JobStatus.PROCESSING,
            creationTimestamp = DUMMY_CREATION_TS
        )
        val subJob = SubJob(
            id = ObjectId(subId),
            parent = dummy,
            routingKey = "whatever",
            status = status,
            quality = quality
        )
        return subJob
    }
}