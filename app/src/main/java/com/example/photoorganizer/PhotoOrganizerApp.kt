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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photoorganizer.media.LogicalAlbum
import com.example.photoorganizer.media.LogicalAlbumStore
import com.example.photoorganizer.media.IndexedMedia
import com.example.photoorganizer.media.IndexScope
import com.example.photoorganizer.media.IndexScopeMode
import com.example.photoorganizer.media.MediaIndexViewModel
import com.example.photoorganizer.media.MediaStatistics
import com.example.photoorganizer.media.PendingMedia
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
import com.example.photoorganizer.media.reviewPreferenceKey
import com.example.photoorganizer.media.smartReviewOrder
import com.example.photoorganizer.media.toPendingMedia
import com.example.photoorganizer.media.toUiMedia
import com.example.photoorganizer.processing.VideoQuality
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
import com.example.photoorganizer.ui.TrackOverlayPopup
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
import top.yukonga.miuix.kmp.basic.Scaffold
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
    val indexViewModel: MediaIndexViewModel = viewModel()
    val indexState by indexViewModel.state.collectAsState()
    var scanRequest by remember { mutableIntStateOf(0) }
    // An immutable map rather than a SnapshotStateMap: a state map has no
    // per-key observability, so reading it in this scope invalidated the whole
    // root - and re-derived every list below - on every single mark. Swapping the
    // instance instead makes the `remember` keys further down a real cache.
    var reviewStates by remember { mutableStateOf<Map<Long, ReviewState>>(emptyMap()) }
    var pendingDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingDeleteReviewKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val deleteLaunchFailedText = stringResource(R.string.error_delete_launch_failed)
    val scanFailedText = stringResource(R.string.error_scan_failed)

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            reviewStates = reviewStates - pendingDeleteIds
            // One commit for the whole batch: the per-item form rewrote the
            // entire preference file once per deleted item.
            prefs.edit {
                pendingDeleteIds.forEach { id -> remove("review_$id") }
                pendingDeleteReviewKeys.forEach(::remove)
            }
            scanRequest++
        }
        pendingDeleteIds = emptySet()
        pendingDeleteReviewKeys = emptySet()
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

    LaunchedEffect(permissionState, scanRequest, indexScope) {
        indexViewModel.refresh(permissionState, indexScope)
    }

    val statistics = remember(indexState.snapshot) {
        indexState.snapshot?.let { snapshot -> MediaStatistics.from(snapshot) }
    }
    val dashboardState: DashboardState = when {
        !permissionState.hasAccess -> DashboardState.NoPermission
        indexState.scanning || indexState.snapshot == null && indexState.error == null -> DashboardState.Scanning
        indexState.error != null -> DashboardState.Error(indexState.error?.message ?: scanFailedText)
        else -> indexState.snapshot!!.let { snapshot ->
            DashboardState.Ready(
                statistics = statistics ?: MediaStatistics(),
                scannedAtMillis = snapshot.scannedAtMillis,
                permissionLimited = snapshot.permissionLimited,
                items = snapshot.items,
            )
        }
    }
    val rawItems = indexState.snapshot?.items ?: emptyList()
    val mediaIndexReady = dashboardState is DashboardState.Ready
    val duplicateAnalysisReady = mediaIndexReady && !indexState.analyzingDuplicates
    val availableAlbums = indexState.snapshot?.availableAlbums ?: emptyList()
    LaunchedEffect(indexState.snapshot, indexScope) {
        val snapshot = indexState.snapshot ?: return@LaunchedEffect
        // Reading one preference per item, with a migration write behind it, is
        // O(library) work: it stays off the main thread and lands as a single
        // commit plus a single state assignment.
        val hydrated = withContext(Dispatchers.Default) {
            hydrateReviewStates(
                prefs = prefs,
                items = snapshot.items,
                pruneStaleKeys = indexScope.mode == IndexScopeMode.ALL && !snapshot.permissionLimited,
            )
        }
        reviewStates = hydrated
    }
    val media = remember(rawItems, reviewStates) {
        rawItems.map { it.toUiMedia(reviewStates[it.id] ?: ReviewState.UNREVIEWED) }
    }
    val itemsById = remember(rawItems) { rawItems.associateBy { it.id } }
    val keptCount = remember(media) { media.count { it.state == ReviewState.KEPT } }
    val trashCount = remember(media) { media.count { it.state == ReviewState.TRASH_MARKED } }
    val toolAnalysisReady = duplicateAnalysisReady
    val toolAnalysis = remember(indexState.duplicateGroups, rawItems, largestThresholdMb) {
        val thresholdBytes = ToolAnalyzer.thresholdBytesOf(largestThresholdMb)
        ToolAnalysis(
            duplicates = indexState.duplicateGroups,
            screenshots = ToolAnalyzer.findScreenshots(rawItems),
            largest = ToolAnalyzer.findLargest(rawItems, thresholdBytes),
            largestThresholdBytes = thresholdBytes,
        )
    }

    var selectedPage by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    var selectedMode by rememberSaveable { mutableStateOf<DetailMode?>(null) }
    var targetFilters by rememberSaveable(stateSaver = TargetFiltersSaver) { mutableStateOf(TargetFilters()) }
    var smartQueue by rememberSaveable { mutableStateOf(false) }
    var logicalAlbums by remember {
        mutableStateOf(LogicalAlbumStore.decode(prefs.getStringSet("logical_albums", emptySet())))
    }
    var duplicateGroup by remember { mutableStateOf<DuplicateGroup?>(null) }
    // A group grid is reachable from both the exact and the similar list, so the
    // back target follows whichever list opened it.
    var groupReturnMode by rememberSaveable { mutableStateOf(DetailMode.DUPLICATES) }
    // Only the name is held, and the album is derived from `logicalAlbums`: that
    // keeps a single source of truth and lets the selection survive a
    // process-death restore, which `selectedMode` already does.
    var selectedAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedLogicalAlbum = remember(logicalAlbums, selectedAlbumName) {
        selectedAlbumName?.let { name -> logicalAlbums.firstOrNull { it.name == name } }
    }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingDiscardIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var selectedMediaId by remember { mutableStateOf<Long?>(null) }
    // Items handed from a gallery grid to the processing tools, so an analysis
    // result can be compressed without re-picking it through the system picker.
    var pendingProcessing by remember { mutableStateOf<List<PendingMedia>>(emptyList()) }

    fun saveLogicalAlbums(albums: List<LogicalAlbum>) {
        logicalAlbums = albums.sortedBy { it.name.lowercase() }
        prefs.edit { putStringSet("logical_albums", LogicalAlbumStore.encode(logicalAlbums)) }
    }

    // A hand-off only lives as long as the tools page is open, so leaving it by
    // any route - button, system back or the predictive back gesture - drops it.
    LaunchedEffect(selectedMode) {
        if (selectedMode != DetailMode.MEDIA) pendingProcessing = emptyList()
    }

    LaunchedEffect(rawItems, mediaIndexReady, indexScope) {
        if (!mediaIndexReady || indexScope.mode != IndexScopeMode.ALL || permissionState.isLimited) return@LaunchedEffect
        val activeIds = rawItems.mapTo(hashSetOf()) { it.id }
        val pruned = logicalAlbums.map { album -> album.copy(mediaIds = album.mediaIds intersect activeIds) }
        if (pruned != logicalAlbums) saveLogicalAlbums(pruned)
    }

    fun beginSystemDelete(markIds: Set<Long>) {
        val uris = media.filter { it.id in markIds && it.uri != null }.mapNotNull { it.uri }
        if (uris.isEmpty()) return
        runCatching {
            val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
            pendingDeleteIds = markIds
            pendingDeleteReviewKeys = rawItems
                .filter { it.id in markIds }
                .mapTo(hashSetOf()) { it.reviewPreferenceKey() }
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure { deleteError = it.message ?: deleteLaunchFailedText }
    }

    /**
     * Applies a whole batch of review decisions at once.
     *
     * Batch-shaped rather than per-item because every bulk action in the grid -
     * select-all plus discard, "mark all discarded", a duplicate cleanup plan -
     * used to call a single-item callback in a loop. That cost one full copy of
     * [reviewStates] and one rewrite of the preference file per item, so
     * discarding a 20k-item selection was quadratic work on the main thread.
     */
    fun markMedia(decisions: Map<Long, ReviewState>) {
        if (decisions.isEmpty()) return
        reviewStates = reviewStates + decisions
        prefs.edit {
            decisions.forEach { (id, state) ->
                itemsById[id]?.let { item -> putString(item.reviewPreferenceKey(), state.name) }
                remove("review_$id")
            }
        }
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

    /** Hands a grid selection to the processing tools instead of the system picker. */
    fun compressSelection(ids: Set<Long>) {
        val items = media.filter { it.id in ids }.mapNotNull { it.toPendingMedia() }
        if (items.isEmpty()) return
        pendingProcessing = items
        selectedMode = DetailMode.MEDIA
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
        CompositionLocalProvider(LocalOverlayPopupCount provides overlayPopupCount) {
        // Root MIUIX Scaffold. It publishes LocalRootDialogStates /
        // LocalRootPopupStates for the whole app, so every overlay declared by a
        // page outside of that page's own Scaffold still finds a host. The
        // Scaffold layout places its popup host last, which keeps those overlays
        // above both the page content and the floating glass bar.
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MiuixTheme.colorScheme.background,
        ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MiuixTheme.colorScheme.background),
            ) {
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
                        reviewedCount = keptCount + trashCount,
                        totalCount = (dashboardState as? DashboardState.Ready)?.statistics?.totalCount ?: 0,
                        toolAnalysis = toolAnalysis,
                        toolAnalysisReady = toolAnalysisReady,
                        contentBottomPadding = contentBottomPadding,
                        onRefresh = { scanRequest++ },
                        onRequestPermission = { permissionLauncher.launch(context.photoPermissionRequest()) },
                        onOpenOrganize = { selectedPage = AppPage.ORGANIZE },
                        onOpenTools = { selectedPage = AppPage.TOOLS },
                        onOpenSafety = { selectedPage = AppPage.SETTINGS },
                    )
                    AppPage.ORGANIZE -> OrganizeScreen(
                        contentBottomPadding = contentBottomPadding,
                        availableAlbums = availableAlbums,
                        keptCount = keptCount,
                        trashCount = trashCount,
                        logicalAlbums = logicalAlbums,
                        onOpenSmart = {
                            targetFilters = TargetFilters()
                            smartQueue = true
                            selectedMode = DetailMode.SWIPE
                        },
                        onOpenTargeted = { filters ->
                            targetFilters = filters
                            smartQueue = false
                            selectedMode = DetailMode.SWIPE
                        },
                        onOpenManual = { selectedMode = DetailMode.MANUAL },
                        onOpenKept = { selectedMode = DetailMode.KEPT },
                        onOpenTrash = { selectedMode = DetailMode.TRASH },
                        onOpenLogicalAlbum = { album ->
                            selectedAlbumName = album.name
                            selectedMode = DetailMode.LOGICAL_ALBUM
                        },
                    )
                    AppPage.TOOLS -> ToolsScreen(
                        analysis = toolAnalysis,
                        hasPermission = hasMediaPermission,
                        indexReady = mediaIndexReady,
                        duplicateAnalysisReady = duplicateAnalysisReady,
                        similar = indexState.similar,
                        contentBottomPadding = contentBottomPadding,
                        largestThresholdMb = largestThresholdMb,
                        onLargestThresholdChange = { largestThresholdMb = it },
                        onRefresh = { scanRequest++ },
                        onRequestPermission = { permissionLauncher.launch(context.photoPermissionRequest()) },
                        onOpenDuplicates = { selectedMode = DetailMode.DUPLICATES },
                        onOpenScreenshots = { selectedMode = DetailMode.SCREENSHOTS },
                        onOpenLargest = { selectedMode = DetailMode.LARGEST },
                        onOpenMediaTools = { selectedMode = DetailMode.MEDIA },
                        onAnalyzeSimilar = indexViewModel::analyzeSimilar,
                        onCancelSimilarAnalysis = indexViewModel::cancelSimilarAnalysis,
                        onOpenSimilar = { selectedMode = DetailMode.SIMILAR },
                    )
                    AppPage.SETTINGS -> SettingsScreen(
                        hasMediaPermission = hasMediaPermission,
                        permissionLimited = permissionState.isLimited,
                        indexedCount = (dashboardState as? DashboardState.Ready)?.statistics?.totalCount ?: 0,
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
                            val filtered = remember(rawItems, targetFilters, smartQueue, toolAnalysis) {
                                val scoped = applyTargetFilters(rawItems, targetFilters)
                                if (!smartQueue) {
                                    scoped
                                } else {
                                    smartReviewOrder(
                                        items = scoped,
                                        duplicates = toolAnalysis.duplicates,
                                        screenshots = toolAnalysis.screenshots,
                                        largest = toolAnalysis.largest,
                                    )
                                }
                            }
                            val queue = remember(filtered, reviewStates) {
                                filtered
                                    .map { it.toUiMedia(reviewStates[it.id] ?: ReviewState.UNREVIEWED) }
                                    .filter { it.state == ReviewState.UNREVIEWED }
                            }
                            SwipeReviewScreen(
                                media = queue,
                                animationEnabled = animationEnabled,
                                title = stringResource(
                                    if (smartQueue) R.string.organize_mode_smart else R.string.organize_mode_targeted,
                                ),
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
                            onCompressSelected = ::compressSelection,
                        )
                        DetailMode.KEPT -> ManualGridScreen(
                            media = remember(media) { media.filter { it.state == ReviewState.KEPT } },
                            defaultSortBySize = false,
                            onBack = { selectedMode = null },
                            onMark = ::markMedia,
                            animationEnabled = animationEnabled,
                            mode = MediaGridMode.KEPT,
                            onCompressSelected = ::compressSelection,
                        )
                        DetailMode.TRASH -> ManualGridScreen(
                            media = remember(media) { media.filter { it.state == ReviewState.TRASH_MARKED } },
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
                                groupReturnMode = DetailMode.DUPLICATES
                                selectedMode = DetailMode.DUPLICATE_GROUP
                            },
                        )
                        DetailMode.SIMILAR -> DuplicateGroupsScreen(
                            groups = indexState.similar.groups,
                            analysisReady = indexState.similar.isReady,
                            onBack = { selectedMode = null },
                            onMark = ::markMedia,
                            onOpenGroup = { group ->
                                duplicateGroup = group
                                groupReturnMode = DetailMode.SIMILAR
                                selectedMode = DetailMode.DUPLICATE_GROUP
                            },
                            title = stringResource(R.string.tools_similar_title),
                            hint = stringResource(R.string.tools_similar_hint),
                            emptyTitle = stringResource(R.string.tools_similar_empty),
                            countLabel = { count, saved ->
                                pluralStringResource(R.plurals.tools_summary_similar, count, count, saved)
                            },
                        )
                        DetailMode.DUPLICATE_GROUP -> {
                            val group = duplicateGroup
                            if (group == null) {
                                // `selectedMode` is saveable but the group it points at is
                                // not, so a process-death restore would land on an empty,
                                // untitled grid. Fall back to the list that opened it.
                                LaunchedEffect(Unit) { selectedMode = groupReturnMode }
                            } else {
                                val groupIds = remember(group) { group.items.mapTo(hashSetOf()) { it.id } }
                                ManualGridScreen(
                                    media = remember(media, groupIds) { media.filter { it.id in groupIds } },
                                    defaultSortBySize = true,
                                    onBack = { selectedMode = groupReturnMode },
                                    onMark = ::markMedia,
                                    animationEnabled = animationEnabled,
                                    mode = MediaGridMode.DUPLICATE_GROUP,
                                    onCompressSelected = ::compressSelection,
                                )
                            }
                        }
                        DetailMode.SCREENSHOTS -> {
                            val screenshotIds = remember(toolAnalysis) {
                                toolAnalysis.screenshots.mapTo(hashSetOf()) { it.id }
                            }
                            ManualGridScreen(
                                media = remember(media, screenshotIds) { media.filter { it.id in screenshotIds } },
                                defaultSortBySize = true,
                                onBack = { selectedMode = null },
                                onMark = ::markMedia,
                                animationEnabled = animationEnabled,
                                mode = MediaGridMode.SCREENSHOTS,
                                onCompressSelected = ::compressSelection,
                            )
                        }
                        DetailMode.LARGEST -> {
                            val largestIds = remember(toolAnalysis) {
                                toolAnalysis.largest.mapTo(hashSetOf()) { it.id }
                            }
                            ManualGridScreen(
                                media = remember(media, largestIds) { media.filter { it.id in largestIds } },
                                defaultSortBySize = true,
                                onBack = { selectedMode = null },
                                onMark = ::markMedia,
                                animationEnabled = animationEnabled,
                                mode = MediaGridMode.LARGEST,
                                onCompressSelected = ::compressSelection,
                            )
                        }
                        DetailMode.MEDIA -> MediaToolsScreen(
                            imageQuality = imageQuality,
                            videoQuality = videoQuality,
                            stripMetadata = stripMetadata,
                            onBack = { selectedMode = null },
                            onMediaCreated = { scanRequest++ },
                            onOpenResult = { uri -> openMediaViewer(context, uri) },
                            preselected = pendingProcessing,
                            onClearPreselected = { pendingProcessing = emptyList() },
                        )
                        DetailMode.LOGICAL_ALBUM -> {
                            if (selectedLogicalAlbum == null) {
                                // The album was deleted, or a process-death restore brought
                                // back the mode without a resolvable album name.
                                LaunchedEffect(Unit) { selectedMode = null }
                            } else {
                                val album = selectedLogicalAlbum
                                val ids = album.mediaIds
                                ManualGridScreen(
                                    media = remember(media, ids) { media.filter { it.id in ids } },
                                    defaultSortBySize = false,
                                    onBack = { selectedMode = null },
                                    onMark = ::markMedia,
                                    animationEnabled = animationEnabled,
                                    mode = MediaGridMode.LOGICAL_ALBUM,
                                    titleOverride = album.name,
                                    onCompressSelected = ::compressSelection,
                                    // No local copy of the album is kept: saving updates
                                    // `logicalAlbums`, which the selection derives from.
                                    onRemoveFromCollection = { removedIds ->
                                        val updated = album.copy(mediaIds = album.mediaIds - removedIds)
                                        saveLogicalAlbums(
                                            logicalAlbums.map { if (it.name == album.name) updated else it },
                                        )
                                    },
                                    onDeleteCollection = {
                                        saveLogicalAlbums(logicalAlbums.filterNot { it.name == album.name })
                                        selectedAlbumName = null
                                        selectedMode = null
                                    },
                                )
                            }
                        }
                        DetailMode.ABOUT -> AboutScreen(
                            onBack = { selectedMode = null },
                        )
                        null -> Unit
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

            // App-level dialogs. They render through the root Scaffold's popup
            // host, so they layer above the page content and the glass bar.
            run {
                val pending = remember(media, pendingDiscardIds) {
                    media.filter { it.state == ReviewState.TRASH_MARKED && it.id in pendingDiscardIds }
                }
                // The dialog stays composed through its exit animation, so the
                // summary keeps rendering the last known selection after the
                // pending set is cleared.
                var discardCount by remember { mutableIntStateOf(0) }
                var discardBytes by remember { mutableStateOf("") }
                val pendingBytes = formatBytes(pending.sumOf { it.sizeBytes })
                LaunchedEffect(showDiscardDialog, pending.size, pendingBytes) {
                    if (showDiscardDialog) {
                        discardCount = pending.size
                        discardBytes = pendingBytes
                    }
                }
                DiscardDialog(
                    show = showDiscardDialog,
                    count = discardCount,
                    bytes = discardBytes,
                    onDismiss = {
                        showDiscardDialog = false
                        pendingDiscardIds = emptySet()
                    },
                    onConfirm = {
                        val ids = pending.map { it.id }.toSet()
                        showDiscardDialog = false
                        pendingDiscardIds = emptySet()
                        beginSystemDelete(ids)
                    },
                )
                AlbumDialog(
                    show = showAlbumDialog,
                    mediaName = selectedMediaId?.let { itemsById[it] }?.displayName
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
                var errorMessage by remember { mutableStateOf("") }
                LaunchedEffect(deleteError) { deleteError?.let { errorMessage = it } }
                MessageDialog(
                    show = deleteError != null,
                    title = stringResource(R.string.error_title),
                    message = errorMessage,
                    onDismiss = { deleteError = null },
                )
            }
        }
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

/**
 * Reads every item's persisted review decision, migrating legacy `review_<id>`
 * keys and dropping keys whose media is gone. Every preference mutation is
 * batched into a single commit, because the per-item form rewrote the whole
 * preference file once per item.
 *
 * Runs off the main thread; the caller assigns the result in one state write.
 */
private fun hydrateReviewStates(
    prefs: SharedPreferences,
    items: List<IndexedMedia>,
    pruneStaleKeys: Boolean,
): Map<Long, ReviewState> {
    val statesByName = ReviewState.entries.associateBy { it.name }
    val states = HashMap<Long, ReviewState>(items.size)
    val activeKeys = HashSet<String>(items.size)
    val migrated = LinkedHashMap<String, String>()
    val legacyKeys = ArrayList<String>()
    items.forEach { item ->
        val key = item.reviewPreferenceKey()
        activeKeys += key
        val saved = prefs.getString(key, null) ?: run {
            val legacyKey = "review_${item.id}"
            prefs.getString(legacyKey, null)?.also { legacy ->
                migrated[key] = legacy
                legacyKeys += legacyKey
            }
        }
        states[item.id] = saved?.let { value -> statesByName[value] } ?: ReviewState.UNREVIEWED
    }
    // Read before the edit, so freshly migrated keys are never seen as stale.
    val staleKeys = if (pruneStaleKeys) {
        prefs.all.keys.filter { it.startsWith("review_") && it !in activeKeys && it !in legacyKeys }
    } else {
        emptyList()
    }
    if (migrated.isNotEmpty() || legacyKeys.isNotEmpty() || staleKeys.isNotEmpty()) {
        prefs.edit {
            migrated.forEach { (key, value) -> putString(key, value) }
            legacyKeys.forEach(::remove)
            staleKeys.forEach(::remove)
        }
    }
    return states
}

private enum class DetailMode {
    SWIPE,
    MANUAL,
    KEPT,
    TRASH,
    DUPLICATES,
    SIMILAR,
    DUPLICATE_GROUP,
    SCREENSHOTS,
    LARGEST,
    MEDIA,
    LOGICAL_ALBUM,
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
