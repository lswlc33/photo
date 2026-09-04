package com.lc33.photoorganizer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lc33.photoorganizer.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton

/**
 * Top-bar help affordance: a question-mark button that opens one dialog saying
 * what the page is for and how its controls behave.
 *
 * Every page that presents a choice the user can get wrong carries one. It is a
 * single composable rather than a copy per screen so the icon, the content
 * description, the dialog shape and the bottom-bar handling cannot drift apart -
 * a new page only supplies two strings.
 *
 * Deliberately an on-demand dialog and not a full-screen walkthrough: a tour has
 * to be dismissed before the app can be used at all, is shown when the user has
 * no context to attach it to, and is never there the second time the question
 * comes up. This is, and stays exactly where the question is asked.
 *
 * [ScreenColumn] and [ScreenLazyColumn] render this from their own `helpTitle` /
 * `helpMessage` parameters, so only the two hand-built top bars - swipe review
 * and the media grid - call it directly.
 */
@Composable
fun HelpAction(title: String, message: String) {
    var show by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { show = true }) {
        Icon(
            Icons.AutoMirrored.Filled.Help,
            contentDescription = stringResource(R.string.help_action_cd),
        )
    }
    MessageDialog(
        show = show,
        title = title,
        message = message,
        onDismiss = { show = false },
    )
}
