package org.edu_sharing.rendering.service

import org.bson.types.ObjectId
import org.edu_sharing.rendering.dto.TestResponse
import org.edu_sharing.rendering.repository.mongo.RenderingJobRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class TestService(
    private val mongoRepo: RenderingJobRepository
    ) {
    fun getMessage(): TestResponse {
        /*+
        println("storing in mongo")
        val myJob = RenderingJob(
            esObjectType = "image",
            esHash = "testHash123",
            esObjectId = "esObj123",
            extension = "jpeg",
            origin = "somePath",
            mimeType = "image/jpeg"
        )
        val subJobOne = SubJob(quality = 400)
        myJob.subJobs.add(subJobOne)
        val subJobTwo = SubJob(quality = 800)
        myJob.subJobs.add(subJobTwo)
        mongoRepo.save(myJob)
         */
        val result = mongoRepo.findByIdOrNull(ObjectId("65d88c3642e7480a405d8efb"))

        return if (result != null) {
            TestResponse("worked", result.status.toString())
        } else {
            TestResponse("not", "working")
        }
    }
}