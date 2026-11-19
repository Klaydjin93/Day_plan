package app.dayplan.nutrition.domain

import kotlin.math.roundToInt

/**
 * Геометрия полосы цели, доли от 0 до 1.
 * Шкала выбрана так, что граница, которую нельзя переходить, стоит у правого края:
 * минимум — для MIN, потолок — для цели с потолком, верх коридора — для цели с коридором.
 */
data class BarGeometry(
    val fill: Float,
    val marker: Float?,
    val corridorStart: Float?,
    val corridorEnd: Float?,
    val zone: Zone,
)

fun Goal.bar(actual: Double): BarGeometry {
    val scaleMax = when {
        mode == GoalMode.MIN -> value
        max != null -> max
        tolPct != null -> upperEdge
        else -> value
    }.coerceAtLeast(1e-9)
    fun frac(x: Double) = (x / scaleMax).toFloat().coerceIn(0f, 1f)
    val hasCorridor = mode == GoalMode.TARGET && tolPct != null
    return BarGeometry(
        fill = frac(actual),
        marker = frac(value),
        corridorStart = if (hasCorridor) frac(lowerEdge) else null,
        corridorEnd = if (hasCorridor) frac(upperEdge) else null,
        zone = zone(actual),
    )
}

/** Подпись под полосой: сколько осталось или насколько вышли за границу. */
fun Goal.remainder(actual: Double, unit: String): String {
    fun n(x: Double) = "${x.roundToInt()} $unit"
    return when (mode) {
        GoalMode.MIN ->
            if (actual < value) "до минимума ${n(value - actual)}"
            else if (actual - value >= 0.5) "минимум набран, +${n(actual - value)}"
            else "минимум набран"
        GoalMode.TARGET -> when (zone(actual)) {
            Zone.BELOW -> "осталось ${n(value - actual)}"
            Zone.OK -> if (tolPct != null) "в коридоре" else "цель достигнута"
            Zone.WARN -> "выше коридора, до потолка ${n((max ?: actual) - actual)}"
            Zone.OVER -> "сверх ${if (max != null) "потолка" else "цели"} +${n(actual - (max ?: upperEdge))}"
        }
    }
}

/** Значение цели для подписи: «≥ 190», «2 200 ± 5 %», «65 (≤ 75)». */
fun Goal.label(): String {
    val v = value.roundToInt().toString()
    return when {
        mode == GoalMode.MIN -> "≥ $v"
        max != null && tolPct != null -> "$v ± ${tolPct.roundToInt()} % (≤ ${max.roundToInt()})"
        max != null -> "$v (≤ ${max.roundToInt()})"
        tolPct != null -> "$v ± ${tolPct.roundToInt()} %"
        else -> v
    }
}
