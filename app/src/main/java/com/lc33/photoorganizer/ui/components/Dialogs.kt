package com.lc33.photoorganizer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.LogicalAlbum
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Spacing between the two action buttons of a MIUIX confirmation dialog. */
private val DialogButtonSpacing = 20.dp

/** Row padding for preference rows nested inside a dialog body. */
private val DialogRowMargin = PaddingValues(horizontal = 4.dp, vertical = 10.dp)

/**
 * Standard MIUIX dialog footer: cancel on the start side, the affirmative
 * action on the end side. Dialogs in this app never show a third button.
 */
@Composable
fun DialogActions(
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    cancelText: String = stringResource(R.string.dialog_cancel),
    emphasizeConfirm: Boolean = true,
) {
    Row(Modifier.fillMaxWidth()) {
        TextButton(text = cancelText, onClick = onCancel, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(DialogButtonSpacing))
        TextButton(
            text = confirmText,
            onClick = onConfirm,
            enabled = confirmEnabled,
            colors = if (emphasizeConfirm) {
                ButtonDefaults.textButtonColorsPrimary()
            } else {
                ButtonDefaults.textButtonColors()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Informational dialog with a single acknowledge button. Hosted by the root
 * MIUIX [top.yukonga.miuix.kmp.basic.Scaffold] so it layers above the floating
 * glass bottom bar and inherits predictive-back handling.
 */
@Composable
fun MessageDialog(show: Boolean, title: String, message: String, onDismiss: () -> Unit) {
    OverlayDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        TextButton(
            text = stringResource(R.string.dialog_ok),
            onClick = onDismiss,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** System delete confirmation shown before launching the platform dialog. */
@Composable
fun DiscardDialog(
    show: Boolean,
    count: Int,
    bytes: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.discard_title),
        summary = pluralStringResource(R.plurals.discard_summary, count, count, bytes),
        onDismissRequest = onDismiss,
    ) {
        DialogActions(
            confirmText = stringResource(R.string.discard_action_continue),
            confirmEnabled = count > 0,
            onCancel = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

/** Assign the reviewed photo to a logical album or create a new one. */
@Composable
fun AlbumDialog(
    show: Boolean,
    mediaName: String,
    albums: List<LogicalAlbum>,
    onAssign: (LogicalAlbum) -> Unit,
    onCreateAndAssign: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showNewAlbum by remember { mutableStateOf(albums.isEmpty()) }
    var newAlbumName by remember { mutableStateOf("") }
    var selectedAlbumName by remember { mutableStateOf(albums.firstOrNull()?.name) }
    // The dialog stays composed across its exit animation, so reset the working
    // selection every time it is reopened.
    LaunchedEffect(show) {
        if (show) {
            showNewAlbum = albums.isEmpty()
            newAlbumName = ""
            selectedAlbumName = albums.firstOrNull()?.name
        }
    }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.album_dialog_title),
        summary = mediaName,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.album_dialog_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.subtitle.fontSize,
            )
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                albums.forEach { album ->
                    RadioButtonPreference(
                        title = album.name,
                        selected = !showNewAlbum && selectedAlbumName == album.name,
                        onClick = {
                            showNewAlbum = false
                            selectedAlbumName = album.name
                        },
                        insideMargin = DialogRowMargin,
                    )
                }
                RadioButtonPreference(
                    title = stringResource(R.string.album_new),
                    selected = showNewAlbum,
                    onClick = { showNewAlbum = true },
                    insideMargin = DialogRowMargin,
                )
            }
            if (showNewAlbum) {
                TextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it.take(80) },
                    singleLine = true,
                    label = stringResource(R.string.album_name_hint),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }
            DialogActions(
                confirmText = stringResource(R.string.dialog_confirm),
                confirmEnabled = if (showNewAlbum) newAlbumName.isNotBlank() else selectedAlbumName != null,
                onCancel = onDismiss,
                onConfirm = {
                    if (showNewAlbum) {
                        onCreateAndAssign(newAlbumName)
                    } else {
                        albums.firstOrNull { it.name == selectedAlbumName }?.let(onAssign)
                    }
                },
            )
        }
    }
}
