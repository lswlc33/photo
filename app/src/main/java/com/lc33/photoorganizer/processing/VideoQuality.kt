package com.lc33.photoorganizer.processing

/**
 * Strength preset shared by the settings screen and the media tools. Encoding
 * itself runs through [ImageProcessor] and [VideoProcessor], which use platform
 * codecs and therefore work on every supported ABI.
 */
enum class VideoQuality { HIGH, MEDIUM, LOW }

/**
 * The resolution a run starts from for this preset.
 *
 * Here rather than on the tools page because the preset is a Settings value and
 * the resolution is what the queue actually acts on, so the mapping belongs on the
 * same side as both.
 */
fun VideoQuality.toDefaultResolution(): VideoResolution = when (this) {
    VideoQuality.HIGH -> VideoResolution.P1080
    VideoQuality.MEDIUM -> VideoResolution.P720
    VideoQuality.LOW -> VideoResolution.P480
}
