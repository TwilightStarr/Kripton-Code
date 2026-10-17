package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.kriptoncod.app.ui.adaptive.Adaptive
import com.kriptoncod.app.ui.adaptive.LocalWindowClass
import com.kriptoncod.app.ui.adaptive.WindowClass
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.theme.KriptonTheme

@Composable
fun HomeScreen(onAddAi: () -> Unit) {
    val wide = LocalWindowClass.current != WindowClass.Compact
    val pad = Adaptive.pagePadding(LocalWindowClass.current)
    val hero: @Composable () -> Unit = {
        KriptonCodLogo(logoSize = if (wide) 160.dp else 120.dp)
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = KriptonTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
    }
    val start: @Composable () -> Unit = {
        EmptyState(
            icon = Icons.Filled.Face,
            title = stringResource(R.string.home_empty_title),
            message = stringResource(R.string.home_empty_message),
            action = { KButton(text = stringResource(R.string.home_action_add_ai), onClick = onAddAi) },
        )
    }
    if (wide) {
        // Tablet: solda tanıtım, sağda başlangıç kartı; boş alan bırakmadan dengeli iki bölme.
        Row(
            modifier = Modifier.fillMaxSize().padding(pad),
            horizontalArrangement = Arrangement.spacedBy(pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { hero() }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { start() }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            hero()
            start()
        }
    }
}
