package app.dayplan.data

import app.dayplan.model.Day
import app.dayplan.model.Plan
import java.time.LocalDate

/** План, сохранённый в приложении. */
data class StoredPlan(
    val plan: Plan,
    val fileName: String,
    val importedAt: Long,
)

data class FoundDay(val stored: StoredPlan, val day: Day)

object DayLookup {
    /**
     * День на дату. Если дата есть в нескольких загруженных планах
     * (перевыпуск той же недели), берётся более свежий: сначала по полю generated, затем по времени загрузки.
     */
    fun find(plans: List<StoredPlan>, date: LocalDate): FoundDay? {
        val key = date.toString()
        return plans
            .sortedWith(
                compareByDescending<StoredPlan> { it.plan.week.generated ?: "" }
                    .thenByDescending { it.importedAt },
            )
            .firstNotNullOfOrNull { sp ->
                sp.plan.days.firstOrNull { it.date == key }?.let { FoundDay(sp, it) }
            }
    }
}
