package com.lc33.photoorganizer.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class MediaPermissionState(
    val images: Boolean,
    val videos: Boolean,
    val selectedOnly: Boolean,
) {
    val hasAccess: Boolean get() = images || videos || selectedOnly

    /**
     * True when the library the app can see is not the whole library - which is what the
     * UI has to say out loud.
     *
     * Distinct from [selectedOnly] on purpose, and the distinction matters: this is also
     * true when the user granted images but not videos, which is a stable, complete view
     * of the photos. Anything that has to react to *the visible set changing underneath
     * it* - dropping cached hashes, re-scanning on every resume - belongs on
     * [selectedOnly], because a user who simply withheld the video permission was
     * otherwise paying for a full rescan and a full duplicate pass every time they came
     * back to the app.
     */
    val isLimited: Boolean get() = hasAccess && !(images && videos)
}

fun Context.hasImagePermission(): Boolean = hasPermission(Manifest.permission.READ_MEDIA_IMAGES)

fun Context.hasVideoPermission(): Boolean = hasPermission(Manifest.permission.READ_MEDIA_VIDEO)

fun Context.hasLimitedMediaPermission(): Boolean {
    if (Build.VERSION.SDK_INT < 34) return false
    val selected = hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    return selected && !(hasImagePermission() && hasVideoPermission())
}

fun Context.mediaPermissionState(): MediaPermissionState = MediaPermissionState(
    images = hasImagePermission(),
    videos = hasVideoPermission(),
    selectedOnly = hasLimitedMediaPermission(),
)

fun Context.photoPermissionRequest(): Array<String> = if (Build.VERSION.SDK_INT >= 34) {
    arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
} else {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
