package app.dayplan.nutrition.data

import android.content.Context
import androidx.room.withTransaction
import app.dayplan.data.PlanStore
import app.dayplan.model.NutritionTargets
import app.dayplan.model.Plan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Перенос целей питания из плана в базу. Логика решения — без Android, проверяется обычными тестами. */
object TargetsImport {

    private val json = Json { encodeDefaults = false }

    /**
     * Брать ли эту версию недели. Правило то же, что у DayLookup: побеждает более свежий generated;
     * при равном generated — последний импорт (перевыпуск того же дня или повторная загрузка).
     */
    fun shouldApply(existing: PlanImportEntity?, plan: Plan): Boolean {
        if (existing == null) return true
        return (plan.week.generated ?: "") >= (existing.generated ?: "")
    }

    fun rows(plan: Plan): List<DayTargetsEntity> =
        plan.days.mapNotNull { day ->
            val n = day.nutrition ?: return@mapNotNull null
            DayTargetsEntity(
                date = day.date,
                weekId = plan.week.id,
                type = n.type,
                kcal = n.kcal,
                proteinG = n.proteinG,
                fatG = n.fatG,
                carbsG = n.carbsG,
                targetsJson = n.targets?.let { json.encodeToString(it) },
                fuelFor = n.fuelFor,
                originDate = day.date,
                notesJson = n.notes.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            )
        }

    fun importRow(plan: Plan, now: Long) = PlanImportEntity(
        weekId = plan.week.id,
        weekStart = plan.week.start,
        planVersion = plan.week.planVersion,
        generated = plan.week.generated,
        kcalAvg = plan.week.nutrition?.kcalAvg,
        importedAt = now,
    )

    fun decodeTargets(text: String?): NutritionTargets? = text?.let { json.decodeFromString<NutritionTargets>(it) }
}

class NutritionSync(private val db: NutritionDatabase) {

    /**
     * Записывает цели недели. Возвращает false, если в базе уже более свежая версия этой недели.
     * Цели недели заменяются целиком. Переносы сессий (этап 5) будут применяться поверх после записи.
     */
    suspend fun syncPlan(plan: Plan, now: Long = System.currentTimeMillis()): Boolean = db.withTransaction {
        val existing = db.planImports().get(plan.week.id)
        if (!TargetsImport.shouldApply(existing, plan)) return@withTransaction false
        db.dayTargets().deleteWeek(plan.week.id)
        db.dayTargets().upsertAll(TargetsImport.rows(plan))
        db.planImports().upsert(TargetsImport.importRow(plan, now))
        true
    }

    companion object {
        /**
         * Подтягивает цели из всех сохранённых планов. Нужен один раз после обновления приложения
         * (планы загружены до появления модуля питания) и безопасен при повторе.
         */
        suspend fun syncAll(context: Context) {
            val sync = NutritionSync(NutritionDatabase.get(context))
            PlanStore(context).all()
                .sortedWith(compareBy({ it.plan.week.generated ?: "" }, { it.importedAt }))
                .forEach { sync.syncPlan(it.plan, it.importedAt) }
        }
    }
}
