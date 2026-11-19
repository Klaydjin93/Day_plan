package app.dayplan.nutrition.domain

/** КБЖУ и клетчатка — на 100 г или на всё количество, по контексту. Клетчатка необязательна. */
data class Nutrients(
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double? = null,
) {
    operator fun plus(o: Nutrients) = Nutrients(
        kcal + o.kcal,
        protein + o.protein,
        fat + o.fat,
        carbs + o.carbs,
        if (fiber == null && o.fiber == null) null else (fiber ?: 0.0) + (o.fiber ?: 0.0),
    )

    fun scaled(factor: Double) = Nutrients(kcal * factor, protein * factor, fat * factor, carbs * factor, fiber?.times(factor))

    companion object {
        val ZERO = Nutrients(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Ингредиент блюда: значения на 100 г и вес в блюде так, как взвешен при готовке (сырым или сухим).
 * swellK — во сколько раз продукт тяжелеет при варке (крупы, макароны); swellKMin/Max — диапазон для оценки.
 */
data class Ingredient(
    val per100: Nutrients,
    val grams: Double,
    val swellK: Double? = null,
    val swellKMin: Double? = null,
    val swellKMax: Double? = null,
)

enum class YieldMethod { WEIGHED, SWELL, RAW_SUM }

/** Расчёт блюда — та же схема, что в чате: итог по ингредиентам ÷ вес готового × 100. */
object DishMath {

    fun totals(ingredients: List<Ingredient>): Nutrients =
        ingredients.fold(Nutrients.ZERO) { acc, i -> acc + i.per100.scaled(i.grams / 100.0) }

    fun rawSum(ingredients: List<Ingredient>): Double = ingredients.sumOf { it.grams }

    /** Сумма сырых весов плюс вода, которую впитали крупы и макароны: сухой вес × (k − 1). */
    fun swellYield(ingredients: List<Ingredient>, k: (Ingredient) -> Double? = { it.swellK }): Double =
        ingredients.sumOf { i -> i.grams + i.grams * ((k(i) ?: 1.0) - 1.0) }

    /** Вес готового по краям диапазона k: (наименьший, наибольший). Без диапазона берётся основной k. */
    fun swellYieldRange(ingredients: List<Ingredient>): Pair<Double, Double> =
        swellYield(ingredients) { it.swellKMin ?: it.swellK } to swellYield(ingredients) { it.swellKMax ?: it.swellK }

    fun per100(totals: Nutrients, yieldGrams: Double): Nutrients {
        require(yieldGrams > 0) { "Вес готового блюда должен быть больше нуля" }
        return totals.scaled(100.0 / yieldGrams)
    }

    /** Вес готового по взвешиванию: брутто минус вес пустой посуды. */
    fun weighedYield(grossGrams: Double, tareGrams: Double): Double {
        val net = grossGrams - tareGrams
        require(net > 0) { "Вес с посудой должен быть больше веса посуды" }
        return net
    }
}
