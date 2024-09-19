package org.edu_sharing.rendering.controller.external

import org.edu_sharing.rendering.blobStorage.minio.MinioAdminClientProvider
import org.edu_sharing.rendering.cacheCleaner.TrackingEntryRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public/test")
class TestController(
    private val repository: TrackingEntryRepository,
    private val mongoTemplate: MongoTemplate,
    private val minioAdminClient: MinioAdminClientProvider
) {
    @GetMapping
    fun test() {

        val test = minioAdminClient.adminClient.getBucketQuota("image")
        val test2 = minioAdminClient.adminClient.dataUsageInfo


        /**
        val entries = listOf(
            TrackingEntry(
                size = 1,
                lastAccessed = Date(190000),
                storagePath = "test1"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(160000),
                storagePath = "test2"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(140000),
                storagePath = "test3"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(120000),
                storagePath = "test4"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(100000),
                storagePath = "test5"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(110000),
                storagePath = "test6"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(130000),
                storagePath = "test7"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(150000),
                storagePath = "test8"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(170000),
                storagePath = "test9"
            ),
            TrackingEntry(
                size = 1,
                lastAccessed = Date(180000),
                storagePath = "test10"
            )
        )
        repository.deleteAll()
        repository.saveAll(entries)

        val toDelete = mutableListOf<TrackingEntry>()
        var totalSize = 0L

        val maxSize = 5
        val page = 0
        var result: Page<TrackingEntry>? = null
        do {
            result = repository.findAll(PageRequest.of(page, 100, Sort.Direction.ASC, "lastAccessed"))
            for (entry in result) {
                totalSize += entry.size
                if(totalSize > maxSize){
                    break
                }
                toDelete.add(entry)
            }

        }while (result?.hasNext() == true && totalSize < maxSize)
        repository.deleteAll(toDelete)
        var count = repository.count()
        **/

    }


}
