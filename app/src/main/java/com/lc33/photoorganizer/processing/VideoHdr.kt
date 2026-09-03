package com.lc33.photoorganizer.processing

/**
 * How a video's colour is encoded, as far as re-encoding cares.
 *
 * This matters because Media3 cannot promise to keep HDR. `HDR_MODE_KEEP_HDR` is
 * the default, but its own documentation says it is "supported on API 31+, by
 * some device and HDR format combinations" and that when it is not supported
 * "Transformer will attempt to use HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL" -
 * that is, quietly hand back an SDR file. No listener is notified. Dolby Vision
 * is worse: its dynamic metadata rides in a separate layer that the decode ->
 * effects -> encode -> MP4 pipeline does not carry at all, so an export can only
 * ever produce the base layer with the Dolby Vision gone.
 *
 * So the app detects this up front and refuses by default, instead of handing
 * back a washed-out file and calling it a success.
 */
enum class HdrKind {
    SDR,
    /** Hybrid Log-Gamma, what most phone cameras record. */
    HLG,
    /** PQ / ST.2084, i.e. HDR10 and HDR10+. */
    PQ,
    DOLBY_VISION,
    UNKNOWN,
}

/** True for anything whose colour cannot survive a plain SDR re-encode. */
internal val HdrKind.isHdr: Boolean
    get() = this == HdrKind.HLG || this == HdrKind.PQ || this == HdrKind.DOLBY_VISION

/**
 * Mirrors `MediaFormat.COLOR_TRANSFER_*`. Kept as local constants so the
 * classifier below stays pure Kotlin and unit-testable, per the rule that
 * analysis logic does not reach for Android APIs.
 */
internal const val ColorTransferUnset = 0
internal const val ColorTransferLinear = 1
internal const val ColorTransferSdr = 3
internal const val ColorTransferSt2084 = 6
internal const val ColorTransferHlg = 7

internal const val DolbyVisionMimeType = "video/dolby-vision"

/**
 * What [colorTransfer] and [mimeType] say about the source's colour.
 *
 * An absent transfer characteristic is treated as SDR rather than as unknown:
 * every SDR file predating the metadata omits it, and refusing to process all of
 * them would be worse than the rare mislabelled HDR file, which the output check
 * catches anyway.
 */
internal fun classifyHdr(mimeType: String?, colorTransfer: Int): HdrKind = when {
    mimeType?.lowercase()?.contains("dolby-vision") == true -> HdrKind.DOLBY_VISION
    colorTransfer == ColorTransferSt2084 -> HdrKind.PQ
    colorTransfer == ColorTransferHlg -> HdrKind.HLG
    colorTransfer == ColorTransferSdr -> HdrKind.SDR
    colorTransfer == ColorTransferUnset -> HdrKind.SDR
    colorTransfer == ColorTransferLinear -> HdrKind.UNKNOWN
    else -> HdrKind.UNKNOWN
}

/** Why an item was refused before any work started. */
enum class SkipReason {
    /** HLG or PQ, and the user has not agreed to an SDR copy. */
    HDR_WOULD_BE_LOST,

    /** Dolby Vision, which no export can preserve. */
    DOLBY_VISION_CANNOT_SURVIVE,
}

/**
 * Whether to refuse [kind] outright, given whether the user has agreed to get an
 * SDR copy back. Null means go ahead.
 */
internal fun hdrSkipReason(kind: HdrKind, allowHdrToSdr: Boolean): SkipReason? = when {
    !kind.isHdr -> null
    allowHdrToSdr -> null
    kind == HdrKind.DOLBY_VISION -> SkipReason.DOLBY_VISION_CANNOT_SURVIVE
    else -> SkipReason.HDR_WOULD_BE_LOST
}

/**
 * True when the export silently dropped HDR - the input carried it and the
 * output does not. Checked after every export rather than trusted from
 * configuration, because that is the one failure Media3 does not report.
 */
internal fun hdrWasLost(input: HdrKind, output: HdrKind): Boolean =
    input.isHdr && !output.isHdr
