package app.dayplan.nutrition.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Одна запись дня для итога — без зависимостей от Room. */
data class ExportEntry(
    val meal: String,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val verified: Boolean,
)

data class ExportInput(
    val date: LocalDate,
    /** Тип дня из плана: «зал B», «отдых · заправка». */
    val dayType: String?,
    /** «W25 v2» — неделя и версия плана, откуда цели. */
    val planLabel: String?,
    val goals: DayGoals?,
    /** Дата, с которой пришли цели, если их переносили. */
    val shiftedFrom: LocalDate?,
    val entries: List<ExportEntry>,
    val meals: List<String>,
    val weightKg: Double?,
    val weightAvg7: WeightAverage?,
    val appVersion: String,
)

/**
 * Итог дня для чата — формат из ТЗ «Итог дня для чата»: постоянные поля, план со смыслом цели (≥, ≤, ±),
 * строка непроверенных всегда, вес — только если замер есть.
 */
object DayExport {

    private val RU = Locale.forLanguageTag("ru")
    private val DDMM = DateTimeFormatter.ofPattern("dd.MM")
    private val DOW = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

    fun text(x: ExportInput, withProducts: Boolean): String = buildString {
        val k = x.entries.sumOf { it.kcal }
        val p = x.entries.sumOf { it.protein }
        val f = x.entries.sumOf { it.fat }
        val c = x.entries.sumOf { it.carbs }

        appendLine(listOfNotNull("Питание · ${day(x.date)}", x.dayType?.takeIf { it.isNotBlank() }, x.planLabel?.let { "план $it" }).joinToString(" · "))
        appendLine("Факт: ${i(k)} ккал · Б ${i(p)} г · Ж ${i(f)} г · У ${i(c)} г")
        val g = x.goals
        if (g == null) {
            appendLine("План: целей на этот день нет")
        } else {
            appendLine("План: ${goal(g.kcal, "ккал", "")} · ${goal(g.protein, "г", "Б ")} · ${goal(g.fat, "г", "Ж ")} · ${goal(g.carbs, "г", "У ")}")
            appendLine("Разница: ${signed(k - g.kcal.value)} ккал · Б ${signed(p - g.protein.value)} · Ж ${signed(f - g.fat.value)} · У ${signed(c - g.carbs.value)}")
        }
        val byMeal = x.meals.joinToString(" · ") { m -> "${m.lowercase(RU)} ${i(x.entries.filter { it.meal == m }.sumOf { it.kcal })}" }
        appendLine("Приёмы: $byMeal (ккал)")
        val unverified = x.entries.filter { !it.verified }
        appendLine("Непроверенные продукты: ${unverified.size} ${plural(unverified.size, "запись", "записи", "записей")}, ${i(unverified.sumOf { it.kcal })} ккал")
        if (x.weightKg != null) {
            val avg = x.weightAvg7?.takeIf { it.kg != null }
                ?.let { " · среднее 7 дн: ${d1(it.kg!!)} кг, из ${it.count} ${plural(it.count, "замера", "замеров", "замеров")}" }.orEmpty()
            appendLine("Вес: ${d1(x.weightKg)} кг$avg")
        }
        appendLine("Перенос целей: " + (x.shiftedFrom?.let { "с ${day(it)}" + (x.dayType?.let { t -> " ($t)" } ?: "") } ?: "нет"))
        append("Источник: План дня ${x.appVersion} (параллель с Lifesum)")
        if (withProducts) {
            appendLine()
            appendLine()
            append("Продукты:")
            val order = x.meals.withIndex().associate { it.value to it.index }
            x.entries.sortedBy { order[it.meal] ?: Int.MAX_VALUE }.forEach { e ->
                appendLine()
                append("${e.meal.lowercase(RU)} · ${e.name} · ${i(e.grams)} г · ${i(e.kcal)} ккал · Б ${d1(e.protein)} · Ж ${d1(e.fat)} · У ${d1(e.carbs)}")
                if (!e.verified) append(" · не проверено")
            }
            if (x.entries.isEmpty()) append(" записей нет")
        }
    }

    fun day(date: LocalDate): String = "${DOW[date.dayOfWeek.value - 1]} ${date.format(DDMM)}"

    /** «2200 ккал ±5%», «Б ≥190 г», «Ж 65 г ±5% (≤75)», без смысла — просто число. */
    fun goal(g: Goal, unit: String, prefix: String): String {
        val v = "${i(g.value)} $unit"
        if (!g.specified) return prefix + v
        return when (g.mode) {
            GoalMode.MIN -> "$prefix≥$v"
            GoalMode.TARGET -> prefix + v + (g.tolPct?.let { " ±${i(it)}%" } ?: "") + (g.max?.let { " (≤${i(it)})" } ?: "")
        }
    }

    private fun i(x: Double) = x.roundToInt().toString()
    private fun d1(x: Double) = String.format(RU, "%.1f", x)
    private fun signed(x: Double): String {
        val r = x.roundToInt()
        return when {
            r > 0 -> "+$r"
            r < 0 -> "−${abs(r)}"
            else -> "0"
        }
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }
}
