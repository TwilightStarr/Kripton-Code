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
