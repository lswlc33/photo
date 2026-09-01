package com.example.photoorganizer.processing

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.photoorganizer.media.ToolAnalyzer
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaProcessingInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val createdUris = mutableListOf<android.net.Uri>()

    @After
    fun cleanup() {
        createdUris.forEach { context.contentResolver.delete(it, null, null) }
        createdUris.clear()
    }

    @Test
    fun imageReencodePublishesNonEmptyResultAndKeepsSource() = runBlocking {
        val source = publishTestImage()
        val result = requireNotNull(
            ImageProcessor.reencode(
                context = context,
                source = source,
                format = ImageFormat.JPEG,
                quality = 70,
                resize = ImageResizeOption.LONG_EDGE_1280,
                stripMetadata = true,
                keepOnlyIfSmaller = false,
            ),
        ) { "Image processing returned no result" }
        createdUris += result.uri

        assertEquals("image/jpeg", context.contentResolver.getType(result.uri))
        assertTrue(result.outputBytes > 0L)
        assertNotNull(context.contentResolver.openInputStream(source)?.use { it.read() })
        assertNotNull(context.contentResolver.openInputStream(result.uri)?.use { it.read() })
    }

    @Test
    fun videoTranscodePublishesPlayableMp4AndKeepsSource() = runBlocking {
        val sourceFile = File(context.cacheDir, "processor-test-source.mp4")
        createTestVideo(sourceFile)
        val source = publishFile(sourceFile, "video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        sourceFile.delete()

        val result = requireNotNull(
            VideoProcessor.transcode(
                context = context,
                source = source,
                resolution = VideoResolution.P480,
                trackMode = VideoTrackMode.VIDEO_ONLY,
                bitrateOverride = 600_000,
                keepOnlyIfSmaller = false,
            ),
        ) { "Video processing returned no result" }
        createdUris += result.uri

        assertEquals("video/mp4", context.contentResolver.getType(result.uri))
        assertTrue(result.outputBytes > 0L)
        context.contentResolver.openFileDescriptor(source, "r")?.use { assertTrue(it.statSize > 0L) }
            ?: throw AssertionError("Source video cannot be reopened")
        context.contentResolver.openFileDescriptor(result.uri, "r")?.use { assertTrue(it.statSize > 0L) }
            ?: throw AssertionError("Output video cannot be reopened")
    }

    @Test
    fun skipsImageResultThatWouldGrowTheFile() = runBlocking {
        val source = publishNoisyTestImage()

        val result = ImageProcessor.reencode(
            context = context,
            source = source,
            format = ImageFormat.PNG,
            quality = 100,
            resize = ImageResizeOption.LONG_EDGE_3840,
            stripMetadata = true,
            keepOnlyIfSmaller = true,
        )

        assertEquals(null, result)
    }

    @Test
    fun exactDuplicateHashUsesFullContentInsteadOfNameOrSize() {
        val first = File(context.cacheDir, "duplicate-a.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val renamedCopy = File(context.cacheDir, "renamed-copy.bin").apply { writeBytes(first.readBytes()) }
        val sameSizeDifferent = File(context.cacheDir, "different.bin").apply { writeBytes(byteArrayOf(4, 3, 2, 1)) }
        val firstUri = publishFile(first, "application/octet-stream", MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        val copyUri = publishFile(renamedCopy, "application/octet-stream", MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        val differentUri = publishFile(sameSizeDifferent, "application/octet-stream", MediaStore.Downloads.EXTERNAL_CONTENT_URI)

        val firstHash = ToolAnalyzer.contentHash(context.contentResolver, firstUri)
        assertEquals(firstHash, ToolAnalyzer.contentHash(context.contentResolver, copyUri))
        assertTrue(firstHash != ToolAnalyzer.contentHash(context.contentResolver, differentUri))
        listOf(first, renamedCopy, sameSizeDifferent).forEach(File::delete)
    }

    private fun publishTestImage(): android.net.Uri {
        val file = File(context.cacheDir, "processor-test-source.jpg")
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(38, 132, 255))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return publishFile(file, "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { file.delete() }
    }

    /** Noise compresses badly, so a lossless PNG re-encode is reliably larger than the JPEG source. */
    private fun publishNoisyTestImage(): android.net.Uri {
        val file = File(context.cacheDir, "processor-test-noise.jpg")
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(7)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                bitmap.setPixel(x, y, Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)))
            }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 55, it) }
        bitmap.recycle()
        return publishFile(file, "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { file.delete() }
    }

    private fun publishFile(file: File, mimeType: String, collection: android.net.Uri): android.net.Uri {
        val relativePath = when {
            mimeType.startsWith("image/") -> "Pictures/Photo Organizer Tests"
            mimeType.startsWith("video/") -> "Movies/Photo Organizer Tests"
            else -> "Download/Photo Organizer Tests"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(collection, values))
        createdUris += uri
        context.contentResolver.openOutputStream(uri, "w")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri
    }

    private fun createTestVideo(file: File) {
        val width = 320
        val height = 240
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var muxerStarted = false
        var frame = 0
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        if (frame < 30) {
                            val buffer = codec.getInputBuffer(inputIndex)!!
                            fillYuvFrame(buffer, width, height, frame)
                            codec.queueInputBuffer(inputIndex, 0, width * height * 3 / 2, frame * 66_666L, 0)
                            frame++
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, 0, frame * 66_666L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outputIndex)!!
                        if (info.size > 0 && muxerStarted) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(track, output, info)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }

    private fun fillYuvFrame(buffer: ByteBuffer, width: Int, height: Int, frame: Int) {
        buffer.clear()
        repeat(width * height) { buffer.put((48 + frame * 3).toByte()) }
        repeat(width * height / 4) { buffer.put(100.toByte()) }
        repeat(width * height / 4) { buffer.put(180.toByte()) }
    }
}
