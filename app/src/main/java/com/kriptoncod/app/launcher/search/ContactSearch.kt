package com.kriptoncod.app.launcher.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Kişi araması. Yalnız kullanıcı Launcher ayarlarında "Kişileri ara"yı açıp izin verirse çalışır; rehber hiçbir yere gönderilmez
 * ve saklanmaz, her aramada sistemden okunur.
 */
object ContactSearch {
    fun hasPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun search(context: Context, query: String, limit: Int = 5): List<SearchResult.Contact> {
        val q = query.trim()
        if (q.length < 2 || !hasPermission(context)) return emptyList()
        return runCatching {
            val out = ArrayList<SearchResult.Contact>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$q%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val name = c.getString(0).orEmpty()
                    val number = c.getString(1).orEmpty()
                    if (name.isNotBlank() && number.isNotBlank() && out.none { it.number == number }) out.add(SearchResult.Contact(name, number))
                }
            }
            out
        }.getOrDefault(emptyList())
    }
}
