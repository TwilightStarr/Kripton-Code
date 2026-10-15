package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.theme.KriptonTheme

@Composable
fun HomeScreen(onAddAi: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KriptonCodLogo(logoSize = 120.dp)
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = KriptonTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        EmptyState(
            icon = Icons.Filled.Face,
            title = stringResource(R.string.home_empty_title),
            message = stringResource(R.string.home_empty_message),
            action = { KButton(text = stringResource(R.string.home_action_add_ai), onClick = onAddAi) },
        )
    }
}
