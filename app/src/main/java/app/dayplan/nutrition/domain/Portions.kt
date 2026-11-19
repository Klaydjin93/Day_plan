package app.dayplan.nutrition.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** Порция для ввода «сколько порций»: подпись и вес одной порции. */
data class PortionOption(val label: String, val grams: Double, val isDefault: Boolean = false, val wholeDish: Boolean = false) {
    /** Шаг кнопок −/+: целое блюдо делят на четверти, обычную порцию — на половинки. */
    val step: Double get() = if (wholeDish) 0.25 else 0.5
}

object Portions {
    const val WHOLE_DISH = "всё блюдо"

    /**
     * Узнаёт в граммах записи целое число шагов какой-то порции: 525 г при порции 350 г → 1,5 порции.
     * Сначала проверяется порция по умолчанию, затем остальные; целое блюдо — последним.
     */
    fun match(grams: Double, options: List<PortionOption>): Pair<PortionOption, Double>? {
        val ordered = options.sortedWith(compareBy({ it.wholeDish }, { !it.isDefault }))
        for (p in ordered) {
            if (p.grams <= 0) continue
            val count = (grams / p.grams / p.step).roundToInt() * p.step
            if (count > 0 && abs(count * p.grams - grams) < 0.5) return p to count
        }
        return null
    }

    fun grams(option: PortionOption, count: Double): Double = option.grams * count

    /** «1,5 × контейнер» / «¼ × всё блюдо» — для подписи под полем. */
    fun countLabel(count: Double): String = when (count) {
        0.25 -> "¼"
        0.5 -> "½"
        0.75 -> "¾"
        else -> if (count == count.roundToInt().toDouble()) count.roundToInt().toString()
        else String.format(java.util.Locale.forLanguageTag("ru"), "%.2f", count).trimEnd('0').trimEnd(',')
    }
}
