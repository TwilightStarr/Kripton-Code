package com.kriptoncod.app.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import kotlin.math.max

/**
 * Ana ekran duvar kağıdı. Üç kaynak: Kripton'un kendi zemini (boş), kullanıcının seçtiği görsel (uygulama klasörüne kopyalanır,
 * ağa gitmez) ya da sistem duvar kağıdı (Android bazı cihazlarda okuma izni ister; okunamazsa sessizce kendi zemine döner).
 */
object Wallpaper {
    const val SYSTEM = "system"
    private const val MAX_SIDE = 2048

    fun file(context: Context) = File(context.filesDir, "launcher-wallpaper.jpg")

    /** [uri] görselini küçültüp kopyalar; başarılıysa yeni ayar değerini ("file:<zaman>") verir. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        "file:" + System.currentTimeMillis()
    }.getOrNull()

    /** Ayar değerine göre görseli yükler (arka planda çağır); kaynak yok/okunamıyorsa null. */
    fun load(context: Context, setting: String): Bitmap? = runCatching {
        when {
            setting.isBlank() -> null
            setting == SYSTEM -> WallpaperManager.getInstance(context).drawable?.toBitmap(1080, 1920)
            else -> file(context).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
        }
    }.getOrNull()
}
