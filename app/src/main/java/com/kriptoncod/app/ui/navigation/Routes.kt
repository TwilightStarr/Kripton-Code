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
enum class Route(val path: String, @StringRes val label: Int, val icon: ImageVector) {
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

    fun run(runId: Long): String = "run/$runId"
}
