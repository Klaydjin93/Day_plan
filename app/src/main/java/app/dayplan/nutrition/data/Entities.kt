package app.dayplan.nutrition.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

fun normalizeSearch(text: String): String = text.lowercase().replace('ё', 'е').trim()

fun searchKeyOf(name: String, brand: String?): String = normalizeSearch(listOfNotNull(name, brand).joinToString(" "))

/** Приёмы пищи — в порядке дня. */
object Meals {
    val ALL = listOf("Завтрак", "Обед", "Ужин", "Перекус", "После тренировки")
}

/** Тип карточки. */
object ProductKind {
    const val GENERIC = "generic"
    const val BRAND = "brand"
    const val DISH = "dish"
}

/** Откуда карточка. */
object ProductSource {
    const val OWN = "own"
    const val OFF = "off"
    const val USDA = "usda"
    const val MANUAL = "manual"
    const val DISH = "dish"
}

/** Карточка продукта или блюда, значения на 100 г. */
@Entity(tableName = "product", indices = [Index("barcode"), Index("search_key")])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    /** Название и бренд в нижнем регистре, ё → е: LIKE в SQLite не понижает регистр кириллицы. */
    @ColumnInfo(name = "search_key") val searchKey: String = searchKeyOf(name, brand),
    val barcode: String? = null,
    val kind: String = ProductKind.GENERIC,
    @ColumnInfo(name = "kcal_100") val kcal100: Double,
    @ColumnInfo(name = "protein_100") val protein100: Double,
    @ColumnInfo(name = "fat_100") val fat100: Double,
    @ColumnInfo(name = "carbs_100") val carbs100: Double,
    @ColumnInfo(name = "fiber_100") val fiber100: Double? = null,
    @ColumnInfo(name = "swell_k") val swellK: Double? = null,
    @ColumnInfo(name = "swell_k_min") val swellKMin: Double? = null,
    @ColumnInfo(name = "swell_k_max") val swellKMax: Double? = null,
    val source: String,
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    val verified: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "product_portion",
    foreignKeys = [ForeignKey(ProductEntity::class, ["id"], ["product_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("product_id")],
)
data class ProductPortionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    val label: String,
    val grams: Double,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
)

/** Как получен вес готового блюда. */
object YieldMethodCode {
    const val WEIGHED = "weighed"
    const val SWELL = "swell"
    const val RAW_SUM = "raw_sum"
}

/** Блюдо: сама карточка лежит в product (kind = dish), здесь — как посчитан вес готового. */
@Entity(
    tableName = "dish",
    foreignKeys = [
        ForeignKey(ProductEntity::class, ["id"], ["product_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CookwareEntity::class, ["id"], ["cookware_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("cookware_id"), Index("batch_of")],
)
data class DishEntity(
    @PrimaryKey @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "yield_method") val yieldMethod: String,
    @ColumnInfo(name = "yield_g") val yieldG: Double,
    @ColumnInfo(name = "cookware_id") val cookwareId: Long? = null,
    /** product_id первой партии этого рецепта; у первой партии — null. */
    @ColumnInfo(name = "batch_of") val batchOf: Long? = null,
    @ColumnInfo(name = "cooked_on") val cookedOn: String? = null,
)

@Entity(
    tableName = "dish_ingredient",
    foreignKeys = [
        ForeignKey(DishEntity::class, ["product_id"], ["dish_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductEntity::class, ["id"], ["product_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("dish_id"), Index("product_id")],
)
data class DishIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dish_id") val dishId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    val grams: Double,
    val position: Int,
)

@Entity(tableName = "cookware")
data class CookwareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "tare_g") val tareG: Double,
)

/**
 * Запись съеденного. Название и КБЖУ сохраняются снимком на момент записи:
 * правка или удаление карточки не переписывает прошлые дни.
 */
@Entity(
    tableName = "log_entry",
    foreignKeys = [ForeignKey(ProductEntity::class, ["id"], ["product_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("date"), Index("product_id")],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val meal: String,
    @ColumnInfo(name = "product_id") val productId: Long?,
    @ColumnInfo(name = "product_name") val productName: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double? = null,
    @ColumnInfo(name = "product_verified") val productVerified: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "template")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "template_item",
    foreignKeys = [
        ForeignKey(TemplateEntity::class, ["id"], ["template_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductEntity::class, ["id"], ["product_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("template_id"), Index("product_id")],
)
data class TemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "template_id") val templateId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    val grams: Double,
)

/** Какая версия недели легла в цели. Более старый перевыпуск цели не перетирает. */
@Entity(tableName = "plan_import")
data class PlanImportEntity(
    @PrimaryKey @ColumnInfo(name = "week_id") val weekId: String,
    @ColumnInfo(name = "week_start") val weekStart: String,
    @ColumnInfo(name = "plan_version") val planVersion: String?,
    val generated: String?,
    @ColumnInfo(name = "kcal_avg") val kcalAvg: Int?,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
)

/**
 * Цели дня — копия из плана. Живут дольше файлов плана (PlanStore удаляет их через 14 дней),
 * чтобы факт прошлых дней было с чем сравнить.
 */
@Entity(tableName = "day_targets", indices = [Index("week_id")])
data class DayTargetsEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "week_id") val weekId: String,
    val type: String?,
    val kcal: Int,
    @ColumnInfo(name = "protein_g") val proteinG: Int,
    @ColumnInfo(name = "fat_g") val fatG: Int,
    @ColumnInfo(name = "carbs_g") val carbsG: Int,
    @ColumnInfo(name = "targets_json") val targetsJson: String?,
    @ColumnInfo(name = "fuel_for") val fuelFor: String?,
    /** Из какого дня плана пришли цели; отличается от date после переноса. */
    @ColumnInfo(name = "origin_date") val originDate: String,
    @ColumnInfo(name = "notes_json") val notesJson: String?,
)

@Entity(tableName = "day_shift", indices = [Index("week_id")])
data class DayShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "week_id") val weekId: String,
    @ColumnInfo(name = "from_date") val fromDate: String,
    @ColumnInfo(name = "to_date") val toDate: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "weight_entry")
data class WeightEntryEntity(
    @PrimaryKey val date: String,
    val kg: Double,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    val note: String? = null,
)

@Entity(tableName = "day_export", indices = [Index("date")])
data class DayExportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "exported_at") val exportedAt: Long,
    val text: String,
)
