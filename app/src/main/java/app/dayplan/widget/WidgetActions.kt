package app.dayplan.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.state.updateAppWidgetState
import app.dayplan.data.ChecklistStore
import app.dayplan.system.MidnightScheduler

/** Галочка в виджете. */
class ToggleChecklistAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters.checkKey() ?: return
        val store = ChecklistStore(context)
        val checked = parameters[ToggleableStateKey]
        if (checked != null) store.set(key, checked) else store.toggle(key)
        WidgetRefresher.refreshAll(context)
    }

    companion object {
        val KEY = ActionParameters.Key<String>("check_key")
    }
}

object WidgetRefresher {
    val TICK = longPreferencesKey("tick")

    /** Перерисовать все виджеты: сдвинуть тик в их состоянии и запросить обновление. */
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        val ids = GlanceAppWidgetManager(app).getGlanceIds(PlanWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(app, id) { it[TICK] = System.nanoTime() }
            PlanWidget().update(app, id)
        }
    }
}

class PlanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PlanWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightScheduler.schedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MidnightScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightScheduler.cancel(context)
    }
}
