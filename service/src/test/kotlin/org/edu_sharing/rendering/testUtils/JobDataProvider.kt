package org.edu_sharing.rendering.testUtils

import org.bson.types.ObjectId
import org.edu_sharing.rendering.renderingJob.entity.RenderingJob
import org.edu_sharing.rendering.renderingJob.entity.RenderingJobStatus
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.edu_sharing.rendering.renderingJob.entity.SubJobStatus

class JobDataProvider {
    companion object {
        const val DUMMY_JOB_ID = "507f191e810c19729de860eb"
        const val DUMMY_JOB_ID_2 = "507f191e810c19729de860ec"
        const val DUMMY_CREATION_TS: Long = 1716276009324
        const val HASH = "hash"
        const val ES_OBJECT_ID = "esobjectid"
        const val SUB_ID_1 = "507f191e810c19729de860ea"
        const val SUB_ID_2 = "507f191e810c19729de860ed"
        const val SUB_ID_3 = "507f191e810c19729de860ee"
        const val SUB_ID_4 = "507f191e810c19729de860ef"
    }

    fun getJobWithoutSubJobs(
        module: String = "HTML",
        id: ObjectId = ObjectId(DUMMY_JOB_ID)
    ): RenderingJob {
        return RenderingJob(
            id = id,
            esHash = "hash",
            esObjectId = ES_OBJECT_ID,
            esObjectType = "esobjecttype",
            mimeType = "image/jpeg",
            module = module,
            nodeVersion = "1.2",
            repoId = "repoid",
            status = RenderingJobStatus.PROCESSING
        )
    }

    fun getDummySubJob(
        subId: String,
        status: SubJobStatus,
        module: String,
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
            status = RenderingJobStatus.PROCESSING,
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

    fun prepareJobForConversionModuleTesting(id: String, module: String = "VIDEO"): RenderingJob {
        val job = RenderingJob(
            id = ObjectId(id),
            esHash = "hash",
            esObjectId = "esobjectid",
            esObjectType = "esobjecttype",
            mimeType = "multipart/form-data",
            module = module,
            nodeVersion = "1.2",
            repoId = "repoid",
            status = RenderingJobStatus.QUEUED
        )
        return job
    }
}