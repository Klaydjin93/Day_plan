package app.dayplan.nutrition.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class,
        ProductPortionEntity::class,
        ProductBarcodeEntity::class,
        DishEntity::class,
        DishIngredientEntity::class,
        CookwareEntity::class,
        LogEntryEntity::class,
        TemplateEntity::class,
        TemplateItemEntity::class,
        PlanImportEntity::class,
        DayTargetsEntity::class,
        DayShiftEntity::class,
        WeightEntryEntity::class,
        DayExportEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun dayTargets(): DayTargetsDao
    abstract fun planImports(): PlanImportDao
    abstract fun dayShifts(): DayShiftDao
    abstract fun products(): ProductDao
    abstract fun log(): LogDao
    abstract fun weights(): WeightDao
    abstract fun dishes(): DishDao
    abstract fun templates(): TemplateDao

    companion object {
        @Volatile
        private var instance: NutritionDatabase? = null

        fun get(context: Context): NutritionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, NutritionDatabase::class.java, "nutrition.db")
                    .addMigrations(Migrations.M1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
