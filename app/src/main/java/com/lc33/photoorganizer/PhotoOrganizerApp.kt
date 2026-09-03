package com.lc33.photoorganizer

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.lc33.photoorganizer.media.LogicalAlbum
import com.lc33.photoorganizer.media.LogicalAlbumStore
import com.lc33.photoorganizer.media.IndexedMedia
import com.lc33.photoorganizer.media.IndexScope
import com.lc33.photoorganizer.media.IndexScopeMode
import com.lc33.photoorganizer.media.MediaIndexViewModel
import com.lc33.photoorganizer.media.MediaStatistics
import com.lc33.photoorganizer.media.ReviewDecisionStore
import com.lc33.photoorganizer.media.ReviewState
import com.lc33.photoorganizer.media.shouldCompactLog
import com.lc33.photoorganizer.media.TargetFilters
import com.lc33.photoorganizer.media.ToolAnalysis
import com.lc33.photoorganizer.media.ToolAnalyzer
import com.lc33.photoorganizer.media.applyTargetFilters
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.media.mediaPermissionState
import com.lc33.photoorganizer.media.photoPermissionRequest
import com.lc33.photoorganizer.media.reviewKey
import com.lc33.photoorganizer.media.smartReviewOrder
import com.lc33.photoorganizer.media.toPendingMedia
import com.lc33.photoorganizer.media.toUiMedia
import com.lc33.photoorganizer.processing.MediaBatchViewModel
import com.lc33.photoorganizer.processing.VideoQuality
import com.lc33.photoorganizer.screens.dashboard.DashboardScreen
import com.lc33.photoorganizer.screens.dashboard.DashboardState
import com.lc33.photoorganizer.screens.organize.OrganizeScreen
import com.lc33.photoorganizer.screens.review.ManualGridScreen
import com.lc33.photoorganizer.screens.review.MediaGridMode
import com.lc33.photoorganizer.screens.review.SwipeReviewScreen
import com.lc33.photoorganizer.screens.settings.AboutScreen
import com.lc33.photoorganizer.screens.settings.SettingsScreen
import com.lc33.photoorganizer.screens.settings.SortOrder
import com.lc33.photoorganizer.screens.tools.DuplicateGroupsScreen
import com.lc33.photoorganizer.screens.tools.MediaToolsScreen
import com.lc33.photoorganizer.screens.tools.ToolsScreen
import com.lc33.photoorganizer.ui.AppPage
import com.lc33.photoorganizer.ui.DetailScreen
import com.lc33.photoorganizer.ui.DetailStackSaver
import com.lc33.photoorganizer.ui.FloatingBottomBarBottomMargin
import com.lc33.photoorganizer.ui.FloatingBottomBarHeight
import com.lc33.photoorganizer.ui.FloatingBottomBarTopMargin
import com.lc33.photoorganizer.ui.LocalOverlayPopupCount
import com.lc33.photoorganizer.ui.SyncSystemBarsWithTheme
import com.lc33.photoorganizer.ui.ThemeMode
import com.lc33.photoorganizer.ui.TrackOverlayPopup
import com.lc33.photoorganizer.ui.floatingBottomBarContentPadding
import com.lc33.photoorganizer.ui.rememberOverlayPopupCount
import com.lc33.photoorganizer.ui.components.AlbumDialog
import com.lc33.photoorganizer.ui.components.DiscardDialog
import com.lc33.photoorganizer.ui.components.MessageDialog
import com.lc33.photoorganizer.ui.navigation.GlassBottomBar
import com.lc33.photoorganizer.ui.resolveIsDark
import com.lc33.photoorganizer.ui.toColorSchemeMode
import com.lc33.photoorganizer.ui.themeModeFromName
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    // Hoisted here rather than obtained inside MediaToolsScreen so the state root
    // is actually the root. Both ViewModels resolve to the Activity's store either
    // way - there is no navigation library to scope them - but reaching for
    // `viewModel()` inside a screen made a transcode's survival across a detail
    // pop look like a property of that screen instead of a deliberate decision.
    // It has to stay a ViewModel: on a `rememberCoroutineScope()` a rotation
    // cancelled a minutes-long transcode silently.
    val batchViewModel: MediaBatchViewModel = viewModel()
    var scanRequest by remember { mutableIntStateOf(0) }
    // An immutable map rather than a SnapshotStateMap: a state map has no
    // per-key observability, so reading it in this scope invalidated the whole
    // root - and re-derived every list below - on every single mark. Swapping the
    // instance instead makes the `remember` keys further down a real cache.
    var reviewStates by remember { mutableStateOf<Map<Long, ReviewState>>(emptyMap()) }
    // Review decisions live in their own append-only log rather than as one
    // SharedPreferences key per item; see ReviewDecisionStore for why.
    val reviewStore = remember(context) {
        ReviewDecisionStore(File(context.filesDir, "review-decisions.tsv"))
    }
    val reviewScope = rememberCoroutineScope()
    var pendingDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingDeleteReviewKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val deleteLaunchFailedText = stringResource(R.string.error_delete_launch_failed)
    val scanFailedText = stringResource(R.string.error_scan_failed)

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            reviewStates = reviewStates - pendingDeleteIds
            // Cleared rather than removed: an append-only log cancels an entry by
            // recording the default, and compaction drops it later.
            val cleared = pendingDeleteReviewKeys.associateWith { ReviewState.UNREVIEWED }
            reviewScope.launch { withContext(Dispatchers.IO) { reviewStore.append(cleared) } }
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
        // Reading the log, migrating any legacy preference keys and deciding whether
        // to compact is all O(library), so it stays off the main thread and lands as
        // a single state assignment.
        val hydrated = withContext(Dispatchers.IO) {
            hydrateReviewStates(
                store = reviewStore,
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
    // A stack rather than a single destination: a grid opened from a duplicate
    // list, and the processing tools opened from that grid, are the same kind of
    // push, so back is one pop at every depth instead of a per-screen return
    // target. Saveable, because nothing else holds the detail layer across a
    // rotation.
    var detailStack by rememberSaveable(stateSaver = DetailStackSaver) {
        mutableStateOf<List<DetailScreen>>(emptyList())
    }
    val detail = detailStack.lastOrNull()
    var logicalAlbums by remember {
        mutableStateOf(LogicalAlbumStore.decode(prefs.getStringSet("logical_albums", emptySet())))
    }
    // Resolved from the name in the destination, so `logicalAlbums` stays the one
    // source of truth and a rename or delete cannot leave a stale copy on screen.
    val selectedLogicalAlbum = remember(logicalAlbums, detail) {
        (detail as? DetailScreen.LogicalAlbumGrid)?.let { target ->
            logicalAlbums.firstOrNull { it.name == target.albumName }
        }
    }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingDiscardIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var selectedMediaId by remember { mutableStateOf<Long?>(null) }

    fun openDetail(screen: DetailScreen) {
        detailStack = detailStack + screen
    }

    /** Swaps the open destination without deepening the stack, so back is unchanged. */
    fun replaceDetail(screen: DetailScreen) {
        detailStack = detailStack.dropLast(1) + screen
    }

    fun popDetail() {
        detailStack = detailStack.dropLast(1)
    }

    fun saveLogicalAlbums(albums: List<LogicalAlbum>) {
        logicalAlbums = albums.sortedBy { it.name.lowercase() }
        prefs.edit { putStringSet("logical_albums", LogicalAlbumStore.encode(logicalAlbums)) }
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
                .mapTo(hashSetOf()) { it.reviewKey() }
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
        val byKey = LinkedHashMap<String, ReviewState>(decisions.size)
        decisions.forEach { (id, state) ->
            itemsById[id]?.let { item -> byKey[item.reviewKey()] = state }
        }
        // Appended off the main thread: the decision is already applied in memory,
        // so the write is durability rather than something the UI waits on.
        reviewScope.launch { withContext(Dispatchers.IO) { reviewStore.append(byKey) } }
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
        // Pushed rather than swapped: back should return to the grid the selection
        // was made in, which is the fourth level this stack exists for.
        openDetail(DetailScreen.MediaProcessing(items))
    }

    val contentBottomPadding = floatingBottomBarContentPadding()
    var detailBackProgress by remember { mutableFloatStateOf(0f) }
    val predictiveBackOffset = with(LocalDensity.current) { 72.dp.toPx() }
    val detailEnterOffset = with(LocalDensity.current) { 18.dp.toPx() }
    val detailEnterProgress by animateFloatAsState(
        targetValue = if (detail == null) 0f else 1f,
        animationSpec = if (animationEnabled) spring(dampingRatio = .82f, stiffness = 440f) else snap(),
        label = "detail-enter",
    )

    MiuixTheme(themeController) {
        PredictiveBackHandler(
            enabled = detail != null && !showDiscardDialog && !showAlbumDialog && deleteError == null,
        ) { progress ->
            try {
                progress.collect { event -> detailBackProgress = event.progress }
                // One pop per gesture, so a fourth-level screen falls back to the
                // third rather than all the way out to the page layer.
                popDetail()
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
                        totalCount = media.size,
                        keptCount = keptCount,
                        trashCount = trashCount,
                        logicalAlbums = logicalAlbums,
                        onOpenSmart = {
                            openDetail(DetailScreen.Swipe(filters = TargetFilters(), smartOrder = true))
                        },
                        onOpenTargeted = { filters ->
                            openDetail(DetailScreen.Swipe(filters = filters, smartOrder = false))
                        },
                        onOpenManual = { openDetail(DetailScreen.Manual) },
                        onOpenKept = { openDetail(DetailScreen.Kept) },
                        onOpenTrash = { openDetail(DetailScreen.Trash) },
                        onOpenLogicalAlbum = { album ->
                            openDetail(DetailScreen.LogicalAlbumGrid(album.name))
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
                        onOpenDuplicates = { openDetail(DetailScreen.Duplicates) },
                        onOpenScreenshots = { openDetail(DetailScreen.Screenshots) },
                        onOpenLargest = { openDetail(DetailScreen.Largest) },
                        onOpenMediaTools = { openDetail(DetailScreen.MediaProcessing(emptyList())) },
                        onAnalyzeSimilar = indexViewModel::analyzeSimilar,
                        onCancelSimilarAnalysis = indexViewModel::cancelSimilarAnalysis,
                        onOpenSimilar = { openDetail(DetailScreen.Similar) },
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
                        onOpenAbout = { openDetail(DetailScreen.About) },
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
                    when (detail) {
                        is DetailScreen.Swipe -> {
                            val filters = detail.filters
                            val smartOrder = detail.smartOrder
                            val filtered = remember(rawItems, filters, smartOrder, toolAnalysis) {
                                val scoped = applyTargetFilters(rawItems, filters)
                                if (!smartOrder) {
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
                                    if (smartOrder) R.string.organize_mode_smart else R.string.organize_mode_targeted,
                                ),
                                onBack = { popDetail(); detailBackProgress = 0f },
                                onMark = ::markMedia,
                                onOpenAlbum = { selectedMediaId = it; showAlbumDialog = true },
                            )
                        }
                        DetailScreen.Manual -> ManualGridScreen(
                            media = media,
                            defaultSortBySize = defaultSortOrder == SortOrder.SIZE,
                            onBack = ::popDetail,
                            onMark = ::markMedia,
                            animationEnabled = animationEnabled,
                            onCompressSelected = ::compressSelection,
                        )
                        DetailScreen.Kept -> ManualGridScreen(
                            media = remember(media) { media.filter { it.state == ReviewState.KEPT } },
                            defaultSortBySize = false,
                            onBack = ::popDetail,
                            onMark = ::markMedia,
                            animationEnabled = animationEnabled,
                            mode = MediaGridMode.KEPT,
                            onCompressSelected = ::compressSelection,
                        )
                        DetailScreen.Trash -> ManualGridScreen(
                            media = remember(media) { media.filter { it.state == ReviewState.TRASH_MARKED } },
                            defaultSortBySize = false,
                            onBack = ::popDetail,
                            onMark = ::markMedia,
                            animationEnabled = animationEnabled,
                            mode = MediaGridMode.TRASH,
                            onDeleteRequest = ::requestDiscard,
                        )
                        DetailScreen.Duplicates -> DuplicateGroupsScreen(
                            groups = toolAnalysis.duplicates,
                            analysisReady = toolAnalysisReady,
                            onBack = ::popDetail,
                            onMark = ::markMedia,
                            onOpenGroup = { group -> openDetail(DetailScreen.DuplicateGroupGrid(group)) },
                        )
                        DetailScreen.Similar -> DuplicateGroupsScreen(
                            groups = indexState.similar.groups,
                            analysisReady = indexState.similar.isReady,
                            onBack = ::popDetail,
                            onMark = ::markMedia,
                            onOpenGroup = { group -> openDetail(DetailScreen.DuplicateGroupGrid(group)) },
                            title = stringResource(R.string.tools_similar_title),
                            hint = stringResource(R.string.tools_similar_hint),
                            emptyTitle = stringResource(R.string.tools_similar_empty),
                            countLabel = { count, saved ->
                                pluralStringResource(R.plurals.tools_summary_similar, count, count, saved)
                            },
                        )
                        is DetailScreen.DuplicateGroupGrid -> {
                            // No missing-group fallback: the stack carries the group,
                            // and a restore that cannot bring it back drops this entry
                            // so the list underneath is what shows.
                            val groupIds = remember(detail) {
                                detail.group.items.mapTo(hashSetOf()) { it.id }
                            }
                            ManualGridScreen(
                                media = remember(media, groupIds) { media.filter { it.id in groupIds } },
                                defaultSortBySize = true,
                                onBack = ::popDetail,
                                onMark = ::markMedia,
                                animationEnabled = animationEnabled,
                                mode = MediaGridMode.DUPLICATE_GROUP,
                                onCompressSelected = ::compressSelection,
                            )
                        }
                        DetailScreen.Screenshots -> {
                            val screenshotIds = remember(toolAnalysis) {
                                toolAnalysis.screenshots.mapTo(hashSetOf()) { it.id }
                            }
                            ManualGridScreen(
                                media = remember(media, screenshotIds) { media.filter { it.id in screenshotIds } },
                                defaultSortBySize = true,
                                onBack = ::popDetail,
                                onMark = ::markMedia,
                                animationEnabled = animationEnabled,
                                mode = MediaGridMode.SCREENSHOTS,
                                onCompressSelected = ::compressSelection,
                            )
                        }
                        DetailScreen.Largest -> {
                            val largestIds = remember(toolAnalysis) {
                                toolAnalysis.largest.mapTo(hashSetOf()) { it.id }
                            }
                            ManualGridScreen(
                                media = remember(media, largestIds) { media.filter { it.id in largestIds } },
                                defaultSortBySize = true,
                                onBack = ::popDetail,
                                onMark = ::markMedia,
                                animationEnabled = animationEnabled,
                                mode = MediaGridMode.LARGEST,
                                onCompressSelected = ::compressSelection,
                            )
                        }
                        is DetailScreen.MediaProcessing -> MediaToolsScreen(
                            batchViewModel = batchViewModel,
                            imageQuality = imageQuality,
                            videoQuality = videoQuality,
                            stripMetadata = stripMetadata,
                            onBack = ::popDetail,
                            onMediaCreated = { scanRequest++ },
                            onOpenResult = { uri -> openMediaViewer(context, uri) },
                            preselected = detail.preselected,
                            // Swapped rather than popped: dropping the hand-off leaves
                            // the tools open, which is what the button offers.
                            onClearPreselected = {
                                replaceDetail(DetailScreen.MediaProcessing(emptyList()))
                            },
                        )
                        is DetailScreen.LogicalAlbumGrid -> {
                            val album = selectedLogicalAlbum
                            if (album == null) {
                                // The album was deleted, or a restore brought back a name
                                // that no longer resolves.
                                LaunchedEffect(detail) { popDetail() }
                            } else {
                                val ids = album.mediaIds
                                ManualGridScreen(
                                    media = remember(media, ids) { media.filter { it.id in ids } },
                                    defaultSortBySize = false,
                                    onBack = ::popDetail,
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
                                        popDetail()
                                    },
                                )
                            }
                        }
                        DetailScreen.About -> AboutScreen(
                            onBack = ::popDetail,
                        )
                        null -> Unit
                    }
                }
            }

            if (detail == null) {
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
 * Loads every item's review decision, folding in any decision an older version
 * left in `SharedPreferences`, and compacts the log once replaying it costs more
 * than the decisions it yields.
 *
 * Runs off the main thread; the caller assigns the result in one state write.
 */
private fun hydrateReviewStates(
    store: ReviewDecisionStore,
    prefs: SharedPreferences,
    items: List<IndexedMedia>,
    pruneStaleKeys: Boolean,
): Map<Long, ReviewState> {
    val byKey = HashMap(store.load())
    val legacy = readLegacyReviewPreferences(prefs, items)
    val unlogged = legacy.filterKeys { it !in byKey }
    byKey.putAll(unlogged)

    val states = HashMap<Long, ReviewState>(items.size)
    val activeKeys = HashSet<String>(items.size)
    items.forEach { item ->
        val key = item.reviewKey()
        activeKeys += key
        states[item.id] = byKey[key] ?: ReviewState.UNREVIEWED
    }

    // Draining the old preference keys deletes them, so it only happens once the
    // snapshot covers the whole library. A scoped or partially permitted scan
    // cannot tell a decision for a file it may not see from a stale one, and
    // deleting on that basis would throw away the user's work.
    if (!pruneStaleKeys) {
        if (unlogged.isNotEmpty()) store.append(unlogged)
        return states
    }
    store.append(unlogged)
    // Every review_* key, not just the ones matched above: with the whole library
    // in view, a key nothing matched is stale by definition.
    val legacyKeys = prefs.all.keys.filter { it.startsWith("review_") }
    if (legacyKeys.isNotEmpty()) prefs.edit { legacyKeys.forEach(::remove) }
    val live = byKey.filterKeys { it in activeKeys }
    if (live.size != byKey.size || shouldCompactLog(store.lastLineCount, live.size)) {
        store.compact(live)
    }
    return states
}

/**
 * Review decisions still held as `review_*` preference keys by a version that kept
 * them there, including the even older `review_<id>` form.
 *
 * `prefs.all` is only touched when at least one such key exists, so a migrated
 * install does not walk the preference map on every scan.
 */
private fun readLegacyReviewPreferences(
    prefs: SharedPreferences,
    items: List<IndexedMedia>,
): Map<String, ReviewState> {
    if (prefs.all.keys.none { it.startsWith("review_") }) return emptyMap()
    val statesByName = ReviewState.entries.associateBy { it.name }
    val found = LinkedHashMap<String, ReviewState>()
    items.forEach { item ->
        val key = item.reviewKey()
        val saved = prefs.getString(key, null) ?: prefs.getString("review_${item.id}", null)
        val state = saved?.let(statesByName::get) ?: return@forEach
        if (state != ReviewState.UNREVIEWED) found[key] = state
    }
    return found
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
