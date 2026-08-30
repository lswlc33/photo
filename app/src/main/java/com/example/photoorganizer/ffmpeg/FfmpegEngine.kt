package com.example.photoorganizer.ffmpeg

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the bundled FFmpeg command-line binary. The binary is
 * packaged as `jniLibs/arm64-v8a/libffmpeg_cli.so`, so the system extracts it
 * to the app's read-only native library directory where execution is allowed.
 */
object FfmpegEngine {

    private const val BINARY_NAME = "libffmpeg_cli.so"

    /** The bundled binary is arm64-only, so the app's primary ABI has to be arm64. */
    fun isSupportedAbi(): Boolean = Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"

    fun binaryFile(context: Context): File? =
        File(context.applicationInfo.nativeLibraryDir, BINARY_NAME).takeIf { it.exists() && it.length() > 0 }

    /** Whether the bundled binary is present and the device ABI can run it. */
    fun isAvailable(context: Context): Boolean = isSupportedAbi() && binaryFile(context) != null

    /** Returns the first line of `ffmpeg -version`, or null when unavailable. */
    suspend fun probeVersion(context: Context): String? = withContext(Dispatchers.IO) {
        val binary = binaryFile(context) ?: return@withContext null
        runCatching {
            val process = ProcessBuilder(binary.absolutePath, "-version")
                .redirectErrorStream(true)
                .start()
            try {
                val line = process.inputStream.bufferedReader().use { reader -> reader.readLine() }
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@runCatching null
                }
                line
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }.getOrNull()
    }

    /**
     * Runs ffmpeg with [args]. [onOutput] receives every merged stdout/stderr
     * line (progress lives on stderr). Cancellation destroys the process.
     */
    suspend fun run(
        context: Context,
        args: List<String>,
        onOutput: (String) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val binary = binaryFile(context)
            ?: return@withContext Result.failure(IllegalStateException("FFmpeg binary is missing"))
        runCatching {
            val process = ProcessBuilder(listOf(binary.absolutePath, "-hide_banner", "-nostdin", "-y") + args)
                .redirectErrorStream(true)
                .start()
            val job = currentCoroutineContext()[kotlinx.coroutines.Job]
            val cancellationHandle = job?.invokeOnCompletion {
                if (job.isCancelled && process.isAlive) process.destroyForcibly()
            }
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach(onOutput)
                }
                val code = process.waitFor()
                check(code == 0) { "ffmpeg exited with code $code" }
            } finally {
                cancellationHandle?.dispose()
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }
}
