package com.example.photoorganizer.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.photoorganizer.R
import com.example.photoorganizer.media.UiMedia
import com.example.photoorganizer.media.formatBytes
import com.example.photoorganizer.media.formatDuration
import com.example.photoorganizer.media.scanTime
import com.example.photoorganizer.ui.systemClearance
import com.example.photoorganizer.ui.theme.AccentBlue
import com.example.photoorganizer.ui.theme.AccentGreen
import com.example.photoorganizer.ui.theme.AccentOrange
import com.example.photoorganizer.ui.theme.AccentViolet
import com.example.photoorganizer.ui.theme.DangerRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Loads a MediaStore thumbnail off the main thread. */
@Composable
fun MediaThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    requestSize: Int = 512,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val cacheKey = remember(uri, requestSize) { "$uri@$requestSize" }
    val cached = remember(cacheKey) { MediaThumbnailCache.get(cacheKey) }
    val bitmap by produceState<Bitmap?>(initialValue = cached, cacheKey) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(requestSize, requestSize), null)
            }.getOrNull()?.also { MediaThumbnailCache.put(cacheKey, it) }
        }
    }
    val thumbnail = bitmap
    if (thumbnail != null) {
        Image(
            bitmap = thumbnail.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Cropped media card used by the swipe review carousel. */
@Composable
fun MediaPreview(item: UiMedia, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = standardCardColors()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val uri = item.uri
            if (uri != null) {
                MediaThumbnail(uri, Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(64.dp),
                )
            }
            MediaBadges(item = item, large = true)
        }
    }
}

