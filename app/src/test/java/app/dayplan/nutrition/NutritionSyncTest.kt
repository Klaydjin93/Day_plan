package app.dayplan.nutrition

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dayplan.data.PlanParser
import app.dayplan.model.Plan
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionSync
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Цели недели в настоящей базе Room (в памяти, под Robolectric). */
@RunWith(RobolectricTestRunner::class)
class NutritionSyncTest {

    private lateinit var db: NutritionDatabase
    private lateinit var sync: NutritionSync

    private val text = javaClass.classLoader!!.getResource("fixture_plan_nutrition.json")!!.readText()
    private fun ok(t: String): Plan = (PlanParser.parse(t) as PlanParser.Result.Ok).plan

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NutritionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sync = NutritionSync(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun importWritesTargetsAndWeekRecord() = runBlocking {
        assertTrue(sync.syncPlan(ok(text), now = 10))
        val week = db.dayTargets().forWeek("W98")
        assertEquals(2, week.size)
        assertEquals(2300, db.dayTargets().forDate("2030-02-09")!!.kcal)
        assertNull(db.dayTargets().forDate("2030-02-08")) // день без питания
        val rec = db.planImports().get("W98")!!
        assertEquals(2250, rec.kcalAvg)
        assertEquals(10L, rec.importedAt)
    }

    @Test
    fun newerReissueReplacesWeekAndOlderIsIgnored() = runBlocking {
        sync.syncPlan(ok(text), now = 1)
        val newer = ok(
            text.replace("\"generated\": \"2030-02-03\"", "\"generated\": \"2030-02-05\"")
                .replace("\"kcal\": 2300", "\"kcal\": 2350")
                // в новой версии воскресенье без питания: его старые цели должны уйти
                .replace("\"nutrition\": {\"type\": \"длительная\", \"kcal\": 2200, \"protein_g\": 190, \"fat_g\": 65, \"carbs_g\": 214, \"notes\": []},", ""),
        )
        assertTrue(sync.syncPlan(newer, now = 2))
        assertEquals(2350, db.dayTargets().forDate("2030-02-09")!!.kcal)
        assertNull(db.dayTargets().forDate("2030-02-10"))

        assertFalse(sync.syncPlan(ok(text), now = 3)) // старая версия той же недели
        assertEquals(2350, db.dayTargets().forDate("2030-02-09")!!.kcal)
        assertEquals("2030-02-05", db.planImports().get("W98")!!.generated)
    }
}
