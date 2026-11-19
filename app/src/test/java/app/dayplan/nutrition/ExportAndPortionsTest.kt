package app.dayplan.nutrition

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dayplan.data.PlanParser
import app.dayplan.nutrition.data.DishInput
import app.dayplan.nutrition.data.DishLine
import app.dayplan.nutrition.data.DishRepo
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionRepo
import app.dayplan.nutrition.data.NutritionSync
import app.dayplan.nutrition.data.ProductEntity
import app.dayplan.nutrition.data.ProductSource
import app.dayplan.nutrition.domain.DayExport
import app.dayplan.nutrition.domain.PortionOption
import app.dayplan.nutrition.domain.Portions
import app.dayplan.nutrition.domain.YieldMethod
import app.dayplan.nutrition.ui.DishDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Ввод порциями и итог дня для чата. */
@RunWith(RobolectricTestRunner::class)
class ExportAndPortionsTest {

    private lateinit var db: NutritionDatabase
    private lateinit var repo: NutritionRepo
    private val day = LocalDate.parse("2030-02-09") // СБ из тестового плана W98: «отдых · заправка»

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NutritionDatabase::class.java).allowMainThreadQueries().build()
        repo = NutritionRepo(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun product(name: String, kcal: Double, p: Double, f: Double, c: Double, verified: Boolean = true) =
        repo.product(repo.saveProduct(ProductEntity(name = name, kcal100 = kcal, protein100 = p, fat100 = f, carbs100 = c, source = ProductSource.MANUAL, verified = verified, updatedAt = 0), emptyList()))!!

    @Test
    fun portionsAreRecognisedInGrams() {
        val box = PortionOption("контейнер", 350.0, isDefault = true)
        val egg = PortionOption("яйцо", 55.0)
        val whole = PortionOption(Portions.WHOLE_DISH, 1990.0, wholeDish = true)
        val all = listOf(egg, box, whole)
        assertEquals(box to 1.5, Portions.match(525.0, all))      // сначала порция по умолчанию
        assertEquals(egg to 2.0, Portions.match(110.0, all))
        assertEquals(whole to 0.25, Portions.match(497.5, all))   // ¼ блюда
        assertNull(Portions.match(333.0, all))                    // не ложится ни в одну — остаются граммы
        assertEquals(0.25, whole.step, 0.0)
        assertEquals(0.5, box.step, 0.0)
        assertEquals("¼", Portions.countLabel(0.25))
        assertEquals("1,5", Portions.countLabel(1.5))
    }

    @Test
    fun dishKeepsItsPortionsAcrossEditsAndBatches() = runBlocking {
        val dishes = DishRepo(db)
        val rice = product("Рис", 340.0, 9.3, 0.0, 75.3)
        val id = dishes.save(DishInput(0, "Плов", listOf(DishLine(rice, 300.0)), YieldMethod.WEIGHED, 900.0, null, null, null, listOf("контейнер" to 350.0)))
        val v = dishes.dish(id)!!
        assertEquals(listOf("контейнер" to 350.0), v.portions.map { it.label to it.grams })
        val edit = DishDraft.edit(v, null)
        assertEquals(listOf("контейнер" to "350"), edit.portions.toList())
        assertEquals(listOf("контейнер" to "350"), DishDraft.newBatch(v, LocalDate.parse("2030-02-10")).portions.toList())
        dishes.save(DishInput(id, "Плов", listOf(DishLine(rice, 300.0)), YieldMethod.WEIGHED, 880.0, null, null, null)) // portions = null — не трогать
        assertEquals(1, dishes.dish(id)!!.portions.size)
        edit.portions.add("" to "")
        assertEquals(listOf("контейнер" to 350.0), edit.parsedPortions()) // пустая строка пропускается
        edit.portions.add("тарелка" to "0")
        assertNull(edit.parsedPortions())
    }

    @Test
    fun exportTextFollowsAgreedFormat() = runBlocking {
        val text = javaClass.classLoader!!.getResource("fixture_plan_nutrition.json")!!.readText()
        NutritionSync(db).syncPlan((PlanParser.parse(text) as PlanParser.Result.Ok).plan, now = 1)
        val tvorog = product("Творог 5%", 121.0, 17.0, 5.0, 1.8)
        val bar = product("Батончик", 400.0, 10.0, 20.0, 45.0, verified = false)
        repo.addEntry(day, "Завтрак", tvorog, 200.0)
        repo.addEntry(day, "Перекус", bar, 50.0)
        repo.saveWeight(day.minusDays(1), 100.8)
        repo.saveWeight(day, 100.4)

        val x = repo.exportInput(day, "0.1.0")
        assertEquals(
            """
            Питание · СБ 09.02 · отдых · заправка · план W98 v1
            Факт: 442 ккал · Б 39 г · Ж 20 г · У 26 г
            План: 2300 ккал ±5% · Б ≥190 г · Ж 60 г ±5% (≤75) · У ≥250 г
            Разница: −1858 ккал · Б −151 · Ж −40 · У −224
            Приёмы: завтрак 242 · обед 0 · ужин 0 · перекус 200 · после тренировки 0 (ккал)
            Непроверенные продукты: 1 запись, 200 ккал
            Вес: 100,4 кг · среднее 7 дн: 100,6 кг, из 2 замеров
            Перенос целей: нет
            Источник: План дня 0.1.0 (параллель с Lifesum)
            """.trimIndent(),
            DayExport.text(x, withProducts = false),
        )
        val full = DayExport.text(x, withProducts = true)
        assertEquals(
            listOf(
                "Продукты:",
                "завтрак · Творог 5% · 200 г · 242 ккал · Б 34,0 · Ж 10,0 · У 3,6",
                "перекус · Батончик · 50 г · 200 ккал · Б 5,0 · Ж 10,0 · У 22,5 · не проверено",
            ),
            full.lines().takeLast(3),
        )
    }

    @Test
    fun exportWithoutTargetsOrWeight() = runBlocking {
        val x = repo.exportInput(LocalDate.parse("2030-03-04"), "0.1.0")
        val t = DayExport.text(x, withProducts = true)
        assertEquals("Питание · ПН 04.03", t.lines()[0])
        assertEquals("План: целей на этот день нет", t.lines()[2])
        assertEquals(false, t.contains("Разница") || t.contains("Вес:"))
        assertEquals("Непроверенные продукты: 0 записей, 0 ккал", t.lines().first { it.startsWith("Непроверенные") })
        assertEquals("Продукты: записей нет", t.lines().last())
    }

    @Test
    fun russianPlurals() {
        assertEquals(listOf("запись", "записи", "записей", "записей", "запись", "записей"),
            listOf(1, 3, 5, 11, 21, 112).map { DayExport.plural(it, "запись", "записи", "записей") })
    }
}
