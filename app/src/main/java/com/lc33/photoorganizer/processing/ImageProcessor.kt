package com.lc33.photoorganizer.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.PendingMedia
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
     *
     * The result is left in the staging directory for the user to compare against
     * the source; nothing is written to the gallery here, and the source is only
     * ever read. Null when [keepOnlyIfSmaller] rejected the output.
     */
    suspend fun reencode(
        context: Context,
        source: PendingMedia,
        format: ImageFormat,
        quality: Int,
        resize: ImageResizeOption,
        stripMetadata: Boolean,
        keepOnlyIfSmaller: Boolean = true,
        onProgress: (Float) -> Unit = {},
    ): StagedMedia? = withContext(Dispatchers.IO) {
        val originalBytes = source.sizeBytes.takeIf { it > 0L }
            ?: GalleryWriter.sourceSize(context, source.uri)
        val input = GalleryWriter.copyToCache(context, source.uri, "img_in")
        val output = StagingArea.file(context, "img", format.extension)
        var decoded: Bitmap? = null
        var finished: Bitmap? = null
        // The output survives this call only when it is handed back as a staged
        // result; on a failure, a cancellation or a skip it is cache to reclaim.
        var staged = false
        try {
            onProgress(.15f)
            val decodedBitmap = decodeSampled(input, resize.longEdgePx)
                ?: throw ProcessingException(R.string.processing_error_decode_image)
            decoded = decodedBitmap
            currentCoroutineContext().ensureActive()
            onProgress(.45f)
            // One transform for the downscale and the Exif rotation together. Two
            // separate createBitmap calls meant three full-size bitmaps were alive at
            // the peak instead of two, which at the 3840 px option is the difference
            // between 92 MB and 136 MB.
            val finishedBitmap = transform(input, decodedBitmap, resize.longEdgePx)
            finished = finishedBitmap
            currentCoroutineContext().ensureActive()
            onProgress(.6f)
            output.outputStream().use { stream ->
                val ok = finishedBitmap.compress(format.toCompressFormat(), quality.coerceIn(1, 100), stream)
                if (!ok) {
                    throw ProcessingException(
                        R.string.processing_error_encode_image,
                        listOf(format.name),
                    )
                }
            }
            currentCoroutineContext().ensureActive()
            onProgress(.8f)
            // Not JPEG-only any more. The restriction predated exifinterface 1.4.1, which
            // writes PNG and WebP as well, and until now choosing WebP silently dropped
            // every tag - capture date, camera, GPS - with "keep Exif" switched on.
            if (!stripMetadata && ExifInterface.isSupportedMimeType(format.mimeType)) {
                copyExif(input, output)
            }
            val outputBytes = output.length()
            if (keepOnlyIfSmaller && originalBytes > 0L && outputBytes >= originalBytes) {
                onProgress(1f)
                return@withContext null
            }
            onProgress(1f)
            staged = true
            StagedMedia(
                source = source,
                file = output,
                outputName = OutputNaming.compressedName(source.displayName, format.extension),
                outputMimeType = format.mimeType,
                kind = OutputKind.IMAGE,
                originalBytes = originalBytes,
                outputBytes = outputBytes,
                resizeShortfallPx = imageResizeShortfall(
                    achievedLongEdge = maxOf(finishedBitmap.width, finishedBitmap.height),
                    targetLongEdge = resize.longEdgePx,
                ),
                requestedLongEdgePx = resize.longEdgePx,
            )
        } finally {
            finished?.takeIf { it !== decoded && !it.isRecycled }?.recycle()
            decoded?.takeIf { !it.isRecycled }?.recycle()
            input.delete()
            if (!staged) output.delete()
        }
    }

    /**
     * Decodes with an integer sample size, bounded so a large photo cannot exhaust
     * the heap. The result is at or above [longEdgePx] whenever the budget allows it;
     * [transform] does the exact scale-down afterwards.
     */
    private fun decodeSampled(file: File, longEdgePx: Int?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (maxOf(bounds.outWidth, bounds.outHeight) <= 0) return null

        val options = BitmapFactory.Options()
        options.inSampleSize = if (longEdgePx == null) {
            if (!originalSizeIsSupported(bounds.outWidth, bounds.outHeight)) {
                throw ProcessingException(
                    R.string.processing_error_original_too_large,
                    listOf(bounds.outWidth.toString(), bounds.outHeight.toString()),
                )
            }
            1
        } else {
            resizeDecodeSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                targetLongEdge = longEdgePx,
                budgetPixels = resizeDecodeBudgetPixels(Runtime.getRuntime().maxMemory()),
            )
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * Bakes the Exif rotation into the pixels and scales down to [longEdgePx], in one
     * transform.
     *
     * A failure here throws rather than handing back the untouched bitmap. It used to
     * `getOrDefault(bitmap)`, and [copyExif] then stamped `ORIENTATION_NORMAL` on the
     * result regardless - so an OutOfMemoryError from the rotation produced a
     * permanently sideways copy that declared itself upright, and the very next screen
     * offered to delete the source. Silent and unrecoverable is the worst pair of
     * properties a failure can have.
     */
    private fun transform(file: File, bitmap: Bitmap, longEdgePx: Int?): Bitmap {
        // Read before deciding anything: an unreadable Exif block is not the same
        // claim as "this image is upright", and treating it as one is how a rotation
        // silently goes missing.
        val orientation = try {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (failure: Throwable) {
            throw ProcessingException(R.string.processing_error_read_orientation, cause = failure)
        }
        val matrix = Matrix()
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdgePx != null && longEdge > longEdgePx) {
            val scale = longEdgePx.toFloat() / longEdge
            matrix.postScale(scale, scale)
        }
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
            else -> Unit
        }
        if (matrix.isIdentity) return bitmap
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (failure: OutOfMemoryError) {
            throw ProcessingException(
                R.string.processing_error_original_too_large,
                listOf(bitmap.width.toString(), bitmap.height.toString()),
                failure,
            )
        }
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
