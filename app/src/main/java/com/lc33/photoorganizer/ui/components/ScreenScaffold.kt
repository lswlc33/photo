package com.lc33.photoorganizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.ui.systemClearance
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Standard page shell: MIUIX [Scaffold] with a collapsing [TopAppBar] and a
 * scrolling [LazyColumn] body. [contentBottomPadding] should reserve room for
 * the floating glass bottom bar. [snackbarHost] is forwarded to the Scaffold so
 * pages can surface transient confirmations without building their own shell.
 *
 * Passing both [isRefreshing] and [onRefresh] wraps the body in a MIUIX
 * [PullToRefresh]; the indicator is offset below the top bar and the bar
 * collapse animation is driven by the same scroll behavior.
 *
 * The whole body is one lazy item, so everything in it composes eagerly. That is
 * the right trade for a page of fixed sections; a page whose body grows with the
 * library - a row per duplicate group, say - wants [ScreenLazyColumn] instead.
 */
@Composable
fun ScreenColumn(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    contentBottomPadding: Dp = 24.dp,
    snackbarHost: (@Composable () -> Unit)? = null,
    isRefreshing: Boolean? = null,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenLazyColumn(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        contentBottomPadding = contentBottomPadding,
        snackbarHost = snackbarHost,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
    }
}

/**
 * [ScreenColumn] with the body's lazy scope exposed, for pages whose content
 * length depends on the library. Emitting real items means rows are composed as
 * they scroll into view instead of all at once - which for a list of duplicate
 * groups also means four thumbnail decodes per row are not all queued up front.
 */
@Composable
fun ScreenLazyColumn(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    contentBottomPadding: Dp = 24.dp,
    snackbarHost: (@Composable () -> Unit)? = null,
    isRefreshing: Boolean? = null,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(topAppBarState, canScroll = { true })
    val clearance = systemClearance()
    val pullToRefreshState = rememberPullToRefreshState()
    val refreshTexts = listOf(
        stringResource(R.string.pull_refresh_pull),
        stringResource(R.string.pull_refresh_release),
        stringResource(R.string.pull_refresh_refreshing),
        stringResource(R.string.pull_refresh_done),
    )
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.background,
        snackbarHost = snackbarHost ?: {},
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.background)
                    .statusBarsPadding(),
                title = title,
                color = MiuixTheme.colorScheme.background,
                titleColor = MiuixTheme.colorScheme.onSurface,
                navigationIcon = navigationIcon ?: {},
                actions = actions ?: {},
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
            )
        },
    ) { innerPadding ->
        val body: @Composable () -> Unit = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp + clearance.start,
                    end = 16.dp + clearance.end,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = contentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
        if (isRefreshing != null && onRefresh != null) {
            PullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                pullToRefreshState = pullToRefreshState,
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                topAppBarScrollBehavior = scrollBehavior,
                color = MiuixTheme.colorScheme.primary,
                refreshTexts = refreshTexts,
                content = body,
            )
        } else {
            body()
        }
    }
}

/** Small text action used in top app bars. */
@Composable
fun BarTextAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(text = text, onClick = onClick, enabled = enabled)
}

/** Pull-to-refresh wiring produced by [rememberRefreshBridge]. */
class RefreshBridge(val isRefreshing: Boolean, val onRefresh: () -> Unit)

/**
 * Bridges the MIUIX PullToRefresh contract (isRefreshing must flip to true
 * synchronously) onto a background job whose progress is only observable as
 * [busy]. The flag is held until [busy] has been seen true and returned to
 * false, with a bounded fallback so a refresh that finishes before the next
 * recomposition cannot latch the indicator.
 */
@Composable
fun rememberRefreshBridge(busy: Boolean, onRefresh: () -> Unit): RefreshBridge {
    var requested by remember { mutableStateOf(false) }
    var sawBusy by remember { mutableStateOf(false) }
    LaunchedEffect(requested, busy) {
        if (!requested) return@LaunchedEffect
        when {
            busy -> sawBusy = true
            sawBusy -> {
                requested = false
                sawBusy = false
            }
            else -> {
                delay(RefreshFallbackMillis)
                requested = false
            }
        }
    }
    return RefreshBridge(requested) {
        requested = true
        onRefresh()
    }
}

private const val RefreshFallbackMillis = 2_000L
