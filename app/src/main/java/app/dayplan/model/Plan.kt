package app.dayplan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Данные недельного плана, schema plan-day/1.
 * Неизвестные поля игнорируются — новые ключи в файле не ломают старое приложение.
 */
@Serializable
data class Plan(
    val schema: String,
    val week: Week,
    val days: List<Day>,
)

@Serializable
data class Week(
    val id: String,
    val start: String,
    val end: String,
    val title: String? = null,
    val subtitle: String? = null,
    @SerialName("plan_file") val planFile: String? = null,
    @SerialName("plan_version") val planVersion: String? = null,
    val generated: String? = null,
    @SerialName("nutrition_rules") val nutritionRules: List<String> = emptyList(),
    /** Недельные параметры питания. Нет в старых планах. */
    val nutrition: WeekNutrition? = null,
)

@Serializable
data class WeekNutrition(
    /** Среднее ккал в день по плану недели — по нему проверяется перенос высокого дня. */
    @SerialName("kcal_avg") val kcalAvg: Int? = null,
)

@Serializable
data class Day(
    val date: String,
    val dow: String,
    val kind: String,
    val nutrition: Nutrition? = null,
    val sessions: List<Session> = emptyList(),
    val report: List<ReportItem> = emptyList(),
)

@Serializable
data class Nutrition(
    val type: String? = null,
    val kcal: Int,
    @SerialName("protein_g") val proteinG: Int,
    @SerialName("fat_g") val fatG: Int,
    @SerialName("carbs_g") val carbsG: Int,
    val notes: List<String> = emptyList(),
    /** Смысл каждой цели: минимум, цель с коридором, цель с потолком. Нет в старых планах. */
    val targets: NutritionTargets? = null,
    /** Дата сессии, которую заправляет этот день (ISO). */
    @SerialName("fuel_for") val fuelFor: String? = null,
)

@Serializable
data class NutritionTargets(
    val kcal: TargetSpec? = null,
    @SerialName("protein_g") val proteinG: TargetSpec? = null,
    @SerialName("fat_g") val fatG: TargetSpec? = null,
    @SerialName("carbs_g") val carbsG: TargetSpec? = null,
)

/**
 * mode: "min" — минимум; "target" — цель. tol_pct — коридор ± в процентах, max — жёсткий потолок.
 * Неизвестный mode читается как "target".
 */
@Serializable
data class TargetSpec(
    val mode: String,
    @SerialName("tol_pct") val tolPct: Double? = null,
    val max: Double? = null,
)

@Serializable
data class Session(
    val time: String,
    val kind: String,
    val title: String,
    val meta: String? = null,
    val summary: List<String> = emptyList(),
    val table: SessionTable? = null,
)

@Serializable
data class SessionTable(
    val head: List<String>,
    val rows: List<List<String>>,
)

@Serializable
data class ReportItem(
    val item: String,
    @SerialName("when") val whenText: String? = null,
    /** "weight" | "nutrition" — пункт отмечается сам, когда в модуле питания есть данные за день. */
    val auto: String? = null,
)
