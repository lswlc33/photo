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
    val isLimited: Boolean get() = hasAccess && !(images && videos)
}

fun Context.hasImagePermission(): Boolean = hasPermission(Manifest.permission.READ_MEDIA_IMAGES)

fun Context.hasVideoPermission(): Boolean = hasPermission(Manifest.permission.READ_MEDIA_VIDEO)

fun Context.hasLimitedMediaPermission(): Boolean {
    if (Build.VERSION.SDK_INT < 34) return false
    val selected = hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    return selected && !(hasImagePermission() && hasVideoPermission())
}

fun Context.hasPhotoPermission(): Boolean = hasImagePermission() || hasVideoPermission() || hasLimitedMediaPermission()

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
