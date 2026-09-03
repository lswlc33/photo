package com.lc33.photoorganizer.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.albumDisplayName
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Row insets shared by the album pickers, so their rows line up with each other. */
val AlbumRowMargin = PaddingValues(horizontal = 4.dp, vertical = 10.dp)

/** Keeps the scrolling body of an overlay dialog clear of its pinned action row. */
val OverlayScrollMaxHeight = 380.dp

/**
 * One checkbox per album folder, as lazy items.
 *
 * Lazy because a device can hold hundreds of album folders and both pickers used
 * to compose a checkbox for every one inside a `verticalScroll` - the whole list
 * built before the dialog could be drawn. Emitted as items rather than wrapped in
 * a composable so each dialog owns its own `LazyColumn` and can put its header
 * rows in the same scroll area instead of nesting two scrollables.
 */
fun LazyListScope.albumCheckboxItems(
    availableAlbums: List<String>,
    selected: Set<String>,
    onToggle: (path: String, checked: Boolean) -> Unit,
) {
    items(items = availableAlbums, key = { path -> path }) { path ->
        CheckboxPreference(
            title = albumDisplayName(path),
            summary = path,
            checked = path in selected,
            onCheckedChange = { checked -> onToggle(path, checked) },
            insideMargin = AlbumRowMargin,
        )
    }
    if (availableAlbums.isEmpty()) {
        item {
            Text(
                stringResource(R.string.filter_album_empty),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        }
    }
}
