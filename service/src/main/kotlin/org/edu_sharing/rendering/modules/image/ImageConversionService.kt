package org.edu_sharing.rendering.modules.image

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifIFD0Directory
import org.edu_sharing.rendering.core.annotation.ConditionalOnConverter
import org.edu_sharing.rendering.core.dto.CacheObject
import org.edu_sharing.rendering.core.exception.ConversionException
import org.edu_sharing.rendering.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * ImageConversionService
 */
@ConditionalOnConverter
@Service
class ImageConversionService (
    private val storageImplementation: StorageService,
){
    private val log = LoggerFactory.getLogger(javaClass)
    @Value($$"${app.converter.image.format}")
    lateinit var imageFormat: String
    @Value($$"${app.converter.image.maxPixels}")
    var maxPixels: Long = 0

    init {
        // JVM-global: keep ImageIO from spilling decode buffers to a tmpdir disk cache
        // (often a memory-backed tmpfs in containers); all decoding here is in-memory anyway.
        ImageIO.setUseCache(false)
    }

    companion object {
        private const val EXIF_ORIENTATION_NORMAL = 1

        /**
         * Subsampling factor so the decoded image keeps `max(width, height)` at roughly twice
         * [targetSize] — enough headroom for a sharp single-pass bilinear downscale while
         * bounding the decoded buffer near the target instead of the native resolution.
         */
        fun subsamplingFactor(sourceMaxDimension: Int, targetSize: Int): Int =
            if (targetSize <= 0) 1 else maxOf(1, sourceMaxDimension / (2 * targetSize))
    }

    /** Exposes the internal buffer so the encoded image is not copied again by `toByteArray()`. */
    private class ExposedByteArrayOutputStream : ByteArrayOutputStream() {
        fun toInputStream() = ByteArrayInputStream(buf, 0, count)
    }

    fun convert(cacheObject: CacheObject, size: Int, sourceImage: BufferedImage) {
        log.debug("Converting image: nodeId=${cacheObject.nodeId}, targetSize=$size, format=$imageFormat")
        val originalHeight = sourceImage.height
        val originalWidth = sourceImage.width
        val ratio = originalWidth.toFloat()/originalHeight
        val targetWidth = if (ratio > 1) size else (size * ratio).toInt()
        val targetHeight = if (ratio > 1) (size / ratio).toInt() else size
        log.debug("Computed target dimensions: ${targetWidth}x${targetHeight} from original ${originalWidth}x${originalHeight}")
        val bufferedOutputImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = bufferedOutputImage.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        val byteArrayOutputStream = ExposedByteArrayOutputStream()
        ImageIO.write(bufferedOutputImage, imageFormat, byteArrayOutputStream)
        cacheObject.quality = size
        cacheObject.size = byteArrayOutputStream.size().toLong()
        cacheObject.mimeType = "image/${imageFormat}"
        val metadata =  mapOf(
            "height" to targetHeight.toString(),
            "width" to targetWidth.toString()
        )
        // Size known from conversion
        log.debug("Storing converted image: nodeId=${cacheObject.nodeId}, quality=$size, size=${byteArrayOutputStream.size()} bytes")
        storageImplementation.putObject(
            cacheObject = cacheObject,
            inputStream = byteArrayOutputStream.toInputStream(),
            metadata = metadata
        )
    }

    fun fetchSourceImage(cacheObject: CacheObject, targetSize: Int): BufferedImage {
        log.debug("Fetching source image from storage: nodeId=${cacheObject.nodeId}, mimeType=${cacheObject.mimeType}")
        // Read the whole compressed source into memory: the decoder discards EXIF metadata, so we
        // need the raw bytes both to decode the image and to read its EXIF orientation independently.
        val bytes = storageImplementation.getObjectStream(cacheObject, true).use { it.readBytes() }
        val orientation = readExifOrientation(bytes)
        log.debug("EXIF orientation $orientation for nodeId=${cacheObject.nodeId}")
        val sourceImage = decodeSubsampled(bytes, targetSize)
        return applyExifOrientation(sourceImage, orientation)
    }

    /**
     * Decodes the image with source subsampling so the decoded buffer is bounded near
     * [targetSize] (the largest requested rendition) instead of the source's native resolution.
     * Rejects images whose header dimensions exceed `app.converter.image.maxPixels` before any
     * pixel data is decoded.
     */
    private fun decodeSubsampled(bytes: ByteArray, targetSize: Int): BufferedImage {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                throw ConversionException("No ImageIO reader found for source image")
            }
            val reader = readers.next()
            try {
                // EXIF is read separately via metadata-extractor, so image metadata can be ignored.
                reader.setInput(input, true, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val pixels = width.toLong() * height
                if (pixels > maxPixels) {
                    throw ConversionException(
                        "Image too large: ${width}x${height} ($pixels px) exceeds app.converter.image.maxPixels=$maxPixels"
                    )
                }
                val factor = subsamplingFactor(maxOf(width, height), targetSize)
                log.debug("Decoding ${width}x${height} source with subsampling factor $factor for targetSize=$targetSize")
                val param = reader.defaultReadParam
                param.setSourceSubsampling(factor, factor, 0, 0)
                return reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }
    }

    /**
     * Reads the EXIF `Orientation` tag (IFD0) from the raw image bytes. Returns the normal
     * orientation (`1`) when the tag is absent or the metadata cannot be parsed (e.g. PNG, or a
     * format without EXIF) — a missing/broken EXIF block must never fail the conversion.
     */
    private fun readExifOrientation(bytes: ByteArray): Int {
        return try {
            val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(bytes))
            val directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            if (directory != null && directory.containsTag(ExifDirectoryBase.TAG_ORIENTATION)) {
                directory.getInt(ExifDirectoryBase.TAG_ORIENTATION)
            } else {
                EXIF_ORIENTATION_NORMAL
            }
        } catch (exception: Exception) {
            log.debug("Could not read EXIF orientation, defaulting to normal: ${exception.message}")
            EXIF_ORIENTATION_NORMAL
        }
    }

    /**
     * Returns [image] rotated/flipped so it displays upright, applying the standard EXIF
     * orientation correction. `javax.imageio` does not auto-apply EXIF orientation, so portrait
     * photos from phones (orientation 6/8) would otherwise render sideways. Replicates the old
     * PHP picture module's rotations (3→180°, 6→90° clockwise, 8→90° counter-clockwise) and
     * additionally handles the mirrored orientations (2/4/5/7). For 90°/270° cases the returned
     * image has width and height swapped. Orientation `1` (and any unknown value) returns the
     * image unchanged.
     */
    fun applyExifOrientation(image: BufferedImage, orientation: Int): BufferedImage {
        if (orientation == EXIF_ORIENTATION_NORMAL) {
            return image
        }
        val width = image.width
        val height = image.height
        val transform = AffineTransform()
        val swapsDimensions: Boolean
        when (orientation) {
            2 -> { // flip horizontal
                transform.scale(-1.0, 1.0)
                transform.translate(-width.toDouble(), 0.0)
                swapsDimensions = false
            }
            3 -> { // rotate 180°
                transform.translate(width.toDouble(), height.toDouble())
                transform.rotate(Math.PI)
                swapsDimensions = false
            }
            4 -> { // flip vertical
                transform.scale(1.0, -1.0)
                transform.translate(0.0, -height.toDouble())
                swapsDimensions = false
            }
            5 -> { // transpose: flip horizontal + rotate 270° clockwise
                transform.rotate(-Math.PI / 2)
                transform.scale(-1.0, 1.0)
                swapsDimensions = true
            }
            6 -> { // rotate 90° clockwise
                transform.translate(height.toDouble(), 0.0)
                transform.rotate(Math.PI / 2)
                swapsDimensions = true
            }
            7 -> { // transverse: flip horizontal + rotate 90° clockwise
                transform.scale(-1.0, 1.0)
                transform.translate(-height.toDouble(), width.toDouble())
                transform.rotate(3 * Math.PI / 2)
                swapsDimensions = true
            }
            8 -> { // rotate 90° counter-clockwise
                transform.translate(0.0, width.toDouble())
                transform.rotate(3 * Math.PI / 2)
                swapsDimensions = true
            }
            else -> return image
        }
        val targetWidth = if (swapsDimensions) height else width
        val targetHeight = if (swapsDimensions) width else height
        val outputType = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val rotated = BufferedImage(targetWidth, targetHeight, outputType)
        val graphics = rotated.createGraphics()
        graphics.transform = transform
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return rotated
    }

    fun deleteTempFile(cacheObject: CacheObject) {
        storageImplementation.removeTempObject(cacheObject)
    }
}
