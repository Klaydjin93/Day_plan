package app.dayplan.nutrition.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface DayTargetsDao {
    @Upsert
    suspend fun upsertAll(rows: List<DayTargetsEntity>)

    @Query("SELECT * FROM day_targets WHERE date = :date")
    suspend fun forDate(date: String): DayTargetsEntity?

    @Query("SELECT * FROM day_targets WHERE week_id = :weekId ORDER BY date")
    suspend fun forWeek(weekId: String): List<DayTargetsEntity>

    @Query("DELETE FROM day_targets WHERE week_id = :weekId")
    suspend fun deleteWeek(weekId: String)
}

@Dao
interface PlanImportDao {
    @Query("SELECT * FROM plan_import WHERE week_id = :weekId")
    suspend fun get(weekId: String): PlanImportEntity?

    @Upsert
    suspend fun upsert(row: PlanImportEntity)

    /** Неделя плана, в которую попадает дата (недели идут ПН–ВС от week_start). */
    @Query("SELECT * FROM plan_import WHERE week_start <= :date ORDER BY week_start DESC LIMIT 1")
    suspend fun weekStartingOnOrBefore(date: String): PlanImportEntity?
}

@Dao
interface DayShiftDao {
    @Query("SELECT * FROM day_shift WHERE week_id = :weekId ORDER BY id")
    suspend fun forWeek(weekId: String): List<DayShiftEntity>

    @Insert
    suspend fun insert(row: DayShiftEntity): Long
}

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: ProductEntity): Long

    @Update
    suspend fun update(row: ProductEntity)

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun byId(id: Long): ProductEntity?

    /** Продукт по любому из равнозначных вариантов кода (EAN-13 с ведущим нулём и без, UPC-A). */
    @Query("SELECT p.* FROM product p JOIN product_barcode b ON b.product_id = p.id WHERE b.barcode IN (:codes) LIMIT 1")
    suspend fun byBarcode(codes: List<String>): ProductEntity?

    @Query("SELECT * FROM product WHERE source = :source AND external_id = :externalId LIMIT 1")
    suspend fun byExternal(source: String, externalId: String): ProductEntity?

    @Query("SELECT barcode FROM product_barcode WHERE barcode IN (:codes)")
    suspend fun ownedBarcodes(codes: List<String>): List<String>

    @Query("SELECT external_id FROM product WHERE source = :source AND external_id IN (:ids)")
    suspend fun ownedExternal(source: String, ids: List<String>): List<String>

    @Query("SELECT * FROM product_barcode WHERE product_id = :productId ORDER BY created_at")
    suspend fun barcodes(productId: Long): List<ProductBarcodeEntity>

    @Query("SELECT * FROM product_barcode WHERE barcode = :barcode")
    suspend fun barcode(barcode: String): ProductBarcodeEntity?

    @Query("DELETE FROM product_barcode WHERE product_id = :productId")
    suspend fun deleteBarcodes(productId: Long)

    /** Код, привязанный к другому продукту, переходит к этому. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarcodes(rows: List<ProductBarcodeEntity>)

    /** Поиск по своей базе: сначала проверенные, потом по названию. key — уже нормализован. */
    @Query("SELECT * FROM product WHERE search_key LIKE '%' || :key || '%' ORDER BY verified DESC, name LIMIT :limit")
    suspend fun search(key: String, limit: Int = 50): List<ProductEntity>

    /** Недавние: по последней записи в дневнике. */
    @Query(
        """
        SELECT p.* FROM product p
        JOIN (SELECT product_id, MAX(created_at) AS last_at FROM log_entry WHERE product_id IS NOT NULL GROUP BY product_id) l
          ON l.product_id = p.id
        ORDER BY l.last_at DESC LIMIT :limit
        """,
    )
    suspend fun recent(limit: Int = 20): List<ProductEntity>

    @Query("SELECT * FROM product ORDER BY updated_at DESC LIMIT :limit")
    suspend fun latest(limit: Int = 20): List<ProductEntity>

    @Insert
    suspend fun insertPortions(rows: List<ProductPortionEntity>)

    @Query("DELETE FROM product_portion WHERE product_id = :productId")
    suspend fun deletePortions(productId: Long)

    @Query("SELECT * FROM product_portion WHERE product_id = :productId ORDER BY is_default DESC, grams")
    suspend fun portions(productId: Long): List<ProductPortionEntity>
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(row: LogEntryEntity): Long

    @Update
    suspend fun update(row: LogEntryEntity)

    @Delete
    suspend fun delete(row: LogEntryEntity)

    @Query("SELECT * FROM log_entry WHERE date = :date ORDER BY created_at")
    suspend fun forDate(date: String): List<LogEntryEntity>

    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun byId(id: Long): LogEntryEntity?

    @Query("SELECT * FROM log_entry WHERE product_id = :productId ORDER BY date, created_at")
    suspend fun forProduct(productId: Long): List<LogEntryEntity>
}

@Dao
interface DishDao {
    @Upsert
    suspend fun upsert(row: DishEntity)

    @Query("SELECT * FROM dish WHERE product_id = :productId")
    suspend fun get(productId: Long): DishEntity?

    @Query("DELETE FROM dish_ingredient WHERE dish_id = :dishId")
    suspend fun deleteIngredients(dishId: Long)

    @Insert
    suspend fun insertIngredients(rows: List<DishIngredientEntity>)

    @Query("SELECT * FROM dish_ingredient WHERE dish_id = :dishId ORDER BY position")
    suspend fun ingredients(dishId: Long): List<DishIngredientEntity>

    @Query("SELECT * FROM cookware ORDER BY name")
    suspend fun cookware(): List<CookwareEntity>

    @Insert
    suspend fun insertCookware(row: CookwareEntity): Long

    @Query("DELETE FROM cookware WHERE id = :id")
    suspend fun deleteCookware(id: Long)
}

@Dao
interface TemplateDao {
    @Insert
    suspend fun insert(row: TemplateEntity): Long

    @Insert
    suspend fun insertItems(rows: List<TemplateItemEntity>)

    @Query("SELECT * FROM template ORDER BY name")
    suspend fun all(): List<TemplateEntity>

    @Query("SELECT * FROM template_item WHERE template_id = :templateId ORDER BY id")
    suspend fun items(templateId: Long): List<TemplateItemEntity>

    @Query("DELETE FROM template WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WeightDao {
    @Upsert
    suspend fun upsert(row: WeightEntryEntity)

    @Query("DELETE FROM weight_entry WHERE date = :date")
    suspend fun delete(date: String)

    @Query("SELECT * FROM weight_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entry ORDER BY date DESC LIMIT :limit")
    suspend fun latest(limit: Int = 30): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entry WHERE date = :date")
    suspend fun forDate(date: String): WeightEntryEntity?
}

@Dao
interface DayExportDao {
    @Insert
    suspend fun insert(row: DayExportEntity): Long

    @Query("SELECT * FROM day_export WHERE date = :date ORDER BY exported_at DESC LIMIT 1")
    suspend fun last(date: String): DayExportEntity?
}
