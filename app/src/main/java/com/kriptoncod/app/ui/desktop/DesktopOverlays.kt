package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

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
