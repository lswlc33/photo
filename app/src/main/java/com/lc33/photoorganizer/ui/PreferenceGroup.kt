package com.lc33.photoorganizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lc33.photoorganizer.ui.components.SectionTitle
import com.lc33.photoorganizer.ui.components.standardCardColors
import top.yukonga.miuix.kmp.basic.Card

/** A labelled MIUIX card group holding related preferences or actions. */
@Composable
fun PreferenceGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionTitle(title)
        Card(modifier = Modifier.fillMaxWidth(), colors = standardCardColors()) {
            Column(content = content)
        }
    }
}
