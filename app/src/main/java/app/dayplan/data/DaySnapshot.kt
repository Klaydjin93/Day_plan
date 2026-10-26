package app.dayplan.data

import android.content.Context
import app.dayplan.model.ReportItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CheckState(val key: String, val item: ReportItem, val checked: Boolean)

/** Всё, что нужно показать за один день: найденный день плана и состояние галочек. */
data class DaySnapshot(
    val date: LocalDate,
    val found: FoundDay?,
    val checks: List<CheckState>,
) {
    val dow: String get() = found?.day?.dow ?: DOW[date.dayOfWeek.value - 1]
    val ddmm: String get() = date.format(DDMM)
    val doneCount: Int get() = checks.count { it.checked }

    companion object {
        private val DOW = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
        private val DDMM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

        fun load(context: Context, date: LocalDate, plans: List<StoredPlan> = PlanStore(context).all()): DaySnapshot {
            val found = DayLookup.find(plans, date)
            val store = ChecklistStore(context)
            val checks = found?.day?.report.orEmpty().mapIndexed { i, item ->
                val key = store.key(found!!.day.date, i, item)
                CheckState(key, item, store.isChecked(key))
            }
            return DaySnapshot(date, found, checks)
        }
    }
}

/** Короткая подпись недели: «W22 · v2 · 26.10–01.11». */
fun StoredPlan.label(): String {
    val w = plan.week
    val f = DateTimeFormatter.ofPattern("dd.MM")
    val range = "${LocalDate.parse(w.start).format(f)}–${LocalDate.parse(w.end).format(f)}"
    return listOfNotNull(w.id, w.planVersion, range).joinToString(" · ")
}
