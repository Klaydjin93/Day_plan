package app.dayplan.data

import android.content.Context
import app.dayplan.model.ReportItem
import java.time.LocalDate

/**
 * Галочки чек-листа. Ключ включает текст пункта: если план перевыпущен и пункт изменился,
 * галочка не переносится на другой смысл.
 */
class ChecklistStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("checklist", Context.MODE_PRIVATE)

    fun key(date: String, index: Int, item: ReportItem): String =
        "$date|$index|${item.item.hashCode()}"

    fun isChecked(key: String): Boolean = prefs.getBoolean(key, false)

    fun set(key: String, checked: Boolean) {
        prefs.edit().apply {
            if (checked) putBoolean(key, true) else remove(key)
        }.apply()
    }

    fun toggle(key: String) = set(key, !isChecked(key))

    /** Чистит отметки старше 30 дней. */
    fun prune(today: LocalDate = AppClock.today()) {
        val border = today.minusDays(30).toString()
        val stale = prefs.all.keys.filter { it.substringBefore('|') < border }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach(::remove) }.apply()
    }
}
