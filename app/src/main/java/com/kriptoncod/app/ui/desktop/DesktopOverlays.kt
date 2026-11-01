package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Masaüstü katmanları. Sistem iletişim kutusu (Dialog) KULLANILMAZ: tam ekran (immersive) modda yeni bir pencere
// açmak sistem çubuklarını geri getirir; bu yüzden bütün katmanlar aynı bileşimin içinde çizilir.

/** Karartılmış zeminde ortalanmış onay/bilgi kartı. Zemine dokunmak [onDismiss]'tir. */
@Composable
fun DesktopConfirm(
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        NodeCard(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(24.dp)
                // Karta dokunmak zemine geçmesin.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = KriptonTheme.colors.muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KButton(text = dismissText, onClick = onDismiss, style = KButtonStyle.Outline, modifier = Modifier.weight(1f))
                KButton(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "Yan yana uygulama aç": kurulu uygulamaları arayıp seçtirir. */
@Composable
fun SideBySidePicker(onDismiss: () -> Unit, onPick: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    val k = KriptonTheme.colors
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { SideBySide.list(context) }.getOrDefault(emptyList()) }
    }
    var query by remember { mutableStateOf("") }
    val shown = apps.orEmpty().filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        NodeCard(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .padding(24.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Text(stringResource(R.string.desktop_sbs_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.desktop_sbs_hint),
                style = MaterialTheme.typography.bodySmall,
                color = k.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.desktop_sbs_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth().height(320.dp).padding(top = 12.dp)) {
                if (apps != null && shown.isEmpty()) {
                    Text(
                        stringResource(R.string.desktop_sbs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = k.muted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(92.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(shown, key = { it.packageName }) { app ->
                            Column(
                                Modifier
                                    .clickable { onPick(app) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(app.packageName)
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            KButton(
                text = stringResource(R.string.desktop_perm_later),
                onClick = onDismiss,
                style = KButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.Default) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(48.dp))
    } else {
        Box(Modifier.size(48.dp).background(KriptonTheme.colors.nodeFill, RoundedCornerShape(12.dp)))
    }
}
