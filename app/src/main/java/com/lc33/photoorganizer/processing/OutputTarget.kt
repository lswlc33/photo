package com.lc33.photoorganizer.processing

/**
 * Which folder a processed copy belongs in.
 *
 * The result belongs next to the file it came from - a compressed clip back in
 * `Movies/`, a compressed photo back in `DCIM/Camera/` - so the source's own
 * `RELATIVE_PATH` is the first choice. Writing every result into one folder of the
 * app's own meant the user had to go and find it, and lost the album a photo was
 * filed under.
 *
 * MediaStore only accepts a fixed set of top-level directories per collection
 * though, and says so by throwing at insert time. A video indexed under
 * `Download/` is a real case, so the rule below picks the source's folder only
 * when the collection will take it and falls back to the app's own folder
 * otherwise. [GalleryWriter] still catches the rejection, because this list is a
 * copy of a platform decision and the platform gets the last word.
 *
 * Pure Kotlin: this is the part with a rule in it, and a JVM test can cover it
 * where an actual `insert` needs a device.
 */

/** Top-level directories MediaStore associates with each collection. */
private val AllowedPrimaryDirectories: Map<OutputKind, Set<String>> = mapOf(
    OutputKind.IMAGE to setOf("dcim", "pictures"),
    OutputKind.VIDEO to setOf("dcim", "movies", "pictures"),
    OutputKind.AUDIO to setOf(
        "alarms",
        "audiobooks",
        "music",
        "notifications",
        "podcasts",
        "recordings",
        "ringtones",
    ),
)

/** The app's own folder, used when the source's folder cannot hold the result. */
internal fun defaultOutputFolder(kind: OutputKind): String = when (kind) {
    OutputKind.IMAGE -> GalleryWriter.IMAGE_FOLDER
    OutputKind.VIDEO -> GalleryWriter.VIDEO_FOLDER
    OutputKind.AUDIO -> GalleryWriter.AUDIO_FOLDER
}

/**
 * [sourceRelativePath] as a folder with no trailing separator, which is the form
 * `MediaStore.MediaColumns.RELATIVE_PATH` wants on insert. Null when the source
 * has no usable path of its own.
 *
 * A `..` anywhere disqualifies the whole path rather than being dropped or
 * resolved. It cannot occur in a `RELATIVE_PATH` MediaStore itself reported, so
 * this is the guard for a crafted or corrupted value - and for one of those,
 * silently writing to whichever folder is left after editing the path is worse
 * than not using it at all.
 */
internal fun normalizeFolder(sourceRelativePath: String?): String? {
    val segments = sourceRelativePath
        ?.replace('\\', '/')
        ?.split('/')
        ?: return null
    if (segments.any { it.trim() == ".." }) return null
    return segments
        .filter { it.isNotBlank() && it != "." }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("/")
}

/**
 * The folder a [kind] result should be written to for a source that lives in
 * [sourceRelativePath].
 */
internal fun resolveOutputFolder(sourceRelativePath: String?, kind: OutputKind): String {
    val folder = normalizeFolder(sourceRelativePath) ?: return defaultOutputFolder(kind)
    val primary = folder.substringBefore('/').lowercase()
    val allowed = AllowedPrimaryDirectories[kind].orEmpty()
    return if (primary in allowed) folder else defaultOutputFolder(kind)
}
