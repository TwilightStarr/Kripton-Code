package com.kriptoncod.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.kriptoncod.app.R

/** Tüm rotalar tek yerde; sıra alt gezinme sırasıdır. */
enum class Route(val path: String, @param:StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.nav_home, Icons.Filled.Home),
    Ais("ais", R.string.nav_ais, Icons.Filled.Face),
    Prompts("prompts", R.string.nav_prompts, Icons.Filled.Edit),
    History("history", R.string.nav_history, Icons.AutoMirrored.Filled.List),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

/** Alt gezinmede görünmeyen, AI hesabı ekleme/düzenleme alt rotaları. */
object AisRoutes {
    const val ADD = "ais/add"
    const val ARG_ID = "id"
    const val EDIT = "ais/edit/{$ARG_ID}"

    /** E-posta ile (web oturumu) hesap ekleme ve yeniden giriş. */
    const val WEB_ADD = "ais/web"
    const val WEB_RELOGIN = "ais/web/{$ARG_ID}"

    /** Tek dokunuşla ekleme: sağlayıcı seçimi atlanır, doğrudan giriş sayfası açılır. */
    const val ARG_PROVIDER = "provider"
    const val WEB_QUICK = "ais/webquick/{$ARG_PROVIDER}"

    fun webQuick(providerId: String): String = "ais/webquick/$providerId"

    /** Web oturumu hesabının adını düzenleme (giriş gerektirmez). */
    const val WEB_EDIT = "ais/webedit/{$ARG_ID}"

    fun webRelogin(id: Long): String = "ais/web/$id"

    fun webEdit(id: Long): String = "ais/webedit/$id"

    fun edit(id: Long): String = "ais/edit/$id"
}

/** Prompt seti detayı ve prompt ekleme/düzenleme alt rotaları (alt gezinmede görünmez). */
object PromptRoutes {
    const val ARG_SET_ID = "setId"
    const val ARG_PROMPT_ID = "promptId"
    const val SET = "prompts/set/{$ARG_SET_ID}"
    const val ADD_PROMPT = "prompts/set/{$ARG_SET_ID}/add"
    const val EDIT_PROMPT = "prompts/set/{$ARG_SET_ID}/edit/{$ARG_PROMPT_ID}"

    fun set(setId: Long): String = "prompts/set/$setId"
    fun addPrompt(setId: Long): String = "prompts/set/$setId/add"
    fun editPrompt(setId: Long, promptId: Long): String = "prompts/set/$setId/edit/$promptId"
}

/** Alt gezinmede görünmeyen diğer ekranlar. */
object ExtraRoutes {
    const val ARG_RUN_ID = "runId"
    const val RUN = "run/{$ARG_RUN_ID}"
    const val GITHUB = "github"
    const val PERMISSIONS = "permissions"
    const val USAGE = "usage"

    /** Mod Merkezi: hazır/özel modlar, tek dokunuşla seçim ve düzenleme. */
    const val MODES = "modes"

    /** Açık web sohbetlerini (akış ve seri mod) canlı izleme ekranı. */
    const val WEB_MONITOR = "webmonitor"

    /** "AI sohbetleri": WhatsApp benzeri liste; satıra dokununca o AI'ın web arayüzü açılır. */
    const val WEB_CHATS = "webchats"

    /** Çalışma alanı (tablet): birden çok AI web arayüzü aynı anda, sayfa başına en çok 4. */
    const val WEB_WORKSPACE = "webworkspace"
    const val ARG_CHAT_ACCOUNT = "chatAccountId"
    const val WEB_CHAT = "webchat/{$ARG_CHAT_ACCOUNT}"

    fun webChat(accountId: Long): String = "webchat/$accountId"

    fun run(runId: Long): String = "run/$runId"

    /** Seri mod (tek sohbet) çalışması. */
    const val ARG_SERIAL_ID = "serialId"
    const val SERIAL = "serial/{$ARG_SERIAL_ID}"

    fun serial(id: Long): String = "serial/$id"
}
