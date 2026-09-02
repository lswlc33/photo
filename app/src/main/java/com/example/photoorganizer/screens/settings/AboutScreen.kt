package com.example.photoorganizer.screens.settings

import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.photoorganizer.ui.components.SectionTitle
import com.example.photoorganizer.ui.components.standardCardColors
import com.example.photoorganizer.ui.theme.AccentBlue
import com.example.photoorganizer.ui.theme.AccentGreen
import com.example.photoorganizer.ui.theme.AccentOrange
import com.example.photoorganizer.ui.theme.AccentViolet
import com.example.photoorganizer.ui.theme.DangerRed
import top.yukonga.miuix.kmp.basic.Card
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

        SectionTitle(stringResource(R.string.about_features_section))
        // One card per capability rather than one wall of prose: the page is the
        // only place that explains what the app actually does, and a reader
        // skimming for "does it touch my originals" should not have to parse a
        // paragraph to find out.
        FeatureCards.forEach { feature ->
            FeatureCard(
                icon = feature.icon,
                accent = feature.accent(),
                title = stringResource(feature.titleRes),
                summary = stringResource(feature.summaryRes),
            )
        }

        SectionTitle(stringResource(R.string.about_open_source_section))
        Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                OpenSourceProjects.forEach { project ->
                    OpenSourcePreference(
                        title = project.name,
                        summary = project.license,
                        url = project.url,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.about_license_note),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** One capability card on the about page. */
private class Feature(
    val icon: ImageVector,
    val accent: @Composable () -> Color,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
)

private val FeatureCards = listOf(
    Feature(
        icon = Icons.Default.PhotoLibrary,
        accent = { AccentBlue },
        titleRes = R.string.about_feature_index_title,
        summaryRes = R.string.about_feature_index_summary,
    ),
    Feature(
        icon = Icons.Default.Tune,
        accent = { AccentViolet },
        titleRes = R.string.about_feature_modes_title,
        summaryRes = R.string.about_feature_modes_summary,
    ),
    Feature(
        icon = Icons.Default.ContentCopy,
        accent = { AccentGreen },
        titleRes = R.string.about_feature_duplicates_title,
        summaryRes = R.string.about_feature_duplicates_summary,
    ),
    Feature(
        icon = Icons.Default.Storage,
        accent = { AccentOrange },
        titleRes = R.string.about_feature_cleanup_title,
        summaryRes = R.string.about_feature_cleanup_summary,
    ),
    Feature(
        icon = Icons.Default.Compress,
        accent = { AccentBlue },
        titleRes = R.string.about_feature_processing_title,
        summaryRes = R.string.about_feature_processing_summary,
    ),
    Feature(
        icon = Icons.Default.Lock,
        accent = { DangerRed },
        titleRes = R.string.about_feature_privacy_title,
        summaryRes = R.string.about_feature_privacy_summary,
    ),
)

@Composable
private fun FeatureCard(icon: ImageVector, accent: Color, title: String, summary: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = .16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    summary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * Every third-party project in the release runtime classpath, with the license its
 * own POM declares. Grouped by the artifact a reader would recognise rather than
 * listed per module - `androidx.compose.ui:ui-geometry` on its own line would be
 * noise, not disclosure.
 */
private class OpenSourceProject(val name: String, val license: String, val url: String)

private val OpenSourceProjects = listOf(
    OpenSourceProject(
        "MIUIX",
        "Apache-2.0",
        "https://github.com/compose-miuix-ui/miuix",
    ),
    OpenSourceProject(
        "Kyant Backdrop",
        "Apache-2.0",
        "https://github.com/Kyant0/AndroidLiquidGlass",
    ),
    OpenSourceProject(
        "MaterialKolor",
        "MIT",
        "https://github.com/jordond/materialkolor",
    ),
    OpenSourceProject(
        "AndroidX / Jetpack Compose",
        "Apache-2.0",
        "https://github.com/androidx/androidx",
    ),
    OpenSourceProject(
        "AndroidX Media3",
        "Apache-2.0",
        "https://github.com/androidx/media",
    ),
    OpenSourceProject(
        "Kotlin / kotlinx.coroutines",
        "Apache-2.0",
        "https://github.com/JetBrains/kotlin",
    ),
    OpenSourceProject(
        "Compose Multiplatform",
        "Apache-2.0",
        "https://github.com/JetBrains/compose-multiplatform",
    ),
    OpenSourceProject(
        "Poko",
        "Apache-2.0",
        "https://github.com/drewhamilton/Poko",
    ),
    OpenSourceProject(
        "Guava",
        "Apache-2.0",
        "https://github.com/google/guava",
    ),
    OpenSourceProject(
        "Checker Framework Qualifiers",
        "MIT",
        "https://github.com/typetools/checker-framework",
    ),
    OpenSourceProject(
        "JSpecify",
        "Apache-2.0",
        "https://github.com/jspecify/jspecify",
    ),
)

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
