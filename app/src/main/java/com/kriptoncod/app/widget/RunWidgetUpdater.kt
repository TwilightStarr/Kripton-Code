package com.kriptoncod.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.kriptoncod.app.data.repo.RunRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Widget'ı tazeler. Ekranda widget yoksa hiçbir şey yapmaz; hata hiçbir zaman çağıranı düşürmez. */
object RunWidgetUpdater {

    suspend fun refresh(context: Context) {
        runCatching {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(RunWidget::class.java)
            if (ids.isNotEmpty()) RunWidget().updateAll(context)
        }
    }

    /**
     * Run durumları değişince (uygulama içinden duraklat/iptal/bitiş dahil) widget'ı tazeler.
     * İş ilerlemesi (biten düğüm sayısı) dağıtım işçisi tarafından ayrıca bildirilir.
     */
    fun syncWithRuns(context: Context, scope: CoroutineScope, runs: RunRepository) {
        val app = context.applicationContext
        scope.launch {
            runs.observeRuns()
                .map { list -> list.map { it.id to it.status } }
                .distinctUntilChanged()
                .collect { refresh(app) }
        }
    }
}
