package app.dayplan.nutrition.data

import android.content.Context
import androidx.room.withTransaction
import app.dayplan.data.ChecklistStore
import app.dayplan.data.DayLookup
import app.dayplan.data.PlanStore
import app.dayplan.model.Nutrition
import app.dayplan.nutrition.domain.Barcodes
import app.dayplan.nutrition.domain.DayGoals
import app.dayplan.nutrition.domain.GoalsMapper
import app.dayplan.nutrition.domain.Nutrients
import app.dayplan.nutrition.domain.WeightAverage
import app.dayplan.nutrition.domain.WeightPoint
import app.dayplan.nutrition.domain.Weights
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Цели дня из базы в готовом для экрана виде. */
data class DayTargetsView(
    val type: String?,
    val goals: DayGoals,
    val notes: List<String>,
    val originDate: String,
    val date: String,
) {
    val shifted: Boolean get() = originDate != date
}

data class WeightView(
    val today: WeightEntryEntity?,
    val rolling: WeightAverage,
    val week: WeightAverage?,
    val previousWeek: WeightAverage?,
    val latest: List<WeightEntryEntity>,
)

sealed interface BarcodeLookup {
    data class Own(val product: ProductEntity) : BarcodeLookup
    data class Off(val product: OffProduct) : BarcodeLookup
    /** offError — Open Food Facts не ответил (нет сети); код тогда не найден только в своей базе. */
    data class NotFound(val code: String, val offError: String?) : BarcodeLookup
}

/**
 * refProvider вызывается лениво, при первом обращении к справочной базе (уже не в главном потоке):
 * открытие справочной базы копирует файл из сборки.
 */
class NutritionRepo(private val db: NutritionDatabase, refProvider: () -> ReferenceSource? = { null }) {

    private val ref: ReferenceSource? by lazy(refProvider)


    private val json = Json { ignoreUnknownKeys = true }

    // ---------- цели и дневник ----------

    suspend fun targets(date: LocalDate): DayTargetsView? {
        val t = db.dayTargets().forDate(date.toString()) ?: return null
        val n = Nutrition(
            type = t.type,
            kcal = t.kcal,
            proteinG = t.proteinG,
            fatG = t.fatG,
            carbsG = t.carbsG,
            notes = t.notesJson?.let { json.decodeFromString<List<String>>(it) }.orEmpty(),
            targets = TargetsImport.decodeTargets(t.targetsJson),
            fuelFor = t.fuelFor,
        )
        return DayTargetsView(n.type, GoalsMapper.from(n), n.notes, t.originDate, t.date)
    }

    suspend fun entries(date: LocalDate): List<LogEntryEntity> = db.log().forDate(date.toString())

    suspend fun addEntry(date: LocalDate, meal: String, product: ProductEntity, grams: Double, now: Long = System.currentTimeMillis()): Long =
        db.log().insert(LogMath.snapshot(product, grams).toEntry(date.toString(), meal, product, grams, now))

    /** Новый вес записи: пересчёт от карточки, а если её удалили — пропорционально прежнему снимку. */
    suspend fun updateEntryGrams(entry: LogEntryEntity, grams: Double) {
        val product = entry.productId?.let { db.products().byId(it) }
        val n = if (product != null) LogMath.snapshot(product, grams) else LogMath.rescale(entry, grams)
        db.log().update(
            entry.copy(
                grams = grams, kcal = n.kcal, protein = n.protein, fat = n.fat, carbs = n.carbs, fiber = n.fiber,
                productVerified = product?.verified ?: entry.productVerified,
            ),
        )
    }

    suspend fun moveEntry(entry: LogEntryEntity, meal: String) = db.log().update(entry.copy(meal = meal))

    suspend fun deleteEntry(entry: LogEntryEntity) = db.log().delete(entry)

    // ---------- продукты ----------

    suspend fun product(id: Long) = db.products().byId(id)

    suspend fun portions(productId: Long) = db.products().portions(productId)

    /** Пустой запрос — недавние, затем последние заведённые; иначе поиск по названию и бренду. */
    suspend fun findProducts(query: String): List<ProductEntity> {
        val key = normalizeSearch(query)
        if (key.isEmpty()) {
            val recent = db.products().recent()
            return (recent + db.products().latest()).distinctBy { it.id }
        }
        return db.products().search(key)
    }

    suspend fun saveProduct(
        product: ProductEntity,
        portions: List<ProductPortionEntity>,
        barcodes: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        val row = product.copy(searchKey = searchKeyOf(product.name, product.brand), updatedAt = now)
        val id = if (row.id == 0L) db.products().insert(row) else row.id.also { db.products().update(row) }
        db.products().deletePortions(id)
        if (portions.isNotEmpty()) {
            val oneDefault = portions.indexOfFirst { it.isDefault }.coerceAtLeast(0)
            db.products().insertPortions(portions.mapIndexed { i, p -> p.copy(id = 0, productId = id, isDefault = i == oneDefault) })
        }
        db.products().deleteBarcodes(id)
        val codes = barcodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (codes.isNotEmpty()) db.products().insertBarcodes(codes.map { ProductBarcodeEntity(it, id, now) })
        id
    }

    suspend fun barcodes(productId: Long): List<String> = db.products().barcodes(productId).map { it.barcode }

    /** Продукт, к которому код уже привязан (кроме exceptId). */
    suspend fun barcodeOwner(code: String, exceptId: Long = 0): ProductEntity? =
        db.products().byBarcode(Barcodes.variants(code))?.takeIf { it.id != exceptId }

