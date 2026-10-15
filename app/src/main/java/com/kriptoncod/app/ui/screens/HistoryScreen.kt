package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.EmptyState

@Composable
fun HistoryScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
        EmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            title = stringResource(R.string.history_empty_title),
            message = stringResource(R.string.history_empty_message),
        )
    }
}
