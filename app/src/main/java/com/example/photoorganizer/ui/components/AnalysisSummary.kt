package com.example.photoorganizer.ui.components

import androidx.compose.runtime.Composable

/**
 * The three states every analysis summary line has, in one place.
 *
 * The tools page spells this out four times and the dashboard once, and the order
 * matters: an analysis that has not finished must not be described as empty, which
 * is the mistake the shape invites. [summary] is only called once there is
 * something to summarise, so its plural lookups and byte formatting are skipped
 * while the pass is still running.
 */
@Composable
fun analysisSummary(
    ready: Boolean,
    isEmpty: Boolean,
    pendingText: String,
    emptyText: String,
    summary: @Composable () -> String,
): String = when {
    !ready -> pendingText
    isEmpty -> emptyText
    else -> summary()
}
