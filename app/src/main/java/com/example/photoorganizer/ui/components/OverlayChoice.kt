package com.example.photoorganizer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import com.example.photoorganizer.ui.TrackOverlayPopup

/**
 * Single-choice MIUIX overlay popup anchored to the row that opened it.
 *
 * Follows the OverlayListPopup contract: the popup is placed inside the page
 * Scaffold so MiuixPopupHost can render it, and every option is a standard
 * [DropdownImpl] row inside [ListPopupColumn]. Since the popup is anchored to a
 * specific row, renderInRootScaffold stays false so it is positioned against
 * [anchor] instead of the whole window.
 */
@Composable
fun <T> OverlayChoicePopup(
    show: Boolean,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
    minWidth: Dp = 200.dp,
    anchor: @Composable () -> Unit,
) {
    // MIUIX registers its own NavigationEventDispatcher handler, but this app
    // hosts a standalone dispatcher, so the platform back gesture is bridged
    // here to make sure back dismisses the popup instead of leaving the screen.
    BackHandler(enabled = show, onBack = onDismissRequest)
    // The popup host draws below the floating glass bottom bar, so publish the
    // open state and let the root composition move the bar out of the way.
    TrackOverlayPopup(show)
    Box {
        anchor()
        OverlayListPopup(
            show = show,
            alignment = alignment,
            onDismissRequest = onDismissRequest,
            minWidth = minWidth,
            renderInRootScaffold = false,
        ) {
            ListPopupColumn {
                options.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = stringResource(label(option)),
                        optionSize = options.size,
                        isSelected = option == selected,
                        index = index,
                        onSelectedIndexChange = {
                            onDismissRequest()
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** One actionable row inside an [OverlayActionPopup]. */
data class OverlayAction(
    val labelRes: Int,
    val onClick: () -> Unit,
)

/**
 * Single-choice overlay popup whose rows carry a title plus an explanatory
 * summary, rendered with MIUIX [DropdownImpl] so options that need a short
 * description read the same as a native spinner. Labels are plain strings
 * because several option sets mix resources with formatted values.
 */
@Composable
fun <T> OverlaySpinnerChoicePopup(
    show: Boolean,
    options: List<T>,
    selected: T,
    title: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
    summary: @Composable (T) -> String? = { null },
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
    minWidth: Dp = 240.dp,
    anchor: @Composable () -> Unit,
) {
    BackHandler(enabled = show, onBack = onDismissRequest)
    TrackOverlayPopup(show)
    Box {
        anchor()
        OverlayListPopup(
            show = show,
            alignment = alignment,
            onDismissRequest = onDismissRequest,
            minWidth = minWidth,
            renderInRootScaffold = false,
        ) {
            val dropdownColors = DropdownDefaults.dropdownColors()
            ListPopupColumn {
                options.forEachIndexed { index, option ->
                    DropdownImpl(
                        item = DropdownItem(title = title(option), summary = summary(option)),
                        optionSize = options.size,
                        isSelected = option == selected,
                        index = index,
                        dropdownColors = dropdownColors,
                        dialogMode = false,
                        onSelectedIndexChange = {
                            onDismissRequest()
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Overlay popup for actions rather than a selection, used where a top bar
 * overflow menu is needed. Rows render as unselected [DropdownImpl] entries so
 * they match every other MIUIX popup in the app.
 */
@Composable
fun OverlayActionPopup(
    show: Boolean,
    actions: List<OverlayAction>,
    onDismissRequest: () -> Unit,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
    minWidth: Dp = 200.dp,
    anchor: @Composable () -> Unit,
) {
    // MIUIX registers its own NavigationEventDispatcher handler, but this app
    // hosts a standalone dispatcher, so the platform back gesture is bridged
    // here to make sure back dismisses the popup instead of leaving the screen.
    BackHandler(enabled = show, onBack = onDismissRequest)
    // The popup host draws below the floating glass bottom bar, so publish the
    // open state and let the root composition move the bar out of the way.
    TrackOverlayPopup(show)
    Box {
        anchor()
        OverlayListPopup(
            show = show,
            alignment = alignment,
            onDismissRequest = onDismissRequest,
            minWidth = minWidth,
            renderInRootScaffold = false,
        ) {
            ListPopupColumn {
                actions.forEachIndexed { index, action ->
                    DropdownImpl(
                        text = stringResource(action.labelRes),
                        optionSize = actions.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            onDismissRequest()
                            action.onClick()
                        },
                    )
                }
            }
        }
    }
}
