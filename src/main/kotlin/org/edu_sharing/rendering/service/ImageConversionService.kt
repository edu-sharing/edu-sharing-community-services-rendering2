package org.edu_sharing.rendering.service

import org.edu_sharing.rendering.blobStorage.StorageService
import org.edu_sharing.rendering.dto.CacheObject
import org.edu_sharing.rendering.entity.RenderingJob
import org.springframework.stereotype.Service
import java.awt.Image
import javax.imageio.ImageIO

/**
 * ImageConversionService
 *
 * What information does it need from job queue?
 * - Location of original tempfile
 * - targetLocation (e.g. image/objId/hash_resolution.jpg)
 * - parent (copy) job id
 *
 * Sequence:
 * - create job, register as sub job with parent job
 * - write tempfile to memory
 * - for every res:
 *      convert
 *      store
 * - delete tempfile
 * - set job to complete
 */
@Service
class ImageConversionService (
    private val storageImplementation: StorageService,
){
    private lateinit var jobEntry: RenderingJob
    fun setRenderingJob(jobEntry: RenderingJob) {
        this.jobEntry = jobEntry
    }
    fun convert(cacheObject: CacheObject, size: Int) {
        val fileInputStream = storageImplementation.getObjectStream(cacheObject, true)
        val inputImage = ImageIO.read(fileInputStream)
        val originalHeight = inputImage.height
        val originalWidth = inputImage.width
        val ratio = originalWidth/originalHeight
        var targetWidth: Int
        var targetHeight: Int
        this.jobEntry.subJobs.forEach {
            if (ratio < 1) {
                targetHeight = it.quality
                targetWidth = it.quality * ratio
            } else {
                targetHeight = it.quality
                targetWidth = it.quality * ratio
            }
        }



        val outputImage = inputImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT)
        // stream image to storage
    }
}