/** Square gallery tile with tap, hold-to-preview and multi-select support. */
@Composable
fun MediaTile(
    item: UiMedia,
    onClick: () -> Unit,
    onPreviewStart: () -> Unit,
    onPreviewEnd: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onSelectionToggle: () -> Unit = {},
) {
    val shape = RoundedCornerShape(8.dp)
    val description = stringResource(
        R.string.media_tile_cd,
        item.displayName,
        mediaTypeLabel(item),
        formatBytes(item.sizeBytes),
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tileScale by animateFloatAsState(
        targetValue = if (pressed) .95f else 1f,
        animationSpec = spring(dampingRatio = .55f, stiffness = 900f),
        label = "tile-press",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = tileScale
                scaleY = tileScale
            }
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .pointerInput(item.id, selectionMode) {
                detectTapGestures(
                    onTap = {
                        if (selectionMode) onSelectionToggle() else onClick()
                    },
                    onLongPress = {
                        if (!selectionMode) onPreviewStart()
                    },
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        val released = tryAwaitRelease()
                        interactionSource.emit(
                            if (released) {
                                PressInteraction.Release(press)
                            } else {
                                PressInteraction.Cancel(press)
                            },
                        )
                        if (!selectionMode) onPreviewEnd()
                    },
                )
            }
            .semantics {
                contentDescription = description
                this.selected = selected
                onClick {
                    if (selectionMode) onSelectionToggle() else onClick()
                    true
                }
            },
    ) {
        val uri = item.uri
        if (uri != null) {
            MediaThumbnail(uri, Modifier.matchParentSize())
        }
        MediaBadges(item)
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(2.dp, MiuixTheme.colorScheme.primary, shape),
            )
        }
        if (selectionMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MiuixTheme.colorScheme.primary else Color.Black.copy(alpha = .5f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

/** Full-window fit preview. Videos autoplay; temporary previews close on release. */
@Composable
fun FullScreenMediaPreview(
    item: UiMedia,
    modifier: Modifier = Modifier,
    temporary: Boolean,
    visible: Boolean,
    animationEnabled: Boolean = true,
    onRequestDismiss: () -> Unit,
    onDismissed: () -> Unit,
) {
    val reveal = remember(item.id) { Animatable(0f) }
    val clearance = systemClearance()
    LaunchedEffect(visible) {
        if (visible) {
            if (animationEnabled) {
                reveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                )
            } else {
                reveal.snapTo(1f)
            }
        } else {
            if (animationEnabled) {
                reveal.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                )
            } else {
                reveal.snapTo(0f)
            }
            onDismissed()
        }
    }

    Dialog(
        onDismissRequest = onRequestDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val progress = reveal.value
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = progress)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = progress
                        scaleX = .86f + .14f * progress
                        scaleY = .86f + .14f * progress
                    },
            ) {
                val uri = item.uri
                when {
                    uri == null -> Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .7f),
                        modifier = Modifier.size(72.dp).align(Alignment.Center),
                    )
                    item.isVideo || item.isLivePhoto -> PlatformMotionPreview(
                        stillUri = uri,
                        playbackUri = item.playbackUri ?: uri,
                        muted = temporary,
                        showControls = !temporary,
                        animationEnabled = animationEnabled,
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> MediaThumbnail(
                        uri = uri,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        requestSize = 2048,
                        contentDescription = item.displayName,
                    )
                }
            }

            if (!temporary) {
                IconButton(
                    onClick = onRequestDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp + clearance.start, top = 20.dp + clearance.top)
                        .size(48.dp)
                        .graphicsLayer {
                            alpha = progress
                            scaleX = .7f + .3f * progress
                            scaleY = .7f + .3f * progress
                        }
                        .background(Color.Black.copy(alpha = .55f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.media_preview_close_cd),
                        tint = Color.White,
                    )
                }
            }

            if (!temporary) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = progress
                            translationY = (1f - progress) * 80f
                        }
                        .background(Color.Black.copy(alpha = .58f))
                        .padding(
                            start = 20.dp + clearance.start,
                            top = 16.dp,
                            end = 20.dp + clearance.end,
                            bottom = 16.dp + clearance.bottom,
                        ),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = item.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(mediaTypeLabel(item), color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                        Text(formatBytes(item.sizeBytes), color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                        Text(mediaFileExtension(item), color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                        mediaTimeLabel(item)?.let {
                            Text(it, color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaBadges(item: UiMedia, large: Boolean = false) {
    val fontSize = if (large) 11.sp else 9.sp
    val horizontalPadding = if (large) 7.dp else 5.dp
    val outerPadding = if (large) 9.dp else 5.dp
    Column(Modifier.fillMaxSize().padding(outerPadding)) {
        Row(Modifier.fillMaxWidth()) {
            MediaBadge(
                mediaTypeLabel(item),
                fontSize,
                horizontalPadding,
                mediaTypeBadgeColor(item),
            )
            Spacer(Modifier.weight(1f))
            mediaTimeLabel(item)?.let { MediaBadge(it, fontSize, horizontalPadding) }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            MediaBadge(formatBytes(item.sizeBytes), fontSize, horizontalPadding)
            Spacer(Modifier.weight(1f))
            val extension = mediaFileExtension(item)
            MediaBadge(extension, fontSize, horizontalPadding, mediaFormatBadgeColor(extension))
        }
    }
}

@Composable
private fun MediaBadge(
    label: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    backgroundColor: Color = Color.Black.copy(alpha = .62f),
) {
    Box(
        Modifier
            .background(backgroundColor, RoundedCornerShape(5.dp))
            .padding(horizontal = horizontalPadding, vertical = 2.dp),
    ) {
        Text(label, color = Color.White, fontSize = fontSize, maxLines = 1)
    }
}

@Composable
private fun mediaTypeBadgeColor(item: UiMedia): Color = when {
    item.isLivePhoto -> AccentGreen
    item.isVideo -> AccentViolet
    item.isRaw -> AccentOrange
    item.isScreenshot -> DangerRed
    item.mimeType.equals("image/gif", ignoreCase = true) -> Color(0xFFB04A9D)
    else -> AccentBlue
}

private fun mediaFormatBadgeColor(extension: String): Color = when (extension) {
    "JPG", "JPEG" -> AccentBlue
    "PNG", "WEBP" -> Color(0xFF008C95)
    "HEIC", "HEIF" -> AccentViolet
    "MP4" -> Color(0xFF6857C8)
    "MOV" -> Color(0xFFB04A9D)
    "GIF" -> AccentGreen
    "ARW", "CR2", "CR3", "DNG", "NEF", "ORF", "PEF", "RAF", "RW2", "SRW" -> AccentOrange
    else -> Color(0xFF59636F)
}

@Composable
private fun mediaTypeLabel(item: UiMedia): String = when {
    item.isLivePhoto -> stringResource(R.string.media_type_live_photo)
    item.isVideo -> stringResource(R.string.media_type_video)
    item.isRaw -> stringResource(R.string.media_type_raw)
    item.isScreenshot -> stringResource(R.string.media_type_screenshot)
    item.mimeType.equals("image/gif", ignoreCase = true) -> stringResource(R.string.media_type_gif)
    else -> stringResource(R.string.media_type_photo)
}

private fun mediaTimeLabel(item: UiMedia): String? = when {
    (item.isVideo || item.isLivePhoto) && item.durationMillis != null -> formatDuration(item.durationMillis)
    item.dateTakenMillis != null -> scanTime(item.dateTakenMillis)
    else -> null
}

private fun mediaFileExtension(item: UiMedia): String =
    item.displayName.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf(String::isNotBlank)
        ?.uppercase()
        ?: item.mimeType.substringAfter('/', missingDelimiterValue = "FILE").uppercase()

@Composable
private fun PlatformMotionPreview(
    stillUri: Uri,
    playbackUri: Uri,
    muted: Boolean,
    showControls: Boolean,
    animationEnabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var renderedFirstFrame by remember(playbackUri) { mutableStateOf(false) }
    var playbackFailed by remember(playbackUri) { mutableStateOf(false) }
    val player = remember(playbackUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(playbackUri))
            prepare()
            playWhenReady = true
        }
    }
    val playerAlpha by animateFloatAsState(
        targetValue = if (renderedFirstFrame && !playbackFailed) 1f else 0f,
        animationSpec = if (animationEnabled) tween(180) else snap(),
        label = "motion-preview-alpha",
    )
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
    Box(modifier) {
        MediaThumbnail(
            uri = stillUri,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            requestSize = 2048,
            contentDescription = contentDescription,
        )
        if (!playbackFailed) {
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
                update = { view ->
                    view.useController = showControls
                    view.player = player
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.play()
                },
            )
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                renderedFirstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackFailed = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        player.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            player.removeListener(playerListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
}

private object MediaThumbnailCache {
    private const val MAX_CACHE_KILOBYTES = 48 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KILOBYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}
