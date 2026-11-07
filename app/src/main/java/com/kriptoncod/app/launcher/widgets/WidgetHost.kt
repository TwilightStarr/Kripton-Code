package com.kriptoncod.app.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.ceil

/** Bir widget satırının yüksekliği (dp). Ana ekran ızgarası ve widget boyutları bununla hesaplanır. */
const val WIDGET_ROW_DP = 84

/**
 * Android widget'larını (başka uygulamaların widget'ları) ana ekranda barındırır. [AppWidgetHost] widget görünümlerini
 * canlı tutar; widget'ı eklemek için kullanıcı Android'in kendi onay ekranını görür (bağlama izni), yapılandırma
 * gerekiyorsa widget'ın kendi ekranı açılır. Hiçbir widget verisi Kripton'a okunmaz; widget kendi uygulamasının sürecinde çalışır.
 */
class WidgetHostController(context: Context) {
    private val app = context.applicationContext
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    private val host = AppWidgetHost(app, HOST_ID)

    @Volatile private var listening = false

    fun start() {
        if (listening) return
        listening = runCatching { host.startListening() }.isSuccess
    }

    fun stop() {
        if (!listening) return
        listening = false
        runCatching { host.stopListening() }
    }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun delete(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }

    /** Kurulu widget sağlayıcıları, uygulama adına ve widget adına göre sıralı. */
    fun providers(): List<AppWidgetProviderInfo> {
        val pm = app.packageManager
        return runCatching { manager.installedProviders }.getOrDefault(emptyList())
            .sortedWith(compareBy({ runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.provider.packageName, 0)).toString().lowercase() }.getOrDefault("") }, { it.loadLabel(pm)?.lowercase() ?: "" }))
    }

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    /** İzin zaten varsa bağlar; yoksa false (kullanıcıya [bindRequest] gösterilir). */
    fun tryBind(id: Int, provider: ComponentName): Boolean = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider) }.getOrDefault(false)

    fun bindRequest(id: Int, provider: ComponentName): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()
    }

    companion object {
        const val HOST_ID = 0x4B52

        /** Widget'ın en az kaç satır yüksekliğinde olması gerektiği (1..6). */
        fun rowsFor(info: AppWidgetProviderInfo): Int {
            val density = Resources.getSystem().displayMetrics.density.takeIf { it > 0f } ?: 1f
            val dp = info.minHeight / density
            return ceil(dp / WIDGET_ROW_DP).toInt().coerceIn(1, 6)
        }
    }
}

/** Widget ekleme akışını başlatır (bağlama izni -> yapılandırma -> ekleme). */
@Stable
class WidgetAdder internal constructor(val start: (AppWidgetProviderInfo) -> Unit)

/**
 * @param onAdded widget hazır olunca (kimlik, satır sayısı)
 * @param onFailed kullanıcı vazgeçti ya da widget eklenemedi
 */
@Composable
fun rememberWidgetAdder(
    controller: WidgetHostController,
    onAdded: (widgetId: Int, rows: Int) -> Unit,
    onFailed: () -> Unit,
): WidgetAdder {
    val onAddedNow by rememberUpdatedState(onAdded)
    val onFailedNow by rememberUpdatedState(onFailed)
    var pendingConfig by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }
    var pendingBind by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }

    val configLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = pendingConfig
        pendingConfig = null
        if (p != null) {
            if (r.resultCode == Activity.RESULT_OK) {
                onAddedNow(p.first, WidgetHostController.rowsFor(p.second))
            } else {
                controller.delete(p.first)
                onFailedNow()
            }
        }
    }

    fun afterBind(id: Int, info: AppWidgetProviderInfo) {
        val configure = info.configure
        if (configure == null) {
            onAddedNow(id, WidgetHostController.rowsFor(info))
            return
        }
        pendingConfig = id to info
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(configure)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        val launched = runCatching { configLauncher.launch(intent) }.isSuccess
        if (!launched) {
            // Yapılandırma ekranı açılamadı: widget varsayılan ayarlarıyla eklenir.
            pendingConfig = null
            onAddedNow(id, WidgetHostController.rowsFor(info))
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = pendingBind
        pendingBind = null
        if (p != null) {
            if (r.resultCode == Activity.RESULT_OK) {
                afterBind(p.first, p.second)
            } else {
                controller.delete(p.first)
                onFailedNow()
            }
        }
    }

    return remember(controller) {
        WidgetAdder { info ->
            val id = controller.allocate()
            if (controller.tryBind(id, info.provider)) {
                afterBind(id, info)
            } else {
                pendingBind = id to info
                val launched = runCatching { bindLauncher.launch(controller.bindRequest(id, info.provider)) }.isSuccess
                if (!launched) {
                    pendingBind = null
                    controller.delete(id)
                    onFailedNow()
                }
            }
        }
    }
}

/** Ana ekranda barındırılan bir Android widget'ı. Yüksekliği [rows] × [WIDGET_ROW_DP]. */
@Composable
fun HostedWidget(controller: WidgetHostController, widgetId: Int, rows: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val h = (rows.coerceIn(1, 8) * WIDGET_ROW_DP).dp
    BoxWithConstraints(modifier.fillMaxWidth().height(h)) {
        val wDp = maxWidth.value.toInt()
        val hDp = h.value.toInt()
        AndroidView(
            factory = { _ -> controller.createView(context, widgetId) ?: View(context) },
            modifier = Modifier.fillMaxWidth().height(h),
            update = { v ->
                (v as? AppWidgetHostView)?.let { applyWidgetSize(it, wDp, hDp) }
            },
        )
    }
}

/**
 * Widget'a ayrılan alanı (dp) bildirir. Kullanımdan kalkmış `updateAppWidgetSize` yerine seçenekler doğrudan yazılır:
 * min/maks genişlik-yükseklik her sürümde, Android 12+ için ayrıca boyut listesi ([AppWidgetManager.OPTION_APPWIDGET_SIZES]).
 */
private fun applyWidgetSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
    val options = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        }
    }
    view.updateAppWidgetOptions(options)
}
