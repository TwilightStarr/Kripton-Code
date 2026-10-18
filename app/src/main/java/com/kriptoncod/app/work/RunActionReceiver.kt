package com.kriptoncod.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.widget.RunWidgetUpdater
import kotlinx.coroutines.launch

/**
 * Bildirimdeki Duraklat / Devam et / İptal düğmelerini işler. Uygulama arayüzü açılmaz.
 * Dışarıya kapalıdır (`exported=false`); yalnız uygulamanın kendi PendingIntent'leri tetikler.
 */
class RunActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val runId = intent.getLongExtra(EXTRA_RUN_ID, -1L)
        val action = runCatching { RunControl.Action.valueOf(intent.getStringExtra(EXTRA_ACTION).orEmpty()) }.getOrNull()
        if (runId < 0 || action == null) return

        val app = context.applicationContext as KriptonCodApp
        val pending = goAsync()
        app.container.appScope.launch {
            try {
                val applied = RunControl.apply(app.container, action, runId)
                if (applied) {
                    when (action) {
                        RunControl.Action.PAUSE -> RunNotifier.paused(app, runId)
                        RunControl.Action.RESUME, RunControl.Action.CANCEL -> RunNotifier.clearPaused(app, runId)
                    }
                    RunWidgetUpdater.refresh(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.kriptoncod.app.RUN_ACTION"
        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_ACTION = "action"
    }
}
