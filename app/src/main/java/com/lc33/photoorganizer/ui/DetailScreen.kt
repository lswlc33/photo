package com.lc33.photoorganizer.ui

import androidx.compose.runtime.saveable.Saver
import com.lc33.photoorganizer.media.DuplicateGroup
import com.lc33.photoorganizer.media.PendingMedia
import com.lc33.photoorganizer.media.TargetFilters
import com.lc33.photoorganizer.media.TypeFilter

/**
 * One full-screen destination stacked above the [AppPage] layer.
 *
 * This replaces the flat `DetailMode` enum so a destination can carry the
 * arguments it needs instead of leaving them in sibling root state. That was the
 * actual defect: the enum said *which* screen was open, and four separate
 * `remember`s said *what* it should show, so every push had to keep two pieces of
 * state in step and every pop had to remember to clear the second one.
 *
 * The root holds these as a stack, so a destination opened from another
 * destination pops back to its opener with no per-screen bookkeeping. That is
 * what makes a fourth level - a gallery grid handing a selection to
 * [MediaProcessing] - work the same way the first one does.
 */
sealed interface DetailScreen {

    /** Full-screen swipe review over [filters], in smart order when [smartOrder]. */
    data class Swipe(val filters: TargetFilters, val smartOrder: Boolean) : DetailScreen

    data object Manual : DetailScreen

    data object Kept : DetailScreen

    data object Trash : DetailScreen

    data object Duplicates : DetailScreen

    data object Similar : DetailScreen

    /**
     * The members of one duplicate or similar group.
     *
     * The group is held rather than an id because it is derived analysis output,
     * not an indexed entity - there is nothing to look it up by. It is also why
     * this destination cannot be restored after process death; see
     * [decodeDetailStack].
     */
    data class DuplicateGroupGrid(val group: DuplicateGroup) : DetailScreen

    data object Screenshots : DetailScreen

    data object Largest : DetailScreen

    /**
     * The processing tools, optionally opened on a selection handed over from a
     * gallery grid so the user does not have to re-pick it through the system
     * picker.
     *
     * Holding the hand-off here rather than in root state is what makes it end
     * when the screen does: leaving by any route - button, system back or the
     * predictive back gesture - pops the destination and the selection with it.
     */
    data class MediaProcessing(val preselected: List<PendingMedia>) : DetailScreen

    /** A user-defined album, identified by name so it survives a state restore. */
    data class LogicalAlbumGrid(val albumName: String) : DetailScreen

    data object About : DetailScreen
}

// Encoding for `rememberSaveable`. Kept as plain functions over plain strings so
// the stack rules - which destinations survive a restore, and what happens to the
// ones above them - are covered by a JVM test rather than only by a device run.

private const val FieldSeparator = '\u001E'
private const val AlbumSeparator = '\u001F'

private const val TagSwipe = "swipe"
private const val TagManual = "manual"
private const val TagKept = "kept"
private const val TagTrash = "trash"
private const val TagDuplicates = "duplicates"
private const val TagSimilar = "similar"
private const val TagDuplicateGroup = "duplicate-group"
private const val TagScreenshots = "screenshots"
private const val TagLargest = "largest"
private const val TagMediaProcessing = "media-processing"
private const val TagLogicalAlbum = "logical-album"
private const val TagAbout = "about"

internal fun encodeDetailScreen(screen: DetailScreen): String = when (screen) {
    is DetailScreen.Swipe -> listOf(
        TagSwipe,
        screen.smartOrder.toString(),
        screen.filters.albumPaths.joinToString(AlbumSeparator.toString()),
        screen.filters.startDateMillis?.toString().orEmpty(),
        screen.filters.endDateMillis?.toString().orEmpty(),
        screen.filters.type.name,
        screen.filters.minSizeBytes?.toString().orEmpty(),
    ).joinToString(FieldSeparator.toString())
    DetailScreen.Manual -> TagManual
    DetailScreen.Kept -> TagKept
    DetailScreen.Trash -> TagTrash
    DetailScreen.Duplicates -> TagDuplicates
    DetailScreen.Similar -> TagSimilar
    // Only the tag: an analysis group is not addressable, so there is nothing to
    // write that could bring it back.
    is DetailScreen.DuplicateGroupGrid -> TagDuplicateGroup
    DetailScreen.Screenshots -> TagScreenshots
    DetailScreen.Largest -> TagLargest
    // The screen is restored, the hand-off is not. A preselection is a transient
    // gesture, and re-opening the tools page with settings intact is the useful
    // half of it.
    is DetailScreen.MediaProcessing -> TagMediaProcessing
    is DetailScreen.LogicalAlbumGrid -> listOf(TagLogicalAlbum, screen.albumName)
        .joinToString(FieldSeparator.toString())
    DetailScreen.About -> TagAbout
}

/** Null when [encoded] names a destination that cannot be brought back. */
internal fun decodeDetailScreen(encoded: String): DetailScreen? {
    val fields = encoded.split(FieldSeparator)
    return when (fields.firstOrNull()) {
        TagSwipe -> runCatching {
            DetailScreen.Swipe(
                filters = TargetFilters(
                    albumPaths = fields[2].takeIf(String::isNotEmpty)
                        ?.split(AlbumSeparator)?.toSet().orEmpty(),
                    startDateMillis = fields[3].toLongOrNull(),
                    endDateMillis = fields[4].toLongOrNull(),
                    type = TypeFilter.valueOf(fields[5]),
                    minSizeBytes = fields[6].toLongOrNull(),
                ),
                smartOrder = fields[1].toBooleanStrict(),
            )
        }.getOrNull()
        TagManual -> DetailScreen.Manual
        TagKept -> DetailScreen.Kept
        TagTrash -> DetailScreen.Trash
        TagDuplicates -> DetailScreen.Duplicates
        TagSimilar -> DetailScreen.Similar
        TagDuplicateGroup -> null
        TagScreenshots -> DetailScreen.Screenshots
        TagLargest -> DetailScreen.Largest
        TagMediaProcessing -> DetailScreen.MediaProcessing(emptyList())
        TagLogicalAlbum -> fields.getOrNull(1)
            ?.takeIf(String::isNotEmpty)
            ?.let { name -> DetailScreen.LogicalAlbumGrid(name) }
        TagAbout -> DetailScreen.About
        else -> null
    }
}

/**
 * Restores as much of the stack as is still meaningful.
 *
 * Truncates at the first entry that cannot be restored instead of skipping it:
 * being "inside" a destination whose opener was lost is not a state the app can
 * render, so a duplicate-group grid that no longer has its group takes the
 * screens above it with it and leaves the user on the list that opened it.
 */
internal fun decodeDetailStack(encoded: List<String>): List<DetailScreen> {
    val restored = ArrayList<DetailScreen>(encoded.size)
    for (entry in encoded) {
        restored += decodeDetailScreen(entry) ?: break
    }
    return restored
}

val DetailStackSaver: Saver<List<DetailScreen>, ArrayList<String>> = Saver(
    save = { stack -> ArrayList(stack.map(::encodeDetailScreen)) },
    restore = { encoded -> decodeDetailStack(encoded) },
)
