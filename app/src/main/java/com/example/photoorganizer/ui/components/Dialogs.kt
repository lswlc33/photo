package com.example.photoorganizer.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photoorganizer.R
import com.example.photoorganizer.media.LogicalAlbum
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Message dialog built from a Compose [Dialog] with a MIUIX [Card] surface, as
 * MIUIX window dialogs need a NavigationEventDispatcher host this activity does
 * not provide.
 */
@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(24.dp), colors = standardCardColors()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(18.dp))
                TextButton(text = stringResource(R.string.dialog_ok), onClick = onDismiss, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

/** System delete confirmation shown before launching the platform dialog. */
@Composable
fun DiscardDialog(
    count: Int,
    bytes: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(24.dp), colors = standardCardColors()) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.discard_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    pluralStringResource(R.plurals.discard_summary, count, count, bytes),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(text = stringResource(R.string.dialog_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                    TextButton(
                        text = stringResource(R.string.discard_action_continue),
                        onClick = onConfirm,
                        enabled = count > 0,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Assign the reviewed photo to a logical album or create a new one. */
@Composable
fun AlbumDialog(
    mediaName: String,
    albums: List<LogicalAlbum>,
    onAssign: (LogicalAlbum) -> Unit,
    onCreateAndAssign: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showNewAlbum by remember { mutableStateOf(albums.isEmpty()) }
    var newAlbumName by remember { mutableStateOf("") }
    var selectedAlbumName by remember { mutableStateOf(albums.firstOrNull()?.name) }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(24.dp), colors = standardCardColors()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.album_dialog_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    mediaName,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    stringResource(R.string.album_dialog_summary),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
                albums.forEach { album ->
                    RadioButtonPreference(
                        title = album.name,
                        selected = !showNewAlbum && selectedAlbumName == album.name,
                        onClick = {
                            showNewAlbum = false
                            selectedAlbumName = album.name
                        },
                    )
                }
                RadioButtonPreference(
                    title = stringResource(R.string.album_new),
                    selected = showNewAlbum,
                    onClick = { showNewAlbum = true },
                )
                if (showNewAlbum) {
                    TextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it.take(80) },
                        singleLine = true,
                        label = stringResource(R.string.album_name_hint),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.dialog_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.dialog_confirm),
                        enabled = if (showNewAlbum) newAlbumName.isNotBlank() else selectedAlbumName != null,
                        onClick = {
                            if (showNewAlbum) {
                                onCreateAndAssign(newAlbumName)
                            } else {
                                albums.firstOrNull { it.name == selectedAlbumName }?.let(onAssign)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
