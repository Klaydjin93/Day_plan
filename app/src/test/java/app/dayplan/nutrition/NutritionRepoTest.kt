package app.dayplan.nutrition

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionRepo
import app.dayplan.nutrition.data.ProductEntity
import app.dayplan.nutrition.data.ProductPortionEntity
import app.dayplan.nutrition.data.ProductSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class NutritionRepoTest {

    private lateinit var db: NutritionDatabase
    private lateinit var repo: NutritionRepo
    private val day = LocalDate.parse("2030-02-09")

    private fun product(name: String, brand: String? = null, kcal: Double = 100.0) = ProductEntity(
        name = name, brand = brand, kcal100 = kcal, protein100 = 20.0, fat100 = 2.0, carbs100 = 3.0,
        source = ProductSource.MANUAL, verified = true, updatedAt = 0,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NutritionDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = NutritionRepo(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun searchIgnoresCaseOfCyrillicAndYo() = runBlocking {
        repo.saveProduct(product("Творог 5%", "Савушкин"), emptyList())
        repo.saveProduct(product("Филе индейки"), emptyList())
        repo.saveProduct(product("Свёкла"), emptyList())
        assertEquals(listOf("Творог 5%"), repo.findProducts("ТВОРОГ").map { it.name })
        assertEquals(listOf("Творог 5%"), repo.findProducts("савуш").map { it.name })
        assertEquals(listOf("Свёкла"), repo.findProducts("свекла").map { it.name })
        assertEquals(3, repo.findProducts("").size)
    }

    @Test
    fun entrySnapshotSurvivesProductEdit() = runBlocking {
        val id = repo.saveProduct(product("Индейка", kcal = 130.0), emptyList())
        val p = repo.product(id)!!
        repo.addEntry(day, "Обед", p, 200.0, now = 1)
        val e = repo.entries(day).single()
        assertEquals(260.0, e.kcal, 1e-9)
        assertEquals(40.0, e.protein, 1e-9)
        // правка карточки не трогает прошлую запись
        repo.saveProduct(p.copy(kcal100 = 150.0), emptyList())
        assertEquals(260.0, repo.entries(day).single().kcal, 1e-9)
        // а смена веса записи пересчитывает от актуальной карточки
        repo.updateEntryGrams(e, 100.0)
        assertEquals(150.0, repo.entries(day).single().kcal, 1e-9)
    }

    @Test
    fun portionsKeepOneDefault() = runBlocking {
        val id = repo.saveProduct(
            product("Яйцо"),
            listOf(ProductPortionEntity(productId = 0, label = "1 яйцо", grams = 55.0), ProductPortionEntity(productId = 0, label = "2 яйца", grams = 110.0)),
        )
        val portions = repo.portions(id)
        assertEquals(2, portions.size)
        assertEquals(1, portions.count { it.isDefault })
        assertEquals("1 яйцо", portions.first().label)
    }

    @Test
    fun recentComesFirstInEmptySearch() = runBlocking {
        val a = repo.saveProduct(product("Аааа"), emptyList())
        repo.saveProduct(product("Бббб"), emptyList(), now = 5)
        repo.addEntry(day, "Ужин", repo.product(a)!!, 50.0, now = 10)
        assertEquals("Аааа", repo.findProducts("").first().name)
    }

    @Test
    fun weightRollingAndPlanWeek() = runBlocking {
        repo.saveWeight(LocalDate.parse("2030-02-05"), 82.0)
        repo.saveWeight(LocalDate.parse("2030-02-07"), 82.6)
        repo.saveWeight(day, 82.2)
        val v = repo.weightView(day)
        assertEquals(3, v.rolling.count)
        assertEquals(82.2666, v.rolling.kg!!, 1e-3)
        assertNull(v.week) // недель плана нет
        repo.deleteWeight(day)
        assertNull(repo.weightView(day).today)
        assertTrue(repo.weightView(day).rolling.thin)
    }

    @Test
    fun barcodeLookupOwnThenOffThenNotFound() = runBlocking {
        val id = repo.saveProduct(product("Творог"), emptyList(), listOf("012345678905"))
        // свой код находится и в записи EAN-13 с ведущим нулём
        val own = repo.lookupBarcode("0012345678905") { error("в OFF ходить не нужно") }
        assertEquals(id, (own as app.dayplan.nutrition.data.BarcodeLookup.Own).product.id)

        val asked = mutableListOf<String>()
        val nf = repo.lookupBarcode("4600000000001") { asked += it; app.dayplan.nutrition.data.OffResult.NotFound }
        assertTrue(nf is app.dayplan.nutrition.data.BarcodeLookup.NotFound)
        assertEquals(listOf("4600000000001"), asked)

        val offline = repo.lookupBarcode("4600000000002") { app.dayplan.nutrition.data.OffResult.Failed("Нет связи") }
        assertEquals("Нет связи", (offline as app.dayplan.nutrition.data.BarcodeLookup.NotFound).offError)
    }

    @Test
    fun linkingMovesCodeAndKeepsOthers() = runBlocking {
        val a = repo.saveProduct(product("Творог в пачке"), emptyList(), listOf("4600000000010"))
        val b = repo.saveProduct(product("Творог развесной"), emptyList())
        repo.linkBarcode("4600000000020", a)
        assertEquals(listOf("4600000000010", "4600000000020"), repo.barcodes(a).sorted())
        // код, привязанный к другой карточке, переходит к новой
        assertEquals("Творог в пачке", repo.barcodeOwner("4600000000020", exceptId = b)!!.name)
        repo.linkBarcode("4600000000020", b)
        assertEquals(listOf("4600000000010"), repo.barcodes(a))
        assertEquals(listOf("4600000000020"), repo.barcodes(b))
        assertNull(repo.barcodeOwner("4600000000020", exceptId = b))
    }
}
