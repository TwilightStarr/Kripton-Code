package com.kriptoncod.app.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kriptoncod.app.ui.desktop.AppInfo
import com.kriptoncod.app.ui.desktop.DesktopAppIcon
import com.kriptoncod.app.ui.theme.KriptonTheme

/**
 * Launcher'ın kendi bildirim/ileti mekanizması: Android Toast'u yerine uygulamanın kendi şeridinde gösterilir.
 * Masaüstü kabuğu sağlar; Dosyalar gibi içerideki ekranlar buradan mesaj gösterir.
 */
val LocalInAppMessage = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Tam ekran katman: karartma + içerik. Karartmaya dokunmak [onDismiss] çağırır; içeriğin kendi dokunuşları yutması gerekir ([swallowTaps]). */
@Composable
internal fun Scrim(onDismiss: () -> Unit, alpha: Float = 0.5f, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        content = content,
    )
}

/** Dokunuşların alttaki karartmaya geçmesini engeller. */
internal fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** Yuvarlak köşeli, kenarlıklı panel yüzeyi. */
@Composable
internal fun Modifier.panelSurface(radius: Dp = 20.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.5.dp, KriptonTheme.colors.nodeBorder, shape)
}

/** Simgenin köşesindeki sayı rozeti. [count] 0 ise çizilmez. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .widthIn(min = 16.dp)
            .height(16.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Rozetli uygulama simgesi. */
@Composable
internal fun BadgedIcon(info: AppInfo, size: Dp, badge: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(size + 8.dp), contentAlignment = Alignment.Center) {
        DesktopAppIcon(info, size)
        CountBadge(badge, Modifier.align(Alignment.TopEnd))
    }
}

/** Kenarlıklı küçük hap düğme. */
@Composable
internal fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val k = KriptonTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) k.nodeFill else Color.Transparent)
            .border(1.2.dp, if (selected) MaterialTheme.colorScheme.primary else k.nodeBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
