package com.kriptoncod.app.ui.desktop

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** "Aç" listesindeki bir harici uygulama. */
data class AppChoice(val label: String, val packageName: String)

/**
 * Dosyayı harici uygulamada açma: FileProvider + ACTION_VIEW. Uzantı başına "her zaman bununla aç" tercihi
 * SharedPreferences'ta tutulur. Dosya yalnız seçilen uygulamaya, salt okuma izniyle verilir.
 */
object FileOpen {
    private const val PREFS = "file_open"

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun viewIntent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(context, file), FileBrowse.mimeFor(file.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Bu dosyayı açabilen kurulu uygulamalar (kendimiz hariç), ada göre. */
    fun candidates(context: Context, file: File): List<AppChoice> {
        val pm = context.packageManager
        val infos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(viewIntent(context, file), PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(viewIntent(context, file), 0)
        }
        return infos.map { AppChoice(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** [pkg] null ise sistem seçicisi ("Şununla aç") gösterilir. Başlatılabildi mi. */
    fun launch(context: Context, file: File, pkg: String?): Boolean {
        val base = viewIntent(context, file)
        val intent = if (pkg == null) {
            Intent.createChooser(base, file.name)
        } else {
            base.setPackage(pkg)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remembered(context: Context, file: File): String? =
        FileBrowse.extension(file.name).takeIf { it.isNotEmpty() }?.let { prefs(context).getString(it, null) }

    fun remember(context: Context, file: File, pkg: String) {
        val ext = FileBrowse.extension(file.name).ifEmpty { return }
        prefs(context).edit().putString(ext, pkg).apply()
    }

    fun forget(context: Context, file: File) {
        val ext = FileBrowse.extension(file.name).ifEmpty { return }
        prefs(context).edit().remove(ext).apply()
    }
}
