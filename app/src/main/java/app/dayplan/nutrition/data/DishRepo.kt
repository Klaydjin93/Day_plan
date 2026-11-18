package app.dayplan.nutrition.data

import androidx.room.withTransaction
import app.dayplan.nutrition.domain.DishMath
import app.dayplan.nutrition.domain.Ingredient
import app.dayplan.nutrition.domain.Nutrients
import app.dayplan.nutrition.domain.YieldMethod

data class DishLine(val product: ProductEntity, val grams: Double) {
    fun ingredient() = Ingredient(
        Nutrients(product.kcal100, product.protein100, product.fat100, product.carbs100, product.fiber100),
        grams, product.swellK, product.swellKMin, product.swellKMax,
    )
}

data class DishView(val product: ProductEntity, val dish: DishEntity, val lines: List<DishLine>)

/** Что сохранить: состав, способ веса готового и сам вес. */
data class DishInput(
    val productId: Long,
    val name: String,
    val lines: List<DishLine>,
    val yieldMethod: YieldMethod,
    val yieldGrams: Double,
    val cookwareId: Long?,
    val batchOf: Long?,
    val cookedOn: String?,
)

data class TemplateView(val template: TemplateEntity, val lines: List<DishLine>) {
    val kcal: Double get() = lines.sumOf { it.product.kcal100 * it.grams / 100 }
}

/** Блюда, посуда и шаблоны приёмов пищи. */
class DishRepo(private val db: NutritionDatabase) {

    suspend fun dish(productId: Long): DishView? {
        val dish = db.dishes().get(productId) ?: return null
        val product = db.products().byId(productId) ?: return null
        val lines = db.dishes().ingredients(productId).mapNotNull { i -> db.products().byId(i.productId)?.let { DishLine(it, i.grams) } }
        return DishView(product, dish, lines)
    }

    /**
     * Сохраняет блюдо как карточку с КБЖУ на 100 г готового веса. Блюдо проверенное, только если
     * проверены все ингредиенты. Возвращает id карточки.
     */
    suspend fun save(input: DishInput, now: Long = System.currentTimeMillis()): Long = db.withTransaction {
        require(input.lines.isNotEmpty()) { "В блюде нет ингредиентов" }
        val totals = DishMath.totals(input.lines.map { it.ingredient() })
        val per100 = DishMath.per100(totals, input.yieldGrams)
        val row = ProductEntity(
            id = input.productId,
            name = input.name.trim(),
            searchKey = searchKeyOf(input.name, null),
            kind = ProductKind.DISH,
            kcal100 = per100.kcal, protein100 = per100.protein, fat100 = per100.fat, carbs100 = per100.carbs, fiber100 = per100.fiber,
            source = ProductSource.DISH,
            verified = input.lines.all { it.product.verified },
            updatedAt = now,
        )
        val id = if (row.id == 0L) db.products().insert(row) else row.id.also { db.products().update(row) }
        db.dishes().upsert(
            DishEntity(
                productId = id,
                yieldMethod = when (input.yieldMethod) {
                    YieldMethod.WEIGHED -> YieldMethodCode.WEIGHED
                    YieldMethod.SWELL -> YieldMethodCode.SWELL
                    YieldMethod.RAW_SUM -> YieldMethodCode.RAW_SUM
                },
                yieldG = input.yieldGrams,
                cookwareId = input.cookwareId,
                batchOf = input.batchOf,
                cookedOn = input.cookedOn,
            ),
        )
        db.dishes().deleteIngredients(id)
        db.dishes().insertIngredients(input.lines.mapIndexed { i, l -> DishIngredientEntity(dishId = id, productId = l.product.id, grams = l.grams, position = i) })
        id
    }

    /** Записи дневника этой партии — чтобы после взвешивания пересчитать их по новому КБЖУ на 100 г. */
    suspend fun entriesOf(productId: Long): List<LogEntryEntity> = db.log().forProduct(productId)

    /** Пересчитать записи партии по текущей карточке блюда. Возвращает число записей. */
    suspend fun reapply(productId: Long): Int = db.withTransaction {
        val p = db.products().byId(productId) ?: return@withTransaction 0
        val entries = db.log().forProduct(productId)
        entries.forEach { e ->
            val n = LogMath.snapshot(p, e.grams)
            db.log().update(e.copy(productName = p.name, kcal = n.kcal, protein = n.protein, fat = n.fat, carbs = n.carbs, fiber = n.fiber, productVerified = p.verified))
        }
        entries.size
    }

    suspend fun cookware() = db.dishes().cookware()

    suspend fun addCookware(name: String, tareG: Double): Long = db.dishes().insertCookware(CookwareEntity(name = name.trim(), tareG = tareG))

    suspend fun deleteCookware(id: Long) = db.dishes().deleteCookware(id)

    // ---------- шаблоны ----------

    /** Шаблон из записей приёма пищи: продукты и граммы. Записи без карточки (её удалили) пропускаются. */
    suspend fun saveTemplate(name: String, entries: List<LogEntryEntity>): Long = db.withTransaction {
        val id = db.templates().insert(TemplateEntity(name = name.trim()))
        db.templates().insertItems(entries.mapNotNull { e -> e.productId?.let { TemplateItemEntity(templateId = id, productId = it, grams = e.grams) } })
        id
    }

    suspend fun templates(): List<TemplateView> = db.templates().all().map { t ->
        TemplateView(t, db.templates().items(t.id).mapNotNull { i -> db.products().byId(i.productId)?.let { DishLine(it, i.grams) } })
    }

    suspend fun deleteTemplate(id: Long) = db.templates().delete(id)
}
