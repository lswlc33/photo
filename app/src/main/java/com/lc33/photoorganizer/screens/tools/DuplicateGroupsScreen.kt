package com.lc33.photoorganizer.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lc33.photoorganizer.R
import com.lc33.photoorganizer.media.DuplicateGroup
import com.lc33.photoorganizer.media.DuplicateKeepStrategy
import com.lc33.photoorganizer.media.ReviewState
import com.lc33.photoorganizer.media.ToolAnalyzer
import com.lc33.photoorganizer.media.formatBytes
import com.lc33.photoorganizer.ui.components.CompactTextButton
import com.lc33.photoorganizer.ui.components.EmptyState
import com.lc33.photoorganizer.ui.components.MediaThumbnail
import com.lc33.photoorganizer.ui.components.OverlaySpinnerChoicePopup
import com.lc33.photoorganizer.ui.components.ScreenLazyColumn
import com.lc33.photoorganizer.ui.components.SectionTitle
import com.lc33.photoorganizer.ui.components.standardCardColors
import kotlinx.coroutines.flow.filter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Groups of interchangeable copies, either byte-identical or visually alike.
 * Each group offers a one-tap "keep one copy" action that only writes review
 * marks; deletion still goes through the discarded page and the system
 * confirmation. The labels are parameters so the same screen serves both the
 * exact-duplicate and the similar-photo lists.
 */
@Composable
fun DuplicateGroupsScreen(
    groups: List<DuplicateGroup>,
    analysisReady: Boolean,
    onBack: () -> Unit,
    onMark: (Map<Long, ReviewState>) -> Unit,
    onOpenGroup: (DuplicateGroup) -> Unit,
    title: String = stringResource(R.string.tools_duplicate_title),
    hint: String = stringResource(R.string.tools_duplicate_hint),
    emptyTitle: String = stringResource(R.string.duplicate_empty),
    countLabel: @Composable (Int, String) -> String = { count, saved ->
        pluralStringResource(R.plurals.tools_summary_duplicate, count, count, saved)
    },
) {
    var strategy by rememberSaveable { mutableStateOf(DuplicateKeepStrategy.LARGEST) }
    var showStrategyPopup by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    // One snackbar at a time. MIUIX keeps a queue behind a mutex, so launching a
    // message per row meant tapping ten groups lined up ten messages the user had
    // to sit through. Cleanups fold into a running count that a single collector
    // drains, which also reports a burst of taps as one total instead of ten
    // fragments.
    var pendingDiscarded by remember { mutableIntStateOf(0) }
    LaunchedEffect(snackbarHostState) {
        snapshotFlow { pendingDiscarded }
            .filter { it > 0 }
            .collect { discarded ->
                pendingDiscarded = 0
                snackbarHostState.showSnackbar(
                    resources.getQuantityString(
                        R.plurals.tools_duplicate_cleanup_done,
                        discarded,
                        discarded,
                    ),
                )
            }
    }
    val announceCleanup: (Int) -> Unit = { discarded -> pendingDiscarded += discarded }
    val applyPlan: (List<DuplicateGroup>, DuplicateKeepStrategy) -> Int = { target, keep ->
        val plan = ToolAnalyzer.planDuplicateCleanup(target, keep)
        onMark(plan)
        plan.count { it.value == ReviewState.TRASH_MARKED }
    }
    ScreenLazyColumn(
        title = title,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_cd),
                )
            }
        },
        actions = {
            if (analysisReady && groups.isNotEmpty()) {
                OverlaySpinnerChoicePopup(
                    show = showStrategyPopup,
                    options = DuplicateKeepStrategy.entries,
                    selected = strategy,
                    title = { option -> stringResource(option.titleRes()) },
                    summary = { option -> stringResource(option.summaryRes()) },
                    onSelect = { option ->
                        strategy = option
                    },
                    onDismissRequest = { showStrategyPopup = false },
                ) {
                    IconButton(onClick = { showStrategyPopup = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.tools_duplicate_cleanup_menu_cd),
                        )
                    }
                }
            }
        },
        contentBottomPadding = 32.dp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        if (!analysisReady) {
            item {
                EmptyState(
                    title = stringResource(R.string.tools_analysis_running),
                    summary = hint,
                )
            }
            return@ScreenLazyColumn
        }
        if (groups.isEmpty()) {
            item { EmptyState(title = emptyTitle, summary = hint) }
            return@ScreenLazyColumn
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Remembered on the strategy, not recomputed per recomposition: the
                // keepers are cached per group but the sum across every group is not.
                val totalReclaimable = remember(groups, strategy) {
                    groups.sumOf { it.reclaimableBytes(strategy) }
                }
                SectionTitle(
                    title = countLabel(groups.size, formatBytes(totalReclaimable)),
                    subtitle = stringResource(strategy.titleRes()),
                )
                Text(
                    text = hint,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                CompactTextButton(
                    text = stringResource(R.string.tools_duplicate_apply_all),
                    onClick = { announceCleanup(applyPlan(groups, strategy)) },
                )
            }
        }
        // A row per group, each decoding four thumbnails. Eager composition meant a
        // library with hundreds of duplicate groups decoded all of them before the
        // page could be drawn.
        items(items = groups, key = { it.hash }) { group ->
            Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
                DuplicateGroupRow(
                    group = group,
                    strategy = strategy,
                    onOpen = { onOpenGroup(group) },
                    onKeepOne = { announceCleanup(applyPlan(listOf(group), strategy)) },
                )
            }
        }
    }
}

