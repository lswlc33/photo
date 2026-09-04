package com.lc33.photoorganizer.processing

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lc33.photoorganizer.media.PendingMedia
import com.lc33.photoorganizer.media.ToolAnalyzer
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val IMAGE_TEST_FOLDER = "Pictures/Photo Organizer Tests"
private const val VIDEO_TEST_FOLDER = "Movies/Photo Organizer Tests"

@RunWith(AndroidJUnit4::class)
class MediaProcessingInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val createdUris = mutableListOf<Uri>()

    @After
    fun cleanup() {
        createdUris.forEach { context.contentResolver.delete(it, null, null) }
        createdUris.clear()
        // Staging is the app's own cache directory, so clearing it is enough to undo
        // whatever a partial run left behind.
        StagingArea.clear(context)
    }

    @Test
    fun imageReencodeStagesAResultAndKeepsSource() = runBlocking {
        val source = publishTestImage()
        val staged = requireNotNull(
            ImageProcessor.reencode(
                context = context,
                source = pendingFor(source, IMAGE_TEST_FOLDER),
                format = ImageFormat.JPEG,
                quality = 70,
                resize = ImageResizeOption.LONG_EDGE_1280,
                stripMetadata = true,
                keepOnlyIfSmaller = false,
            ),
        ) { "Image processing returned no result" }

        // Staged, not published: nothing may reach MediaStore before the user has
        // looked at it.
        assertTrue(staged.file.isFile)
        assertTrue(staged.outputBytes > 0L)
        assertEquals("image/jpeg", staged.outputMimeType)
        assertEquals(OutputKind.IMAGE, staged.kind)
        assertTrue(staged.outputName.endsWith("-z1.jpg"))
        assertNotNull(context.contentResolver.openInputStream(source)?.use { it.read() })
    }

    @Test
    fun committingWritesTheResultIntoTheSourceFolder() = runBlocking {
        val source = publishTestImage()
        val staged = requireNotNull(
            ImageProcessor.reencode(
                context = context,
                source = pendingFor(source, IMAGE_TEST_FOLDER),
                format = ImageFormat.JPEG,
                quality = 70,
                resize = ImageResizeOption.LONG_EDGE_1280,
                stripMetadata = true,
                keepOnlyIfSmaller = false,
            ),
        ) { "Image processing returned no result" }

        val published = GalleryWriter.commit(context, staged)
        createdUris += published.uri

        assertEquals(IMAGE_TEST_FOLDER, published.folder)
        assertFalse(published.relocated)
        assertEquals("image/jpeg", context.contentResolver.getType(published.uri))
        assertNotNull(context.contentResolver.openInputStream(published.uri)?.use { it.read() })
        // The source is still there: a run copies, it never moves.
        assertNotNull(context.contentResolver.openInputStream(source)?.use { it.read() })
    }

    @Test
    fun videoTranscodeStagesAPlayableMp4AndKeepsSource() = runBlocking {
        val sourceFile = File(context.cacheDir, "processor-test-source.mp4")
        createTestVideo(sourceFile)
        val source = publishFile(sourceFile, "video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        sourceFile.delete()

        val staged = requireNotNull(
            VideoProcessor.transcode(
                context = context,
                source = pendingFor(source, VIDEO_TEST_FOLDER, isVideo = true),
                resolution = VideoResolution.P480,
                trackMode = VideoTrackMode.VIDEO_ONLY,
                bitrateOverride = 600_000,
                keepOnlyIfSmaller = false,
            ),
        ) { "Video processing returned no result" }

        assertTrue(staged.file.isFile)
        assertTrue(staged.outputBytes > 0L)
        assertEquals("video/mp4", staged.outputMimeType)
        assertEquals(OutputKind.VIDEO, staged.kind)

        val published = GalleryWriter.commit(context, staged)
        createdUris += published.uri
        assertEquals(VIDEO_TEST_FOLDER, published.folder)
        assertEquals("video/mp4", context.contentResolver.getType(published.uri))
        context.contentResolver.openFileDescriptor(source, "r")?.use { assertTrue(it.statSize > 0L) }
            ?: throw AssertionError("Source video cannot be reopened")
        context.contentResolver.openFileDescriptor(published.uri, "r")?.use { assertTrue(it.statSize > 0L) }
            ?: throw AssertionError("Output video cannot be reopened")
    }

    @Test
    fun skipsImageResultThatWouldGrowTheFile() = runBlocking {
        val source = publishNoisyTestImage()

        val result = ImageProcessor.reencode(
            context = context,
            source = pendingFor(source, IMAGE_TEST_FOLDER),
            format = ImageFormat.PNG,
            quality = 100,
            resize = ImageResizeOption.LONG_EDGE_3840,
            stripMetadata = true,
            keepOnlyIfSmaller = true,
        )

        assertEquals(null, result)
        // A rejected output must not be left behind: it is the same size as the file
        // it was rejected for being larger than.
        assertEquals(0, StagingArea.directory(context).listFiles()?.size ?: 0)
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

    private fun pendingFor(uri: Uri, relativePath: String, isVideo: Boolean = false) = PendingMedia(
        uri = uri,
        displayName = GalleryWriter.displayName(context, uri),
        isVideo = isVideo,
        sizeBytes = GalleryWriter.sourceSize(context, uri),
        relativePath = relativePath,
    )

    private fun publishTestImage(): Uri {
        val file = File(context.cacheDir, "processor-test-source.jpg")
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(38, 132, 255))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return publishFile(file, "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { file.delete() }
    }

    /** Noise compresses badly, so a lossless PNG re-encode is reliably larger than the JPEG source. */
    private fun publishNoisyTestImage(): Uri {
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

    private fun publishFile(file: File, mimeType: String, collection: Uri): Uri {
        val relativePath = when {
            mimeType.startsWith("image/") -> IMAGE_TEST_FOLDER
            mimeType.startsWith("video/") -> VIDEO_TEST_FOLDER
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