    /** Привязать код к карточке из базы. Если он был у другой карточки — переходит к этой. */
    suspend fun linkBarcode(code: String, productId: Long, now: Long = System.currentTimeMillis()) =
        db.products().insertBarcodes(listOf(ProductBarcodeEntity(code, productId, now)))

    /** Поиск по коду: своя база, офлайн-выгрузка Open Food Facts, затем онлайн Open Food Facts. */
    suspend fun lookupBarcode(code: String, off: (String) -> OffResult): BarcodeLookup {
        db.products().byBarcode(Barcodes.variants(code))?.let { return BarcodeLookup.Own(it) }
        ref?.byBarcode(Barcodes.variants(code))?.let { return BarcodeLookup.Off(it.toOff().copy(code = code)) }
        var failure: String? = null
        for (v in Barcodes.variants(code)) {
            when (val r = off(v)) {
                is OffResult.Found -> return BarcodeLookup.Off(r.product.copy(code = code))
                is OffResult.Failed -> { failure = r.message; break }
                OffResult.NotFound -> Unit
            }
        }
        return BarcodeLookup.NotFound(code, failure)
    }

    /**
     * Справочная база по названию без того, что уже есть в своей: товары OFF с привязанным кодом
     * и продукты USDA, уже перенесённые в свою базу, не повторяются.
     */
    suspend fun findReference(query: String): List<RefProduct> {
        val found = ref?.search(query).orEmpty()
        if (found.isEmpty()) return found
        val codes = found.mapNotNull { it.barcode }.flatMap { Barcodes.variants(it) }
        val owned = if (codes.isEmpty()) emptySet() else db.products().ownedBarcodes(codes).toSet()
        val usdaIds = found.filter { it.isUsda }.map { it.sourceId }
        val adopted = if (usdaIds.isEmpty()) emptySet() else db.products().ownedExternal(ProductSource.USDA, usdaIds).toSet()
        return found.filter { r ->
            if (r.isUsda) r.sourceId !in adopted else Barcodes.variants(r.barcode ?: "").none { it in owned }
        }
    }

    /** Продукт USDA переносится в свою базу как справочный (проверенный), один раз. */
    suspend fun adoptUsda(r: RefProduct, now: Long = System.currentTimeMillis()): ProductEntity {
        db.products().byExternal(ProductSource.USDA, r.sourceId)?.let { return it }
        val id = saveProduct(
            ProductEntity(
                name = r.name, kind = ProductKind.GENERIC,
                kcal100 = r.kcal, protein100 = r.protein, fat100 = r.fat, carbs100 = r.carbs, fiber100 = r.fiber,
                swellK = r.swellK, swellKMin = r.swellKMin, swellKMax = r.swellKMax,
                source = ProductSource.USDA, externalId = r.sourceId, verified = true, updatedAt = now,
            ),
            emptyList(), emptyList(), now,
        )
        return db.products().byId(id)!!
    }

    // ---------- вес ----------

    suspend fun saveWeight(date: LocalDate, kg: Double, now: Long = System.currentTimeMillis()) =
        db.weights().upsert(WeightEntryEntity(date.toString(), kg, now))

    suspend fun deleteWeight(date: LocalDate) = db.weights().delete(date.toString())

    suspend fun weightView(date: LocalDate): WeightView {
        val from = date.minusDays(20)
        val points = db.weights().between(from.toString(), date.toString()).map { WeightPoint(LocalDate.parse(it.date), it.kg) }
        val week = db.planImports().weekStartingOnOrBefore(date.toString())
            ?.let { LocalDate.parse(it.weekStart) }
            ?.takeIf { !date.isAfter(it.plusDays(6)) }
        return WeightView(
            today = db.weights().forDate(date.toString()),
            rolling = Weights.rolling7(points, date),
            week = week?.let { Weights.week(points, it) },
            previousWeek = week?.let { Weights.week(points, it.minusDays(7)) },
            latest = db.weights().latest(),
        )
    }
}

/** Снимок КБЖУ записи на момент внесения. */
object LogMath {
    fun snapshot(p: ProductEntity, grams: Double): Nutrients =
        Nutrients(p.kcal100, p.protein100, p.fat100, p.carbs100, p.fiber100).scaled(grams / 100.0)

    fun rescale(e: LogEntryEntity, grams: Double): Nutrients {
        val f = if (e.grams > 0) grams / e.grams else 0.0
        return Nutrients(e.kcal, e.protein, e.fat, e.carbs, e.fiber).scaled(f)
    }

    fun total(entries: List<LogEntryEntity>): Nutrients =
        entries.fold(Nutrients.ZERO) { acc, e -> acc + Nutrients(e.kcal, e.protein, e.fat, e.carbs, e.fiber) }
}

private fun Nutrients.toEntry(date: String, meal: String, p: ProductEntity, grams: Double, now: Long) = LogEntryEntity(
    date = date, meal = meal, productId = p.id, productName = p.name, grams = grams,
    kcal = kcal, protein = protein, fat = fat, carbs = carbs, fiber = fiber,
    productVerified = p.verified, createdAt = now,
)

/** Автоотметка пунктов чек-листа плана: пункт с auto = kind ставится, когда данные за день внесены. */
object AutoChecks {
    const val WEIGHT = "weight"
    const val NUTRITION = "nutrition"

    fun mark(context: Context, date: LocalDate, kind: String) {
        val found = DayLookup.find(PlanStore(context).all(), date) ?: return
        val store = ChecklistStore(context)
        found.day.report.forEachIndexed { i, item ->
            if (item.auto == kind) store.set(store.key(found.day.date, i, item), true)
        }
    }
}
