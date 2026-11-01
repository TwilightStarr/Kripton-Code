package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.screens.Avatar
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Çubukta, başlatıcıda ve masaüstünde gösterilen bir uygulamanın görünümü.
 * [provider]: AI uygulaması ise web sağlayıcısı (simge ve renk buradan).
 */
@Immutable
class AppInfo(
    val app: String,
    val title: String,
    val tint: Color,
    val provider: WebProvider? = null,
)

/** [info] uygulamasının simgesi (AI: renkli baş harf, Dosyalar: klasör, Kripton: logo, diğerleri: gerçek uygulama simgesi). */
@Composable
internal fun DesktopAppIcon(info: AppInfo, size: Dp) {
    val provider = info.provider
    when {
        provider != null -> Avatar(provider, size)

        info.app == DesktopApps.FILES -> FolderGlyph(MaterialTheme.colorScheme.primary, size)

        info.app == DesktopApps.KRIPTON -> KriptonCodLogo(logoSize = size)

        else -> {
            val pkg = DesktopApps.packageOf(info.app)
            if (pkg != null) PackageIcon(pkg, size) else MonitorGlyph(MaterialTheme.colorScheme.onSurface, size)
        }
    }
}

/** Kurulu bir uygulamanın simgesi; arka planda yüklenir, o sürece yer tutucu çizilir. */
@Composable
internal fun PackageIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.Default) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).background(KriptonTheme.colors.nodeFill, RoundedCornerShape(size / 4)))
    }
}

/** Başlatıcıda ve masaüstünde kullanılan kutucuk ölçüsü. */
internal val TILE_ICON = 48.dp
