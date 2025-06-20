package org.edu_sharing.rendering.storage.minio

import io.minio.messages.Event
import io.minio.messages.EventType
import io.minio.messages.NotificationRecords
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.storage.minio.bucket.BucketStrategy
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.net.URLDecoder

@Component
class ObjectDeletionListener(
    private val trackingService: TrackingService,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val bucketStrategy: BucketStrategy,
) {

    // language=json
    val eventExample = """
            {
              "EventName" : "s3:ObjectRemoved:Delete",
              "Key" : "repository/upload/1234/thumbnail.png",
              "Records" : [ {
                "eventVersion" : "2.0",
                "eventSource" : "minio:s3",
                "awsRegion" : "",
                "eventTime" : "2025-01-08T08:42:34.116Z",
                "eventName" : "s3:ObjectRemoved:Delete",
                "userIdentity" : {
                  "principalId" : "admin"
                },
                "requestParameters" : {
                  "principalId" : "admin",
                  "region" : "",
                  "sourceIPAddress" : "172.18.0.1"
                },
                "responseElements" : {
                  "content-length" : "230",
                  "x-amz-id-2" : "dd9025bab4ad464b049177c95eb6ebf374d3b3fd1af9251148b658df7ac2e3e8",
                  "x-amz-request-id" : "1818AB12EC5DB03B",
                  "x-minio-deployment-id" : "b431d7b2-1432-4b08-a3ea-d7fd2074d53c",
                  "x-minio-origin-endpoint" : "http://localhost:9000"
                },
                "s3" : {
                  "s3SchemaVersion" : "1.0",
                  "configurationId" : "Config",
                  "bucket" : {
                    "name" : "repository",
                    "ownerIdentity" : {
                      "principalId" : "admin"
                    },
                    "arn" : "arn:aws:s3:::repository"
                  },
                  "object" : {
                    "key" : "upload%2F1234%2Fthumbnail.png",
                    "versionId" : "b993e2c6-7dbc-4731-b8ab-5f954b8c4d1f",
                    "sequencer" : "1818AB12EC7C60D9"
                  }
                },
                "source" : {
                  "host" : "172.18.0.1",
                  "port" : "",
                  "userAgent" : "MinIO (linux; amd64) minio-go/v7.0.77 MinIO Console/(dev)"
                }
              } ]
            }
            """

    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(name = "\${app.queue.minio.deletion.name}", durable = "true"),
                exchange = Exchange(name = "minio-events", type = "topic"),
                key = ["\${app.queue.minio.deletion.key}"]
            )
        ]
    )
    fun handleObjectDeletion(records: NotificationRecords) {
        records.events().forEach { event ->
            log.info("Received MinIO deletion event: {}", event)
            if (event.eventType() != EventType.OBJECT_REMOVED_DELETE) {
                log.warn("Received unexpected event type: {}", event.eventType())
                return
            }
            processObjectDeletion(event)
        }
    }

    private fun processObjectDeletion(event: Event) {
        try {
            val bucketName = event.bucketName()
            val objectKey = URLDecoder.decode(event.objectName(), Charsets.UTF_8)
            
            log.info("Processing object deletion: bucket={}, key={}", bucketName, objectKey)

            val cacheObject = parseCacheObjectFromPath(bucketName, objectKey)
            
            if (cacheObject != null) {
                trackingService.deleteTrackedObject(cacheObject, bucketName)
                log.info("Removed tracking for deleted object: {}/{}", bucketName, objectKey)
            } else {
                log.debug("Could not parse cache object from path: {}/{}", bucketName, objectKey)
            }

        } catch (exception: Exception) {
            log.error("Error processing object deletion.", exception)
        }

    }

    private fun parseCacheObjectFromPath(bucketName: String, objectKey: String): CacheObject? {
        return try {
            bucketStrategy.getCacheObjectFromStoragePath(bucketName, objectKey)
        } catch (exception: Exception) {
            log.debug("Failed to parse cache object from path {}/{}: {}", bucketName, objectKey, exception.message)
            null
        }
    }
}

