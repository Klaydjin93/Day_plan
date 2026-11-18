package app.dayplan.nutrition.domain

import java.time.LocalDate

data class WeightPoint(val date: LocalDate, val kg: Double)

data class WeightAverage(val kg: Double?, val count: Int, val from: LocalDate, val to: LocalDate) {
    /** Меньше трёх замеров — среднее показывается с пометкой «мало данных». */
    val thin: Boolean get() = count < MIN_POINTS

    companion object {
        const val MIN_POINTS = 3
    }
}

/** Вес читается только средним: дневные колебания — вода, гликоген, креатин, а не жир. */
object Weights {

    fun average(points: List<WeightPoint>, from: LocalDate, to: LocalDate): WeightAverage {
        val inRange = points.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        return WeightAverage(inRange.map { it.kg }.average().takeIf { inRange.isNotEmpty() }, inRange.size, from, to)
    }

    /** Скользящее: последние 7 календарных дней по дату включительно; пропуски не досчитываются. */
    fun rolling7(points: List<WeightPoint>, date: LocalDate) = average(points, date.minusDays(6), date)

    /** Неделя плана ПН–ВС по её началу. */
    fun week(points: List<WeightPoint>, weekStart: LocalDate) = average(points, weekStart, weekStart.plusDays(6))
}

/** Разбор числа, введённого с телефона: запятая и точка, пробелы. */
fun parseDecimal(text: String): Double? =
    text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
