package com.example.photoorganizer

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.photoorganizer.ffmpeg.FfmpegEngine
import com.example.photoorganizer.ffmpeg.VideoQuality
import com.example.photoorganizer.media.LogicalAlbum
import com.example.photoorganizer.media.LogicalAlbumStore
import com.example.photoorganizer.media.IndexScope
import com.example.photoorganizer.media.IndexScopeMode
import com.example.photoorganizer.media.MediaStoreIndexer
import com.example.photoorganizer.media.ReviewState
import com.example.photoorganizer.media.TargetFilters
import com.example.photoorganizer.media.DuplicateGroup
import com.example.photoorganizer.media.ToolAnalysis
import com.example.photoorganizer.media.ToolAnalyzer
import com.example.photoorganizer.media.TypeFilter
import com.example.photoorganizer.media.applyTargetFilters
import com.example.photoorganizer.media.formatBytes
import com.example.photoorganizer.media.mediaPermissionState
import com.example.photoorganizer.media.photoPermissionRequest
import com.example.photoorganizer.media.toUiMedia
import com.example.photoorganizer.screens.dashboard.DashboardScreen
import com.example.photoorganizer.screens.dashboard.DashboardState
import com.example.photoorganizer.screens.organize.OrganizeScreen
import com.example.photoorganizer.screens.review.ManualGridScreen
import com.example.photoorganizer.screens.review.MediaGridMode
import com.example.photoorganizer.screens.review.SwipeReviewScreen
import com.example.photoorganizer.screens.settings.AboutScreen
import com.example.photoorganizer.screens.settings.SettingsScreen
import com.example.photoorganizer.screens.settings.SortOrder
import com.example.photoorganizer.screens.tools.DuplicateGroupsScreen
import com.example.photoorganizer.screens.tools.MediaToolsScreen
import com.example.photoorganizer.screens.tools.ToolsScreen
import com.example.photoorganizer.ui.AppPage
import com.example.photoorganizer.ui.FloatingBottomBarBottomMargin
import com.example.photoorganizer.ui.FloatingBottomBarHeight
import com.example.photoorganizer.ui.FloatingBottomBarTopMargin
import com.example.photoorganizer.ui.LocalOverlayPopupCount
import com.example.photoorganizer.ui.SyncSystemBarsWithTheme
import com.example.photoorganizer.ui.ThemeMode
import com.example.photoorganizer.ui.floatingBottomBarContentPadding
import com.example.photoorganizer.ui.rememberOverlayPopupCount
import com.example.photoorganizer.ui.components.AlbumDialog
import com.example.photoorganizer.ui.components.DiscardDialog
import com.example.photoorganizer.ui.components.MessageDialog
import com.example.photoorganizer.ui.navigation.GlassBottomBar
import com.example.photoorganizer.ui.resolveIsDark
import com.example.photoorganizer.ui.toColorSchemeMode
import com.example.photoorganizer.ui.themeModeFromName
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Application root: owns preferences, theme, the media index and page
 * navigation. Screens live in `screens/`. The liquid glass bottom bar samples
 * a backdrop that records ONLY the page Box, never the bar itself, so the
 * render tree stays acyclic.
 */
