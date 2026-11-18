package app.dayplan.nutrition

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.dayplan.nutrition.data.Migrations
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Миграции базы на её настоящих прежних схемах (app/schemas). */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NutritionDatabase::class.java)

    @Test
    fun v1ToV2KeepsProductsEntriesAndMovesBarcodes() {
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO product (id, name, brand, search_key, barcode, kind, kcal_100, protein_100, fat_100, carbs_100, source, verified, updated_at) " +
                    "VALUES (1, 'Творог', 'Савушкин', 'творог савушкин', '4601234567890', 'brand', 121, 17, 5, 2, 'manual', 1, 100)",
            )
            execSQL(
                "INSERT INTO product (id, name, search_key, barcode, kind, kcal_100, protein_100, fat_100, carbs_100, source, verified, updated_at) " +
                    "VALUES (2, 'Тест', 'тест', NULL, 'generic', 64, 64, 64, 64, 'manual', 1, 200)",
            )
            execSQL("INSERT INTO product_portion (product_id, label, grams, is_default) VALUES (1, 'пачка', 200, 1)")
            execSQL(
                "INSERT INTO log_entry (date, meal, product_id, product_name, grams, kcal, protein, fat, carbs, product_verified, created_at) " +
                    "VALUES ('2026-11-18', 'Завтрак', 1, 'Творог', 200, 242, 34, 10, 4, 1, 300)",
            )
            execSQL("INSERT INTO weight_entry (date, kg, measured_at) VALUES ('2026-11-19', 100.9, 400)")
            close()
        }

        helper.runMigrationsAndValidate(name, 2, true, Migrations.M1_2).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), NutritionDatabase::class.java, name)
            .addMigrations(Migrations.M1_2).allowMainThreadQueries().build()
        runBlocking {
            val repo = NutritionRepo(db)
            assertEquals("Творог", db.products().byBarcode(listOf("4601234567890"))!!.name)
            assertEquals(0, db.products().barcodes(2).size)
            assertEquals(1, repo.portions(1).size)
            assertEquals(1L, repo.entries(LocalDate.parse("2026-11-18")).single().productId)
            assertEquals(100.9, repo.weightView(LocalDate.parse("2026-11-19")).today!!.kg, 0.0)
        }
        db.close()
    }
}
