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
)