@Composable
fun PhotoOrganizerApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photo_organizer_preferences", Context.MODE_PRIVATE) }

    var themeMode by rememberPersisted(prefs, "theme_mode") { it ->
        themeModeFromName(it.getString("theme_mode", null)) ?: ThemeMode.AUTO
    }
    var animationEnabled by rememberPersisted(prefs, "animation_enabled") { it.getBoolean("animation_enabled", true) }
    var confirmDelete by rememberPersisted(prefs, "confirm_delete") { it.getBoolean("confirm_delete", true) }
    var defaultSortOrder by rememberPersisted(prefs, "default_sort") { it ->
        it.getString("default_sort", null)?.let { value -> SortOrder.entries.firstOrNull { e -> e.name == value } }
            ?: SortOrder.DATE
    }
    var imageQuality by rememberPersisted(prefs, "image_quality") { it.getInt("image_quality", 80) }
    var videoQuality by rememberPersisted(prefs, "video_quality") { it ->
        it.getString("video_quality", null)?.let { value -> VideoQuality.entries.firstOrNull { e -> e.name == value } }
            ?: VideoQuality.MEDIUM
    }
    var stripMetadata by rememberPersisted(prefs, "strip_metadata") { it.getBoolean("strip_metadata", false) }
    var largestThresholdMb by rememberPersisted(prefs, "largest_threshold_mb") {
        it.getInt("largest_threshold_mb", ToolAnalyzer.DefaultLargestThresholdMb)
    }
    var indexScope by remember { mutableStateOf(readIndexScope(prefs)) }

    val themeController = remember(themeMode) { ThemeController(themeMode.toColorSchemeMode()) }
    val isDark = resolveIsDark(themeMode)
    SyncSystemBarsWithTheme(isDark)

    val initialPermissionState = remember { context.mediaPermissionState() }
    var permissionState by remember { mutableStateOf(initialPermissionState) }
    val hasMediaPermission = permissionState.hasAccess
    var dashboardState by remember {
        mutableStateOf<DashboardState>(if (initialPermissionState.hasAccess) DashboardState.Scanning else DashboardState.NoPermission)
    }
    var availableAlbums by remember { mutableStateOf<List<String>>(emptyList()) }
    var scanRequest by remember { mutableIntStateOf(0) }
    val reviewStates = remember { mutableStateMapOf<Long, ReviewState>() }
    var pendingDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val deleteLaunchFailedText = stringResource(R.string.error_delete_launch_failed)
    val scanFailedText = stringResource(R.string.error_scan_failed)

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteIds.forEach { id ->
                reviewStates.remove(id)
                prefs.edit { remove("review_$id") }
            }
            scanRequest++
        }
        pendingDeleteIds = emptySet()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        permissionState = context.mediaPermissionState()
        scanRequest++
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPermissionState by rememberUpdatedState(permissionState)
    var hasResumedOnce by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val refreshed = context.mediaPermissionState()
                val permissionChanged = refreshed != latestPermissionState
                permissionState = refreshed
                if (hasResumedOnce && (permissionChanged || refreshed.isLimited)) {
                    scanRequest++
                }
                hasResumedOnce = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionState, scanRequest) {
        if (!permissionState.hasAccess) {
            dashboardState = DashboardState.NoPermission
            return@LaunchedEffect
        }
        dashboardState = DashboardState.Scanning
        dashboardState = runCatching {
            withContext(Dispatchers.IO) {
                MediaStoreIndexer(context.contentResolver).scan(
                    includeImages = permissionState.images || permissionState.selectedOnly,
                    includeVideos = permissionState.videos || permissionState.selectedOnly,
                    permissionLimited = permissionState.isLimited,
                    scope = indexScope,
                )
            }
        }.fold(
            onSuccess = { snapshot ->
                availableAlbums = snapshot.availableAlbums
                snapshot.items.forEach { item ->
                    val saved = prefs.getString("review_${item.id}", null)
                        ?.let { value -> ReviewState.entries.firstOrNull { it.name == value } }
                    reviewStates[item.id] = saved ?: reviewStates[item.id] ?: ReviewState.UNREVIEWED
                }
                DashboardState.Ready(
                    statistics = com.example.photoorganizer.media.MediaStatistics.from(snapshot),
                    scannedAtMillis = snapshot.scannedAtMillis,
                    permissionLimited = snapshot.permissionLimited,
                    items = snapshot.items,
                )
            },
            onFailure = { DashboardState.Error(it.message ?: scanFailedText) },
        )
    }

    val rawItems = (dashboardState as? DashboardState.Ready)?.items ?: emptyList()
    val mediaIndexReady = dashboardState is DashboardState.Ready
    val media = rawItems.map { it.toUiMedia(reviewStates[it.id] ?: ReviewState.UNREVIEWED) }
    var toolAnalysis by remember { mutableStateOf(ToolAnalysis.Empty) }
    var toolAnalysisReady by remember { mutableStateOf(false) }
    LaunchedEffect(rawItems, mediaIndexReady, largestThresholdMb) {
        toolAnalysisReady = false
        toolAnalysis = if (rawItems.isEmpty()) {
            ToolAnalysis.Empty
        } else {
            withContext(Dispatchers.IO) {
                ToolAnalyzer.analyze(
                    items = rawItems,
                    largestThresholdBytes = ToolAnalyzer.thresholdBytesOf(largestThresholdMb),
                    contentHashOf = { item -> ToolAnalyzer.contentHash(context.contentResolver, item.uri) },
                )
            }
        }
        toolAnalysisReady = mediaIndexReady
    }

    var selectedPage by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    var selectedMode by rememberSaveable { mutableStateOf<DetailMode?>(null) }
    var targetFilters by rememberSaveable(stateSaver = TargetFiltersSaver) { mutableStateOf(TargetFilters()) }
    var logicalAlbums by remember {
        mutableStateOf(LogicalAlbumStore.decode(prefs.getStringSet("logical_albums", emptySet())))
    }
    var duplicateGroup by remember { mutableStateOf<DuplicateGroup?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingDiscardIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var selectedMediaId by remember { mutableStateOf<Long?>(null) }

    val ffmpegInstallable = remember(context) { FfmpegEngine.isAvailable(context) }
    var ffmpegProbing by remember { mutableStateOf(ffmpegInstallable) }
    var ffmpegVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ffmpegInstallable) {
        if (!ffmpegInstallable) return@LaunchedEffect
        val version = withContext(Dispatchers.IO) { FfmpegEngine.probeVersion(context) }
        ffmpegVersion = version?.takeIf { it.isNotBlank() }
        ffmpegProbing = false
    }

    fun saveLogicalAlbums(albums: List<LogicalAlbum>) {
        logicalAlbums = albums.sortedBy { it.name.lowercase() }
        prefs.edit { putStringSet("logical_albums", LogicalAlbumStore.encode(logicalAlbums)) }
    }

    fun beginSystemDelete(markIds: Set<Long>) {
        val uris = media.filter { it.id in markIds && it.uri != null }.mapNotNull { it.uri }
        if (uris.isEmpty()) return
        runCatching {
            val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
            pendingDeleteIds = markIds
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure { deleteError = it.message ?: deleteLaunchFailedText }
    }

    fun markMedia(id: Long, state: ReviewState) {
        reviewStates[id] = state
        prefs.edit { putString("review_$id", state.name) }
    }

    fun requestDiscard(ids: Set<Long>) {
        val pendingIds = media
            .filter { it.state == ReviewState.TRASH_MARKED && it.id in ids }
            .map { it.id }
            .toSet()
        if (pendingIds.isEmpty()) return
        if (confirmDelete) {
            pendingDiscardIds = pendingIds
            showDiscardDialog = true
        } else {
            beginSystemDelete(pendingIds)
        }
    }

    val contentBottomPadding = floatingBottomBarContentPadding()
    var detailBackProgress by remember { mutableFloatStateOf(0f) }
    val predictiveBackOffset = with(LocalDensity.current) { 72.dp.toPx() }
    val detailEnterOffset = with(LocalDensity.current) { 18.dp.toPx() }
    val detailEnterProgress by animateFloatAsState(
        targetValue = if (selectedMode == null) 0f else 1f,
        animationSpec = if (animationEnabled) spring(dampingRatio = .82f, stiffness = 440f) else snap(),
        label = "detail-enter",
    )

    MiuixTheme(themeController) {
        PredictiveBackHandler(
            enabled = selectedMode != null && !showDiscardDialog && !showAlbumDialog && deleteError == null,
        ) { progress ->
            try {
                progress.collect { event -> detailBackProgress = event.progress }
                selectedMode = null
            } catch (cancellation: CancellationException) {
                // A cancelled gesture leaves the current screen in place.
                throw cancellation
            } finally {
                detailBackProgress = 0f
            }
        }

        val backdrop = rememberLayerBackdrop()
        val overlayPopupCount = rememberOverlayPopupCount()
        // MIUIX overlay popups are hosted by each page's Scaffold, so they draw
        // inside the captured content layer and end up beneath the floating
        // glass bar. Slide the bar away while a popup is open so a popup
        // anchored near the bottom of a list stays fully readable.
        val bottomBarHiddenProgress by animateFloatAsState(
            targetValue = if (overlayPopupCount.intValue > 0) 1f else 0f,
            animationSpec = if (animationEnabled) spring(dampingRatio = .9f, stiffness = 500f) else snap(),
            label = "bottom-bar-hide",
        )
        val bottomBarHideOffset = with(LocalDensity.current) {
            (FloatingBottomBarHeight + FloatingBottomBarTopMargin + FloatingBottomBarBottomMargin).toPx()
        }
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MiuixTheme.colorScheme.background),
            ) {
                CompositionLocalProvider(LocalOverlayPopupCount provides overlayPopupCount) {
                AnimatedContent(
                    targetState = selectedPage,
                    transitionSpec = {
                        if (animationEnabled) {
                            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = .985f)) togetherWith
                                fadeOut(tween(140))
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "root-page",
                ) { page ->
                when (page) {
                    AppPage.DASHBOARD -> DashboardScreen(
                        state = dashboardState,
                        reviewedCount = media.count { it.state == ReviewState.KEPT || it.state == ReviewState.TRASH_MARKED },
                        totalCount = (dashboardState as? DashboardState.Ready)?.statistics?.totalCount ?: 0,
                        toolAnalysis = toolAnalysis,
                        toolAnalysisReady = toolAnalysisReady,
                        contentBottomPadding = contentBottomPadding,
                        onRefresh = { scanRequest++ },
                        onRequestPermission = { permissionLauncher.launch(context.photoPermissionRequest()) },
                        onOpenOrganize = { selectedPage = AppPage.ORGANIZE },
                        onOpenTools = { selectedPage = AppPage.TOOLS },
                    )
                    AppPage.ORGANIZE -> OrganizeScreen(
                        contentBottomPadding = contentBottomPadding,
                        availableAlbums = availableAlbums,
                        keptCount = media.count { it.state == ReviewState.KEPT },
                        trashCount = media.count { it.state == ReviewState.TRASH_MARKED },
                        onOpenSmart = {
                            targetFilters = TargetFilters()
                            selectedMode = DetailMode.SWIPE
                        },
                        onOpenTargeted = { filters ->
                            targetFilters = filters
                            selectedMode = DetailMode.SWIPE
                        },
                        onOpenManual = { selectedMode = DetailMode.MANUAL },
                        onOpenKept = { selectedMode = DetailMode.KEPT },
                        onOpenTrash = { selectedMode = DetailMode.TRASH },
                    )
                    AppPage.TOOLS -> ToolsScreen(
                        analysis = toolAnalysis,
                        analysisReady = toolAnalysisReady,
                        contentBottomPadding = contentBottomPadding,
                        largestThresholdMb = largestThresholdMb,
                        onLargestThresholdChange = { largestThresholdMb = it },
                        onRefresh = { scanRequest++ },
                        onOpenDuplicates = { selectedMode = DetailMode.DUPLICATES },
                        onOpenScreenshots = { selectedMode = DetailMode.SCREENSHOTS },
                        onOpenLargest = { selectedMode = DetailMode.LARGEST },
                        onOpenMediaTools = { selectedMode = DetailMode.MEDIA },
                    )
                    AppPage.SETTINGS -> SettingsScreen(
                        hasMediaPermission = hasMediaPermission,
                        permissionLimited = permissionState.isLimited,
                        indexedCount = (dashboardState as? DashboardState.Ready)?.statistics?.totalCount ?: 0,
                        ffmpegVersion = ffmpegVersion,
                        themeMode = themeMode,
                        animationEnabled = animationEnabled,
                        confirmDelete = confirmDelete,
                        defaultSortOrder = defaultSortOrder,
                        imageQuality = imageQuality,
                        videoQuality = videoQuality,
                        stripMetadata = stripMetadata,
                        availableAlbums = availableAlbums,
                        indexScope = indexScope,
                        onThemeModeChange = { themeMode = it },
                        onDefaultSortChange = { defaultSortOrder = it },
                        onImageQualityChange = { imageQuality = it },
                        onVideoQualityChange = { videoQuality = it },
                        onStripMetadataChange = { stripMetadata = it },
                        onIndexScopeChange = { scope ->
                            indexScope = scope
                            saveIndexScope(prefs, scope)
                            scanRequest++
                        },
                        onAnimationChange = { animationEnabled = it },
                        onConfirmDeleteChange = { confirmDelete = it },
                        onRequestPermission = { permissionLauncher.launch(context.photoPermissionRequest()) },
                        onOpenAbout = { selectedMode = DetailMode.ABOUT },
                        contentBottomPadding = contentBottomPadding,
                    )
                }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = detailBackProgress * predictiveBackOffset
                            translationY = (1f - detailEnterProgress) * detailEnterOffset
                            val scale = .985f + detailEnterProgress * .015f - detailBackProgress * .018f
                            scaleX = scale
                            scaleY = scale
                            alpha = detailEnterProgress * (1f - detailBackProgress * .08f)
                        },
                ) {
                    when (selectedMode) {
                        DetailMode.SWIPE -> {
                            val filtered = remember(rawItems, targetFilters) {
                                applyTargetFilters(rawItems, targetFilters)
                            }
                            val queue = filtered
                                .map { it.toUiMedia(reviewStates[it.id] ?: ReviewState.UNREVIEWED) }
                                .filter { it.state == ReviewState.UNREVIEWED }
                            SwipeReviewScreen(
                                media = queue,
                                animationEnabled = animationEnabled,
                                onBack = { selectedMode = null; detailBackProgress = 0f },
                                onMark = ::markMedia,
                                onOpenAlbum = { selectedMediaId = it; showAlbumDialog = true },
                            )
                        }
                        DetailMode.MANUAL -> ManualGridScreen(
                            media = media,
                            defaultSortBySize = defaultSortOrder == SortOrder.SIZE,
                            onBack = { selectedMode = null },
                            onMark = ::markMedia,
                            animationEnabled = animationEnabled,
                        )
                        DetailMode.KEPT -> ManualGridScreen(
                            media = media.filter { it.state == ReviewState.KEPT },
                            defaultSortBySize = false,
                            onBack = { selectedMode = null },
                            onMark = ::markMedia,
                            animationEnabled = animationEnabled,
                            mode = MediaGridMode.KEPT,
                        )
                        DetailMode.TRASH -> ManualGridScreen(
                            media = media.filter { it.state == ReviewState.TRASH_MARKED },
                            defaultSortBySize = false,
                            onBack = { selectedMode = null },
                            onMark = ::markMedia,
                            animationEnabled = animationEnabled,
                            mode = MediaGridMode.TRASH,
                            onDeleteRequest = ::requestDiscard,
                        )
                        DetailMode.DUPLICATES -> DuplicateGroupsScreen(
                            groups = toolAnalysis.duplicates,
                            analysisReady = toolAnalysisReady,
                            onBack = { selectedMode = null },
                            onMark = ::markMedia,
                            onOpenGroup = { group ->
                                duplicateGroup = group
                                selectedMode = DetailMode.DUPLICATE_GROUP
                            },
                        )
                        DetailMode.DUPLICATE_GROUP -> {
                            val groupIds = duplicateGroup?.items?.map { it.id }?.toSet().orEmpty()
                            ManualGridScreen(
                                media = media.filter { it.id in groupIds },
                                defaultSortBySize = true,
                                onBack = { selectedMode = DetailMode.DUPLICATES },
                                onMark = ::markMedia,
                                animationEnabled = animationEnabled,
                                mode = MediaGridMode.DUPLICATE_GROUP,
                            )
                        }
                        DetailMode.SCREENSHOTS -> {
                            val screenshotIds = toolAnalysis.screenshots.map { it.id }.toSet()
                            ManualGridScreen(
                                media = media.filter { it.id in screenshotIds },
                                defaultSortBySize = true,
                                onBack = { selectedMode = null },
                                onMark = ::markMedia,
                                animationEnabled = animationEnabled,
                                mode = MediaGridMode.SCREENSHOTS,
                            )
                        }
                        DetailMode.LARGEST -> {
                            val largestIds = toolAnalysis.largest.map { it.id }.toSet()
                            ManualGridScreen(
                                media = media.filter { it.id in largestIds },
                                defaultSortBySize = true,
                                onBack = { selectedMode = null },
                                onMark = ::markMedia,
                                animationEnabled = animationEnabled,
                                mode = MediaGridMode.LARGEST,
                            )
                        }
                        DetailMode.MEDIA -> MediaToolsScreen(
                            imageQuality = imageQuality,
                            videoQuality = videoQuality,
                            stripMetadata = stripMetadata,
                            onBack = { selectedMode = null },
                            onMediaCreated = { scanRequest++ },
                            onOpenResult = { uri -> openMediaViewer(context, uri) },
                        )
                        DetailMode.ABOUT -> AboutScreen(
                            onBack = { selectedMode = null },
                        )
                        null -> Unit
                    }
                }
                }
            }

            if (selectedMode == null) {
                GlassBottomBar(
                    backdrop = backdrop,
                    selected = selectedPage,
                    onSelected = { selectedPage = it },
                    animationEnabled = animationEnabled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            translationY = bottomBarHiddenProgress * bottomBarHideOffset
                            alpha = 1f - bottomBarHiddenProgress
                        },
                )
            }

            if (showDiscardDialog) {
                val pending = media.filter {
                    it.state == ReviewState.TRASH_MARKED && it.id in pendingDiscardIds
                }
                DiscardDialog(
                    count = pending.size,
                    bytes = formatBytes(pending.sumOf { it.sizeBytes }),
                    onDismiss = {
                        showDiscardDialog = false
                        pendingDiscardIds = emptySet()
                    },
                    onConfirm = {
                        showDiscardDialog = false
                        pendingDiscardIds = emptySet()
                        beginSystemDelete(pending.map { it.id }.toSet())
                    },
                )
            }
            if (showAlbumDialog) {
                AlbumDialog(
                    mediaName = media.firstOrNull { it.id == selectedMediaId }?.displayName
                        ?: stringResource(R.string.default_album_name),
                    albums = logicalAlbums,
                    onAssign = { album ->
                        val id = selectedMediaId
                        if (id != null) {
                            val updated = album.copy(mediaIds = album.mediaIds + id)
                            saveLogicalAlbums(logicalAlbums.map { if (it.name == album.name) updated else it })
                        }
                        showAlbumDialog = false
                    },
                    onCreateAndAssign = { name ->
                        val id = selectedMediaId
                        val trimmed = name.trim()
                        if (id != null && trimmed.isNotEmpty()) {
                            val existing = logicalAlbums.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
                            val updated = existing?.copy(mediaIds = existing.mediaIds + id)
                                ?: LogicalAlbum(trimmed, setOf(id))
                            saveLogicalAlbums(
                                logicalAlbums.filterNot { it.name.equals(trimmed, ignoreCase = true) } + updated,
                            )
                        }
                        showAlbumDialog = false
                    },
                    onDismiss = { showAlbumDialog = false },
                )
            }
            deleteError?.let { message ->
                MessageDialog(
                    title = stringResource(R.string.error_title),
                    message = message,
                    onDismiss = { deleteError = null },
                )
            }
        }
    }
}

