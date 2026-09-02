package com.example.photoorganizer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.photoorganizer.media.UiMedia

/**
 * Hold-to-preview state for a media surface.
 *
 * The gallery grid and the swipe surface both drive the same full-screen preview
 * from the same three values and the same tap protocol, and each had its own copy -
 * including the invariant that a release must close only a preview that the *same*
 * item opened by holding, because a tap-opened one has to stay up. Getting that
 * wrong in one copy and not the other is the drift a shared holder prevents.
 */
@Stable
class MediaPreviewController {
    /** The item being previewed, or null when nothing is. */
    var item: UiMedia? by mutableStateOf(null)
        private set

    /** False while the preview animates away; the host stays composed until then. */
    var visible: Boolean by mutableStateOf(false)
        private set

    /** True when the preview lasts only as long as the finger is down. */
    var temporary: Boolean by mutableStateOf(false)
        private set

    /** A tap: the preview stays up until it is dismissed. */
    fun open(item: UiMedia) {
        this.item = item
        temporary = false
        visible = true
    }

    /** A long press: the preview lasts as long as the finger is down. */
    fun peek(item: UiMedia) {
        this.item = item
        temporary = true
        visible = true
    }

    /**
     * The finger came up on [itemId]. Closes only a peek, and only of that item, so
     * releasing after a tap - or after the pointer has moved on to another tile -
     * leaves the preview alone.
     */
    fun release(itemId: Long) {
        if (temporary && item?.id == itemId) visible = false
    }

    /** Starts the exit animation; the host finishes the teardown when it ends. */
    fun requestDismiss() {
        visible = false
    }

    internal fun onDismissed() {
        item = null
        temporary = false
    }
}

@Composable
fun rememberMediaPreviewController(): MediaPreviewController = remember { MediaPreviewController() }

/** Renders [controller]'s preview, if it has one. */
@Composable
fun MediaPreviewHost(controller: MediaPreviewController, animationEnabled: Boolean) {
    val item = controller.item ?: return
    FullScreenMediaPreview(
        item = item,
        temporary = controller.temporary,
        visible = controller.visible,
        animationEnabled = animationEnabled,
        onRequestDismiss = controller::requestDismiss,
        onDismissed = controller::onDismissed,
    )
}
