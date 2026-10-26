package app.dayplan.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.dayplan.data.ChecklistStore
import app.dayplan.data.PlanStore
import app.dayplan.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Будильник на 00:00:30 следующего дня. Неточный и без пробуждения телефона:
 * сработает при первом пробуждении после полуночи — то есть к моменту, когда виджет увидят.
 * Точные будильники на Android 12+ требуют отдельного разрешения — здесь они не нужны.
 */
object MidnightScheduler {
    const val ACTION_DAY_CHANGED = "app.dayplan.action.DAY_CHANGED"
    private const val REQUEST = 1001

    fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val next = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .plusSeconds(30)
            .toInstant()
            .toEpochMilli()
        alarm.setAndAllowWhileIdle(AlarmManager.RTC, next, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, DayChangeReceiver::class.java).setAction(ACTION_DAY_CHANGED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Смена дня, перевод часов, смена часового пояса, перезагрузка, обновление приложения. */
class DayChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PlanStore(app).prune()
                ChecklistStore(app).prune()
                WidgetRefresher.refreshAll(app)
                MidnightScheduler.schedule(app)
            } finally {
                pending.finish()
            }
        }
    }
}
