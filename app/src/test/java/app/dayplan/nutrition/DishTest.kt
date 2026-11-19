package app.dayplan.nutrition

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dayplan.nutrition.data.DishInput
import app.dayplan.nutrition.data.DishLine
import app.dayplan.nutrition.data.DishRepo
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionRepo
import app.dayplan.nutrition.data.ProductEntity
import app.dayplan.nutrition.data.ProductKind
import app.dayplan.nutrition.data.ProductSource
import app.dayplan.nutrition.data.YieldMethodCode
import app.dayplan.nutrition.domain.YieldMethod
import app.dayplan.nutrition.ui.DishDraft
import app.dayplan.nutrition.ui.calc
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
import java.time.LocalDate
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class DishTest {

    private lateinit var db: NutritionDatabase
    private lateinit var repo: NutritionRepo
    private lateinit var dishes: DishRepo
    private val day = LocalDate.parse("2026-11-18")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NutritionDatabase::class.java).allowMainThreadQueries().build()
        repo = NutritionRepo(db)
        dishes = DishRepo(db)
    }

    @After
    fun tearDown() = db.close()

    /** Продукты контрольного блюда из чата: значения на 100 г восстановлены из итогов по ингредиентам. */
    private suspend fun chatProducts(verifiedRice: Boolean = true): List<DishLine> {
        fun p(name: String, total: List<Double>, grams: Double, swell: Double? = null, verified: Boolean = true) = ProductEntity(
            name = name, kcal100 = total[0] * 100 / grams, protein100 = total[1] * 100 / grams, fat100 = total[2] * 100 / grams,
            carbs100 = total[3] * 100 / grams, swellK = swell, swellKMin = swell?.let { 2.5 }, swellKMax = swell?.let { 3.0 },
            source = ProductSource.MANUAL, verified = verified, updatedAt = 0,
        )
        val chicken = repo.product(repo.saveProduct(p("Грудка", listOf(877.0, 167.0, 20.0, 0.0), 797.0), emptyList()))!!
        val rice = repo.product(repo.saveProduct(p("Рис басмати", listOf(510.0, 14.0, 0.0, 113.0), 150.0, 2.8, verifiedRice), emptyList()))!!
        val carrot = repo.product(repo.saveProduct(p("Морковь", listOf(80.0, 2.0, 0.0, 19.0), 195.0), emptyList()))!!
        return listOf(DishLine(chicken, 797.0), DishLine(rice, 150.0), DishLine(carrot, 195.0))
    }

    @Test
    fun savedDishIsACardPer100OfCookedWeight() = runBlocking {
        val lines = chatProducts()
        val id = dishes.save(DishInput(0, "Грудка с рисом", lines, YieldMethod.SWELL, 1412.0, null, null, day.toString()))
        val p = repo.product(id)!!
        assertEquals(ProductKind.DISH, p.kind)
        assertEquals(ProductSource.DISH, p.source)
        assertTrue(p.verified)
        assertEquals(104, p.kcal100.roundToInt())
        assertEquals(13.0, Math.round(p.protein100 * 10) / 10.0, 0.0)
        val v = dishes.dish(id)!!
        assertEquals(YieldMethodCode.SWELL, v.dish.yieldMethod)
        assertEquals(listOf("Грудка", "Рис басмати", "Морковь"), v.lines.map { it.product.name })
        assertEquals("Грудка с рисом", repo.findProducts("рисом").single().name)
    }

    @Test
    fun unverifiedIngredientMakesDishUnverified() = runBlocking {
        val id = dishes.save(DishInput(0, "Блюдо", chatProducts(verifiedRice = false), YieldMethod.RAW_SUM, 1142.0, null, null, null))
        assertFalse(repo.product(id)!!.verified)
    }

    @Test
    fun draftCalcMatchesChatForAllThreeMethods() = runBlocking {
        val lines = chatProducts()
        val d = DishDraft(0, "Тест", lines, YieldMethod.SWELL, null, null, null, null)
        val swell = calc(d, 0.0)!!
        assertEquals(1412.0, swell.yieldGrams!!, 1e-6)
        assertEquals(104, swell.per100!!.kcal.roundToInt())
        assertEquals(102, swell.rangeKcal!!.first.roundToInt())
        assertEquals(107, swell.rangeKcal!!.second.roundToInt())

        d.method = YieldMethod.RAW_SUM
        assertEquals(1142.0, calc(d, 0.0)!!.yieldGrams!!, 1e-6)
        assertNull(calc(d, 0.0)!!.rangeKcal)

        d.method = YieldMethod.WEIGHED
        d.gross = "2312"
        assertEquals(1412.0, calc(d, 900.0)!!.yieldGrams!!, 1e-6) // с кастрюлей 900 г
        d.gross = "800"
        assertNull(calc(d, 900.0)!!.yieldGrams) // меньше веса посуды
        d.lines[0] = d.lines[0].first to "0"
        assertNull(calc(d, 900.0)) // граммы ингредиента не заданы
    }

    @Test
    fun newBatchCopiesRecipeAndLinksToFirst() = runBlocking {
        val id = dishes.save(DishInput(0, "Плов · 16.11", chatProducts(), YieldMethod.WEIGHED, 1400.0, null, null, "2026-11-16"))
        val b = DishDraft.newBatch(dishes.dish(id)!!, day)
        assertEquals("Плов · 18.11", b.name)
        assertEquals(id, b.batchOf)
        assertEquals(0L, b.productId)
        assertEquals("", b.gross)
        assertEquals(3, b.lines.size)
        assertFalse(b.hasOil())
    }

    @Test
    fun weighingLaterReappliesToLoggedEntries() = runBlocking {
        val lines = chatProducts()
        val id = dishes.save(DishInput(0, "Плов", lines, YieldMethod.SWELL, 1412.0, null, null, null))
        repo.addEntry(day, "Обед", repo.product(id)!!, 300.0)
        val before = repo.entries(day).single().kcal
        // взвесил: готового меньше, чем по оценке, — КБЖУ на 100 г растёт
        dishes.save(DishInput(id, "Плов", lines, YieldMethod.WEIGHED, 1300.0, null, null, null))
        assertEquals(before, repo.entries(day).single().kcal, 1e-9) // само не меняется
        assertEquals(1, dishes.reapply(id))
        assertEquals(1467.0 * 300 / 1300, repo.entries(day).single().kcal, 1e-6)
    }

    @Test
    fun templatesKeepProductsAndGrams() = runBlocking {
        val lines = chatProducts()
        repo.addEntry(day, "Перекус", lines[0].product, 150.0)
        repo.addEntry(day, "Перекус", lines[2].product, 50.0)
        dishes.saveTemplate("Белковая порция", repo.entries(day))
        val t = dishes.templates().single()
        assertEquals("Белковая порция", t.template.name)
        assertEquals(listOf(150.0, 50.0), t.lines.map { it.grams })
        dishes.deleteTemplate(t.template.id)
        assertTrue(dishes.templates().isEmpty())
    }

    @Test
    fun fiberIsSavedOnlyWhenKnownForEveryIngredient() = runBlocking {
        val lines = chatProducts()
        val partial = lines.mapIndexed { i, l -> if (i == 2) DishLine(l.product.copy(fiber100 = 2.8), l.grams) else l }
        assertNull(repo.product(dishes.save(DishInput(0, "Частично", partial, YieldMethod.RAW_SUM, 1142.0, null, null, null)))!!.fiber100)
        val full = lines.map { DishLine(it.product.copy(fiber100 = if (it.product.name == "Морковь") 2.8 else 0.0), it.grams) }
        val f = repo.product(dishes.save(DishInput(0, "Полностью", full, YieldMethod.RAW_SUM, 1142.0, null, null, null)))!!.fiber100!!
        assertEquals(2.8 * 195 / 1142, f, 1e-9)
    }

    @Test
    fun cookwareIsRemembered() = runBlocking {
        dishes.addCookware("Кастрюля 3 л", 900.0)
        assertEquals(900.0, dishes.cookware().single().tareG, 0.0)
    }
}
