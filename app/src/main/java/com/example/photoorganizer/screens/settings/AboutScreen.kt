package com.example.photoorganizer.screens.settings

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.photoorganizer.R
import com.example.photoorganizer.ui.PreferenceGroup
import com.example.photoorganizer.ui.components.ScreenColumn
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        ).versionName ?: "?"
    }

    ScreenColumn(
        title = stringResource(R.string.about_title),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_cd),
                )
            }
        },
        contentBottomPadding = 32.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_version_summary, versionName),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }

        PreferenceGroup(stringResource(R.string.about_application_section)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_application_description),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                )
                Text(
                    text = stringResource(R.string.about_privacy_description),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
        }

        PreferenceGroup(stringResource(R.string.about_open_source_section)) {
            OpenSourcePreference(
                title = "MIUIX",
                summary = "Apache-2.0",
                url = "https://github.com/compose-miuix-ui/miuix",
            )
            OpenSourcePreference(
                title = "Kyant Backdrop",
                summary = "Apache-2.0",
                url = "https://github.com/Kyant0/AndroidLiquidGlass",
            )
            OpenSourcePreference(
                title = "AndroidX / Jetpack Compose / Media3",
                summary = "Apache-2.0",
                url = "https://source.android.com/docs/setup/about/licenses",
            )
            OpenSourcePreference(
                title = "FFmpeg",
                summary = "LGPL-2.1-or-later",
                url = "https://ffmpeg.org/legal.html",
            )
        }

        Text(
            text = stringResource(R.string.about_license_note),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun OpenSourcePreference(title: String, summary: String, url: String) {
    val context = LocalContext.current
    ArrowPreference(
        title = title,
        summary = summary,
        onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}
