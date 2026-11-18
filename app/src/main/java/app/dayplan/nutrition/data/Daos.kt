package app.dayplan.nutrition.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun byId(id: Long): ProductEntity?

    @Query("SELECT * FROM product WHERE barcode = :barcode ORDER BY verified DESC, updated_at DESC LIMIT 1")
    suspend fun byBarcode(barcode: String): ProductEntity?
}
