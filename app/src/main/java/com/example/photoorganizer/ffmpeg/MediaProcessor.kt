package com.example.photoorganizer.ffmpeg

/**
 * Strength preset shared by the settings screen and the media tools. The actual
 * encoding is performed by [com.example.photoorganizer.processing.ImageProcessor]
 * and [com.example.photoorganizer.processing.VideoProcessor], which use platform
 * codecs and therefore work on every supported ABI.
 */
enum class VideoQuality { HIGH, MEDIUM, LOW }
