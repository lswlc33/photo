package com.lc33.photoorganizer.media

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes [target] by filling a sibling temporary file and moving it into place.
 *
 * Both persisted files in this package are rewritten wholesale, and both used to do it
 * as `writeText` to a temporary followed by `renameTo` with a delete-and-retry when the
 * rename failed. Two problems with that. `File.renameTo` does not replace an existing
 * destination on every platform - notably not on Windows, where the unit tests run - so
 * the retry was not a rare fallback but the normal path; and deleting the destination
 * first means the one situation where the rename fails is also the one where the
 * original is already gone. For the review log that is the user''s entire decision
 * history.
 *
 * `Files.move` with REPLACE_EXISTING and ATOMIC_MOVE is a single atomic replace on both
 * platforms, so the destination is either the old file or the new one and never neither.
 * [sync] fsyncs before the move, which is what makes "never neither" survive a power
 * cut rather than only a crash.
 *
 * Returns false when nothing could be written; the original is left untouched.
 */
internal fun writeFileAtomically(target: File, sync: Boolean, write: (FileOutputStream) -> Unit): Boolean =
    runCatching {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                write(stream)
                stream.flush()
                if (sync) stream.fd.sync()
            }
            moveInto(temporary, target)
            true
        } finally {
            temporary.delete()
        }
    }.getOrDefault(false)

private fun moveInto(temporary: File, target: File) {
    try {
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        // A filesystem that cannot promise atomicity still replaces the file; losing the
        // guarantee is better than losing the write.
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}