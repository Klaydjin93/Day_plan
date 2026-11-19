package app.dayplan.nutrition.domain

import app.dayplan.model.Nutrition
import app.dayplan.model.TargetSpec

enum class GoalMode { MIN, TARGET }

/** Зона факта относительно цели — по ней красится прогресс-бар. */
enum class Zone { BELOW, OK, WARN, OVER }

/**
 * Одна цель дня.
 * MIN — минимум, больше нормально. TARGET — цель; tolPct задаёт коридор ±, max — жёсткий потолок.
 * specified = false: смысл цели планом не задан, число показывается как простая цель.
 */
data class Goal(
    val value: Double,
    val mode: GoalMode,
    val tolPct: Double? = null,
    val max: Double? = null,
    val specified: Boolean = true,
) {
    val lowerEdge: Double get() = if (mode == GoalMode.TARGET && tolPct != null) value * (1 - tolPct / 100) else value
    val upperEdge: Double get() = if (mode == GoalMode.TARGET && tolPct != null) value * (1 + tolPct / 100) else value

    fun zone(actual: Double): Zone = when (mode) {
        GoalMode.MIN -> if (actual >= value) Zone.OK else Zone.BELOW
        GoalMode.TARGET -> when {
            actual < lowerEdge -> Zone.BELOW
            actual <= upperEdge -> Zone.OK
            max != null && actual <= max -> Zone.WARN
            else -> Zone.OVER
        }
    }
}

data class DayGoals(
    val kcal: Goal,
    val protein: Goal,
    val fat: Goal,
    val carbs: Goal,
) {
    /** Все четыре цели получили смысл из плана. */
    val fromPlan: Boolean get() = listOf(kcal, protein, fat, carbs).all { it.specified }
}

object GoalsMapper {

    fun from(n: Nutrition): DayGoals {
        val t = n.targets
        return DayGoals(
            kcal = goal(n.kcal, t?.kcal),
            protein = goal(n.proteinG, t?.proteinG),
            fat = goal(n.fatG, t?.fatG),
            carbs = goal(n.carbsG, t?.carbsG),
        )
    }

    private fun goal(value: Int, spec: TargetSpec?): Goal {
        if (spec == null) return Goal(value.toDouble(), GoalMode.TARGET, specified = false)
        val mode = if (spec.mode.equals("min", ignoreCase = true)) GoalMode.MIN else GoalMode.TARGET
        return Goal(
            value = value.toDouble(),
            mode = mode,
            tolPct = spec.tolPct.takeIf { mode == GoalMode.TARGET },
            max = spec.max.takeIf { mode == GoalMode.TARGET },
        )
    }
}
