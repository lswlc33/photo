package com.lc33.photoorganizer.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.lc33.photoorganizer.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune

/** Top-level pages shown in the liquid glass bottom bar. */
enum class AppPage(@StringRes val labelRes: Int, val icon: ImageVector) {
    DASHBOARD(R.string.nav_dashboard, MiuixIcons.Photos),
    ORGANIZE(R.string.nav_organize, MiuixIcons.GridView),
    TOOLS(R.string.nav_tools, MiuixIcons.Tune),
    SETTINGS(R.string.nav_settings, MiuixIcons.Settings),
}
