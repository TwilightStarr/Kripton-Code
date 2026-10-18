package com.kriptoncod.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.MainActivity
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.labelRes
import com.kriptoncod.app.work.RunControl

/**
 * Ana ekran widget'ı: çalışan (ya da duraklatılmış) run'ın durumu, ilerlemesi ve şu an üretilen düğüm.
 * Duraklat / Devam et / İptal düğmeleri uygulamayı açmadan çalışır; dokununca uygulama açılır.
 *
 * Uygulama temasıyla aynı: her zaman koyu. Veri her güncellemede (iş ilerlemesi, durum değişimi) taze okunur
 * ([RunWidgetUpdater]); ayrıca sistem yarım saatte bir yeniler.
 */
class RunWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as KriptonCodApp).container
        val run = runCatching { RunWidgetData.load(container) }.getOrNull()
        provideContent { WidgetContent(context, run) }
    }

    private companion object {
        val BG = ColorProvider(Color(0xFF111418))
        val FG = ColorProvider(Color(0xFFE6E8EB))
        val MUTED = ColorProvider(Color(0xFF9AA3AD))
        val ACCENT = ColorProvider(Color(0xFF4C8DFF))
        val TRACK = ColorProvider(Color(0xFF2A3038))
        val DANGER = ColorProvider(Color(0xFFB3261E))
        val ON_COLOR = ColorProvider(Color(0xFFFFFFFF))
    }

    @androidx.compose.runtime.Composable
    private fun WidgetContent(context: Context, run: WidgetRun?) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            if (run == null) {
                Text(context.getString(R.string.app_name), style = TextStyle(color = MUTED, fontSize = 12.sp))
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    context.getString(R.string.widget_empty),
                    style = TextStyle(color = FG, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                return@Column
            }

            Text(
                context.getString(R.string.widget_run_title, run.id, context.getString(run.status.labelRes())),
                style = TextStyle(color = FG, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            val node = run.nodeTitle?.takeIf { it.isNotBlank() }
                ?: run.nodeKind?.let { context.getString(it.labelRes()) }
            Text(
                if (node != null) context.getString(R.string.widget_node, node)
                else context.getString(R.string.widget_progress, run.done, run.total),
                style = TextStyle(color = MUTED, fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = run.progress,
                modifier = GlanceModifier.fillMaxWidth(),
                color = ACCENT,
                backgroundColor = TRACK,
            )
            if (node != null) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    context.getString(R.string.widget_progress, run.done, run.total),
                    style = TextStyle(color = MUTED, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                val params = actionParametersOf(RunIdKey to run.id)
                if (run.canPause) {
                    androidx.glance.Button(
                        text = context.getString(R.string.notif_action_pause),
                        onClick = actionRunCallback<PauseRunAction>(params),
                        colors = ButtonDefaults.buttonColors(backgroundColor = ACCENT, contentColor = ON_COLOR),
                    )
                } else if (run.canResume) {
                    androidx.glance.Button(
                        text = context.getString(R.string.notif_action_resume),
                        onClick = actionRunCallback<ResumeRunAction>(params),
                        colors = ButtonDefaults.buttonColors(backgroundColor = ACCENT, contentColor = ON_COLOR),
                    )
                }
                if (run.canCancel) {
                    Spacer(GlanceModifier.width(8.dp))
                    androidx.glance.Button(
                        text = context.getString(R.string.notif_action_cancel),
                        onClick = actionRunCallback<CancelRunAction>(params),
                        colors = ButtonDefaults.buttonColors(backgroundColor = DANGER, contentColor = ON_COLOR),
                    )
                }
            }
        }
    }
}

class RunWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RunWidget()
}

internal val RunIdKey = ActionParameters.Key<Long>("run_id")

/** Widget düğmeleri; bildirim düğmeleriyle aynı [RunControl] kurallarını kullanır. */
private suspend fun runAction(context: Context, parameters: ActionParameters, action: RunControl.Action) {
    val runId = parameters[RunIdKey] ?: return
    val container = (context.applicationContext as KriptonCodApp).container
    RunControl.apply(container, action, runId)
    RunWidgetUpdater.refresh(context)
}

class PauseRunAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runAction(context, parameters, RunControl.Action.PAUSE)
}

class ResumeRunAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runAction(context, parameters, RunControl.Action.RESUME)
}

class CancelRunAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        runAction(context, parameters, RunControl.Action.CANCEL)
}
