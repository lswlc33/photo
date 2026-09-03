package com.lc33.photoorganizer.processing

/**
 * Strength preset shared by the settings screen and the media tools. Encoding
 * itself runs through [ImageProcessor] and [VideoProcessor], which use platform
 * codecs and therefore work on every supported ABI.
 */
enum class VideoQuality { HIGH, MEDIUM, LOW }
