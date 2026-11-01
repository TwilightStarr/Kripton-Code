package com.kriptoncod.app.ui.desktop

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File
import kotlin.math.min

/** Masaüstü modunun Android'e bağlı yardımcıları. */
object DesktopPlatform {
    /** Cihaz (açık pencere değil) tablet mi: fiziksel ekranın kısa kenarı en az 600 dp. Bölünmüş ekranda da değişmez. */
    fun isTabletDevice(): Boolean {
        val dm = Resources.getSystem().displayMetrics
        if (dm.density <= 0f) return false
        return min(dm.widthPixels, dm.heightPixels) / dm.density >= 600f
    }

    /** Dosya yöneticisinin kökü: cihazın paylaşılan depolaması (genelde /storage/emulated/0). */
    @Suppress("DEPRECATION")
    fun storageRoot(): File = Environment.getExternalStorageDirectory()
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Tüm dosyalara erişim izni (Android 11+: "tüm dosyalara erişim" ayarı; öncesi: okuma izni). */
object FilesAccess {
    fun isGranted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** Android 11+ için bu uygulamanın "tüm dosyalara erişim" ayar ekranı; bulunamazsa genel liste. */
    fun settingsIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )
}

/** Dosya erişim durumu ve izin isteme eylemi. Ayarlardan dönünce durum yenilenir. */
@Stable
class FilesAccessState(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberFilesAccess(): FilesAccessState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(FilesAccess.isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = FilesAccess.isGranted(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = FilesAccess.isGranted(context) }
    return FilesAccessState(granted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Önce bu uygulamaya özel ekran; açılamazsa genel liste.
            FilesAccess.settingsIntents(context).firstOrNull { i -> runCatching { context.startActivity(i) }.isSuccess }
        } else {
            launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

/** Masaüstü modu açıkken sistem çubuklarını gizler (kenardan kaydırınca geçici görünür); kapanınca geri getirir. */
@Composable
fun DesktopImmersiveEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // Başka bir uygulamadan dönünce ya da klavye kapanınca çubuklar geri gelmiş olabilir.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (enabled) {
            val window = view.context.findActivity()?.window
            window?.let { WindowCompat.getInsetsController(it, view).hide(WindowInsetsCompat.Type.systemBars()) }
        }
    }
}

/** Yan yana açılabilecek kurulu uygulama. */
data class InstalledApp(val label: String, val packageName: String)

object SideBySide {
    /** Başlatıcıda görünen uygulamalar (kendimiz hariç), ada göre sıralı. Yavaştır; arka planda çağır. */
    fun list(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(query, 0)
        }
        return infos
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Uygulamayı Kripton Code'un YANINDA (Android bölünmüş ekranı) başlatır. Dönen değer: başlatıldı mı.
     * Bölünmüş ekran, açan etkinlik zaten çoklu pencere modundayken uygulanır; [Activity.isInMultiWindowMode] kapalıysa
     * uygulama yine açılır, ama yan yana yerleşim için kullanıcının önce bölünmüş ekranı açması gerekir.
     */
    fun launch(activity: Activity, packageName: String): Boolean {
        val intent = activity.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        return runCatching { activity.startActivity(intent, ActivityOptions.makeBasic().toBundle()) }.isSuccess
    }
}
