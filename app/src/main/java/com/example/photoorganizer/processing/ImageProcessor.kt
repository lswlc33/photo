package com.example.photoorganizer.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.scale
import com.example.photoorganizer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Output container for a re-encoded still image. */
enum class ImageFormat(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    PNG("image/png", "png"),
}

/** Optional long-edge cap applied before re-encoding. */
enum class ImageResizeOption(val longEdgePx: Int?) {
    ORIGINAL(null),
    LONG_EDGE_3840(3840),
    LONG_EDGE_2560(2560),
    LONG_EDGE_1920(1920),
    LONG_EDGE_1280(1280),
}

/**
 * Platform-only still-image processing. Decoding, optional downscaling and
 * re-encoding all run through [Bitmap], so this works on every supported ABI
 * without any native dependency.
 */
object ImageProcessor {

    /**
     * Re-encodes [source] into [format] with [quality] (0-100). Rotation stored
     * in Exif is baked into the pixels so viewers see the same orientation.
     * Returns the published MediaStore item, never touching the original.
     */
    suspend fun reencode(
        context: Context,
        source: Uri,
        format: ImageFormat,
        quality: Int,
        resize: ImageResizeOption,
        stripMetadata: Boolean,
        onProgress: (Float) -> Unit = {},
    ): ProcessedMedia = withContext(Dispatchers.IO) {
        val originalBytes = GalleryWriter.sourceSize(context, source)
        val input = GalleryWriter.copyToCache(context, source, "img_in")
        val output = GalleryWriter.cacheFile(context, "img", format.extension)
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        try {
            onProgress(.15f)
            val decodedBitmap = decodeScaled(input, resize.longEdgePx)
                ?: throw ProcessingException(R.string.processing_error_decode_image)
            decoded = decodedBitmap
            currentCoroutineContext().ensureActive()
            onProgress(.45f)
            val orientedBitmap = applyExifRotation(input, decodedBitmap)
            oriented = orientedBitmap
            currentCoroutineContext().ensureActive()
            onProgress(.6f)
            output.outputStream().use { stream ->
                val ok = orientedBitmap.compress(format.toCompressFormat(), quality.coerceIn(1, 100), stream)
                if (!ok) {
                    throw ProcessingException(
                        R.string.processing_error_encode_image,
                        listOf(format.name),
                    )
                }
            }
            currentCoroutineContext().ensureActive()
            onProgress(.8f)
            if (!stripMetadata && format == ImageFormat.JPEG) {
                copyExif(input, output)
            }
            val outputBytes = output.length()
            val uri = GalleryWriter.publishImage(context, output, format.mimeType)
            onProgress(1f)
            ProcessedMedia(
                uri = uri,
                displayName = output.name,
                originalBytes = originalBytes,
                outputBytes = outputBytes,
            )
        } finally {
            oriented?.takeIf { it !== decoded && !it.isRecycled }?.recycle()
            decoded?.takeIf { !it.isRecycled }?.recycle()
            input.delete()
            output.delete()
        }
    }

    /** Decodes with an integer sample size so large photos never blow up memory. */
    private fun decodeScaled(file: File, longEdgePx: Int?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sourceLongEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (sourceLongEdge <= 0) return null

        val options = BitmapFactory.Options()
        val sample = if (longEdgePx == null) {
            if (!originalSizeIsSupported(bounds.outWidth, bounds.outHeight)) {
                throw ProcessingException(
                    R.string.processing_error_original_too_large,
                    listOf(bounds.outWidth, bounds.outHeight),
                )
            }
            1
        } else {
            resizeDecodeSampleSize(sourceLongEdge, longEdgePx)
        }
        options.inSampleSize = sample
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        if (longEdgePx == null) return decoded

        val decodedLongEdge = maxOf(decoded.width, decoded.height)
        if (decodedLongEdge <= longEdgePx) return decoded
        val scale = longEdgePx.toFloat() / decodedLongEdge
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = decoded.scale(width, height)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    /** Carries date/camera tags over while dropping the now-baked rotation. */
    private fun copyExif(source: File, target: File) {
        try {
            val from = ExifInterface(source)
            val to = ExifInterface(target)
            PRESERVED_EXIF_TAGS.forEach { tag ->
                from.getAttribute(tag)?.let { value -> to.setAttribute(tag, value) }
            }
            to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            to.saveAttributes()
        } catch (failure: Throwable) {
            throw ProcessingException(R.string.processing_error_copy_metadata, cause = failure)
        }
    }

    private fun ImageFormat.toCompressFormat(): Bitmap.CompressFormat = when (this) {
        ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
        ImageFormat.PNG -> Bitmap.CompressFormat.PNG
    }

    private val PRESERVED_EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
    )
}