@Composable
private fun DuplicateGroupRow(
    group: DuplicateGroup,
    strategy: DuplicateKeepStrategy,
    onOpen: () -> Unit,
    onKeepOne: () -> Unit,
) {
    val groupName = group.items.firstOrNull()?.displayName.orEmpty()
    val detail = pluralStringResource(
        R.plurals.tools_duplicate_group_detail,
        group.items.size,
        group.items.size,
        formatBytes(group.reclaimableBytes(strategy)),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The row's own actions repeat once per group, so without a name on the
        // row a screen reader read back N identical pairs of buttons with nothing
        // to tell them apart. The thumbnails stay decorative and are cleared so
        // they do not add four more stops per row.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            group.items.take(4).forEach { item ->
                MediaThumbnail(
                    uri = item.uri,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    requestSize = 256,
                )
            }
        }
        Text(
            text = groupName,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = detail,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val keepOneDescription =
                stringResource(R.string.tools_duplicate_keep_one_cd, groupName)
            val openDescription = stringResource(R.string.tools_open_group_cd, groupName)
            CompactTextButton(
                text = stringResource(R.string.tools_duplicate_keep_one),
                onClick = onKeepOne,
                modifier = Modifier.semantics { contentDescription = keepOneDescription },
            )
            CompactTextButton(
                text = stringResource(R.string.tools_open_group),
                onClick = onOpen,
                modifier = Modifier.semantics { contentDescription = openDescription },
            )
        }
    }
}

private fun DuplicateKeepStrategy.titleRes(): Int = when (this) {
    DuplicateKeepStrategy.LARGEST -> R.string.tools_duplicate_strategy_largest
    DuplicateKeepStrategy.NEWEST -> R.string.tools_duplicate_strategy_newest
    DuplicateKeepStrategy.OLDEST -> R.string.tools_duplicate_strategy_oldest
}

private fun DuplicateKeepStrategy.summaryRes(): Int = when (this) {
    DuplicateKeepStrategy.LARGEST -> R.string.tools_duplicate_strategy_largest_summary
    DuplicateKeepStrategy.NEWEST -> R.string.tools_duplicate_strategy_newest_summary
    DuplicateKeepStrategy.OLDEST -> R.string.tools_duplicate_strategy_oldest_summary
}
