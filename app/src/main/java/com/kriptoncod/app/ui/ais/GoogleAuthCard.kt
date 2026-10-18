package com.kriptoncod.app.ui.ais

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KriptonLoader
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

/**
 * Gemini'ye özgü "Google ile bağlan" kartı.
 * Yalnız [com.kriptoncod.app.data.providers.ProviderCatalog.GEMINI_ID] sağlayıcısında gösterilir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleAuthCard(
    accountId: Long?,
    factory: GoogleAuthViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val vm: GoogleAuthViewModel = viewModel(
        key = "google-auth-$accountId",
        factory = factory,
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val k = KriptonTheme.colors

    NodeCard(
        modifier = modifier.fillMaxWidth(),
        accent = when (state) {
            is GoogleAuthCardState.Connected -> NodeAccent.Green

            is GoogleAuthCardState.Error,
            is GoogleAuthCardState.NeedsReauth,
            -> NodeAccent.Red

            else -> NodeAccent.Blue
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    is GoogleAuthCardState.Connected ->
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = k.arrow)

                    is GoogleAuthCardState.Error,
                    is GoogleAuthCardState.NeedsReauth,
                    ->
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = k.danger)

                    is GoogleAuthCardState.Loading ->
                        KriptonLoader(size = 28.dp)

                    else -> Unit
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.google_auth_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val subtitle = when (state) {
                        is GoogleAuthCardState.Unavailable ->
                            stringResource((state as GoogleAuthCardState.Unavailable).reasonRes)

                        is GoogleAuthCardState.Connected ->
                            stringResource(R.string.google_auth_connected)

                        is GoogleAuthCardState.NeedsReauth ->
                            stringResource(R.string.google_auth_needs_reauth)

                        is GoogleAuthCardState.Error ->
                            stringResource((state as GoogleAuthCardState.Error).messageRes)

                        is GoogleAuthCardState.Disconnected ->
                            stringResource(R.string.google_auth_hint)

                        is GoogleAuthCardState.Loading ->
                            stringResource(R.string.google_auth_loading)
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
            }

            if (state !is GoogleAuthCardState.Unavailable && state !is GoogleAuthCardState.Loading) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (state) {
                        is GoogleAuthCardState.Disconnected ->
                            KButton(
                                text = stringResource(R.string.google_auth_connect),
                                onClick = { activity?.let { vm.signIn(it) } },
                            )

                        is GoogleAuthCardState.Connected ->
                            KButton(
                                text = stringResource(R.string.google_auth_disconnect),
                                onClick = { vm.signOut() },
                                style = KButtonStyle.Outline,
                            )

                        is GoogleAuthCardState.NeedsReauth ->
                            KButton(
                                text = stringResource(R.string.google_auth_reconnect),
                                onClick = { activity?.let { vm.signIn(it) } },
                            )

                        is GoogleAuthCardState.Error ->
                            KButton(
                                text = stringResource(R.string.google_auth_retry),
                                onClick = { vm.silentRefresh() },
                                style = KButtonStyle.Outline,
                            )

                        else -> Unit
                    }
                }
            }
        }
    }
}

/**
 * Diğer sağlayıcılar (Claude, ChatGPT vb.) yalnız API anahtarı destekler.
 * Bilgi kartı: "Yalnızca API anahtarı" + anahtar alma bağlantısı.
 */
@Composable
fun ApiKeyOnlyCard(
    keyUrl: String?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    NodeCard(modifier = modifier.fillMaxWidth(), accent = NodeAccent.Blue) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.auth_api_key_only_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.auth_api_key_only_message),
                style = MaterialTheme.typography.bodySmall,
                color = KriptonTheme.colors.muted,
            )
            if (keyUrl != null) {
                KButton(
                    text = stringResource(R.string.provider_get_key),
                    onClick = { runCatching { uriHandler.openUri(keyUrl) } },
                    style = KButtonStyle.Outline,
                )
            }
        }
    }
}