/** [remember] a [androidx.compose.runtime.MutableState] whose changes write back to [prefs]. */
@Composable
private fun <T> rememberPersisted(
    prefs: SharedPreferences,
    key: String,
    read: (SharedPreferences) -> T,
): androidx.compose.runtime.MutableState<T> {
    val initial = remember(key) { read(prefs) }
    val state = remember(key) { mutableStateOf(initial) }
    LaunchedEffect(state.value) {
        writePreference(prefs, key, state.value)
    }
    return state
}

private fun writePreference(prefs: SharedPreferences, key: String, value: Any?) {
    prefs.edit {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is String -> putString(key, value)
            is Enum<*> -> putString(key, value.name)
            else -> Unit
        }
    }
}

private enum class DetailMode {
    SWIPE,
    MANUAL,
    KEPT,
    TRASH,
    DUPLICATES,
    DUPLICATE_GROUP,
    SCREENSHOTS,
    LARGEST,
    MEDIA,
    ABOUT,
}

/** Hands a freshly created file to the system gallery viewer. */
private fun openMediaViewer(context: Context, uri: android.net.Uri) {
    val mimeType = context.contentResolver.getType(uri) ?: "image/*"
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}

private val TargetFiltersSaver = Saver<TargetFilters, List<String>>(
    save = { filters ->
        listOf(
            filters.albumPaths.joinToString("\u001F"),
            filters.startDateMillis?.toString().orEmpty(),
            filters.endDateMillis?.toString().orEmpty(),
            filters.type.name,
            filters.minSizeBytes?.toString().orEmpty(),
        )
    },
    restore = { values ->
        runCatching {
            TargetFilters(
                albumPaths = values[0].takeIf(String::isNotEmpty)?.split('\u001F')?.toSet().orEmpty(),
                startDateMillis = values[1].toLongOrNull(),
                endDateMillis = values[2].toLongOrNull(),
                type = TypeFilter.valueOf(values[3]),
                minSizeBytes = values[4].toLongOrNull(),
            )
        }.getOrDefault(TargetFilters())
    },
)

private fun readIndexScope(prefs: SharedPreferences): IndexScope {
    val mode = prefs.getString("index_scope_mode", null)
        ?.let { value -> IndexScopeMode.entries.firstOrNull { it.name == value } }
        ?: IndexScopeMode.ALL
    return IndexScope(mode, prefs.getStringSet("index_scope_albums", emptySet()).orEmpty())
}

private fun saveIndexScope(prefs: SharedPreferences, scope: IndexScope) {
    prefs.edit {
        putString("index_scope_mode", scope.mode.name)
        putStringSet("index_scope_albums", scope.albumPaths)
    }
}
