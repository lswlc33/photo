package com.lc33.photoorganizer.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lc33.photoorganizer.processing.resizeDecodeSampleSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import kotlin.math.abs

/**
 * Previews for a file that is not in MediaStore yet.
 *
 * A staged processing result has no content uri, so `loadThumbnail` cannot see it
 * and every existing preview in the app is unusable for the right-hand half of a
 * before/after comparison. These decode straight from the file instead.
 */

/** How often the start of playback is re-checked while waiting for both players. */
private const val ReadyPollMillis = 50L

/** How often the two positions are compared once both are playing. */
private const val DriftCheckMillis = 500L

/**
 * Drift the two players are allowed before the result is nudged back onto the
 * source's position. Two decoders never advance at exactly the same rate, and
 * correcting a difference this small would be a visible stutter for no gain.
 */
private const val MaxDriftMillis = 250L

/** Decodes a local image or a video's first frame off the main thread. */
@Composable
fun LocalMediaImage(
    file: File,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    requestSize: Int = 512,
    contentDescription: String? = null,
) {
    // The length is part of the key because a staged file is written once and then
    // replaced by the next run under a name that can repeat within a second.
    val cacheKey = remember(file, requestSize) {
        "staged:${file.absolutePath}:${file.length()}@$requestSize"
    }
    val cached = remember(cacheKey) { MediaThumbnailCache.get(cacheKey, requestSize) }
    val bitmap by produceState<Bitmap?>(initialValue = cached, cacheKey) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            val decoded = if (isVideo) decodeVideoFrame(file, requestSize) else decodeImage(file, requestSize)
            decoded?.also { MediaThumbnailCache.put(cacheKey, requestSize, it) }
        }
    }
    val decoded = bitmap
    if (decoded != null) {
        Image(
            bitmap = decoded.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Two players wound to the same position, so a source and its re-encode line up. */
@Stable
class SyncedPlayers internal constructor(
    val source: ExoPlayer,
    val result: ExoPlayer,
)

/**
 * Two players prepared on [sourceUri] and [resultUri] and started together.
 *
 * Started together rather than independently because that is the whole point: a
 * player that is still buffering when the other begins shows a different moment of
 * the same clip, which makes the comparison useless. Playback waits until both
 * report ready, then both seek to zero and start, and the result is nudged back
 * onto the source's position whenever the two drift apart.
 *
 * Only the result side is audible. Two soundtracks at once is noise, and the
 * result is the half whose audio there is any reason to check.
 */
@Composable
fun rememberSyncedPlayers(
    sourceUri: Uri,
    resultUri: Uri,
    playing: Boolean,
    muted: Boolean,
): SyncedPlayers {
    val context = LocalContext.current
    val players = remember(sourceUri, resultUri) {
        SyncedPlayers(
            source = comparisonPlayer(context, sourceUri),
            result = comparisonPlayer(context, resultUri),
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> resumed = false
                Lifecycle.Event.ON_RESUME -> resumed = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(players, muted) {
        players.source.volume = 0f
        players.result.volume = if (muted) 0f else 1f
    }
    LaunchedEffect(players, playing && resumed) {
        if (!(playing && resumed)) {
            players.source.pause()
            players.result.pause()
            return@LaunchedEffect
        }
        while (!players.source.isReadyToStart() || !players.result.isReadyToStart()) {
            if (players.source.playerError != null || players.result.playerError != null) {
                return@LaunchedEffect
            }
            delay(ReadyPollMillis)
        }
        players.source.seekTo(0L)
        players.result.seekTo(0L)
        players.source.play()
        players.result.play()
        while (true) {
            delay(DriftCheckMillis)
            val drift = players.result.currentPosition - players.source.currentPosition
            if (abs(drift) > MaxDriftMillis) players.result.seekTo(players.source.currentPosition)
        }
    }
    DisposableEffect(players) {
        onDispose {
            players.source.release()
            players.result.release()
        }
    }
    return players
}

/**
 * One half of a video comparison: [still] underneath, [player] fading in over it
 * once it has a frame to show.
 *
 * The still stays because two hardware decoders at once is not something every
 * device can do. When the second one cannot be allocated the player reports an
 * error and this pane keeps showing the frame it already had, instead of going
 * black.
 */
@Composable
fun VideoComparisonPane(
    player: ExoPlayer,
    showControls: Boolean,
    modifier: Modifier = Modifier,
    animationEnabled: Boolean = true,
    still: @Composable () -> Unit,
) {
    var renderedFirstFrame by remember(player) { mutableStateOf(false) }
    var failed by remember(player) { mutableStateOf(false) }
    val playerAlpha by animateFloatAsState(
        targetValue = if (renderedFirstFrame && !failed) 1f else 0f,
        animationSpec = if (animationEnabled) tween(180) else snap(),
        label = "comparison-player-alpha",
    )
    Box(modifier) {
        still()
        if (!failed) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = showControls
                        this.player = player
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = playerAlpha },
                // Detached before the view is discarded: a PlayerView that outlives
                // this composition by a frame would still be pointed at a player
                // the caller is about to release.
                onRelease = { view -> view.player = null },
                update = { view ->
                    view.useController = showControls
                    view.player = player
                },
            )
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                renderedFirstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}

private fun comparisonPlayer(context: Context, uri: Uri): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        setMediaItem(MediaItem.fromUri(uri))
        prepare()
        // Left paused: the two are started together once both are ready.
        playWhenReady = false
    }

private fun ExoPlayer.isReadyToStart(): Boolean = playbackState == Player.STATE_READY

private fun decodeImage(file: File, requestSize: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longEdge <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = resizeDecodeSampleSize(longEdge, requestSize)
    }
    BitmapFactory.decodeFile(file.absolutePath, options)
}.getOrNull()

/**
 * The first frame, scaled inside a [requestSize] box.
 *
 * The box is computed from the video's own dimensions rather than handed to
 * `getScaledFrameAtTime` as a square, because that call takes an exact output size
 * and a square one would stretch every clip that is not square.
 */
private fun decodeVideoFrame(file: File, requestSize: Int): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val width = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?: 0
        val height = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?: 0
        if (width <= 0 || height <= 0) {
            retriever.frameAtTime
        } else {
            val scale = (requestSize.toFloat() / maxOf(width, height)).coerceAtMost(1f)
            retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
            )
        }
    } finally {
        retriever.release()
    }
}.getOrNull()
