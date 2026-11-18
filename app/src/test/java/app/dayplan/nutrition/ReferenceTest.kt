package app.dayplan.nutrition

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dayplan.nutrition.data.BarcodeLookup
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionRepo
import app.dayplan.nutrition.data.OffClient
import app.dayplan.nutrition.data.OffResult
import app.dayplan.nutrition.data.OffSearch
import app.dayplan.nutrition.data.ProductSource
import app.dayplan.nutrition.data.ReferenceDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Справочная база (OFF офлайн + USDA) и её связь со своей базой. */
@RunWith(RobolectricTestRunner::class)
class ReferenceTest {

    private lateinit var refSql: SQLiteDatabase
    private lateinit var ref: ReferenceDb
    private lateinit var db: NutritionDatabase
    private lateinit var repo: NutritionRepo

    @Before
    fun setUp() {
        refSql = SQLiteDatabase.create(null)
        refSql.execSQL(ReferenceDb.SCHEMA)
        fun add(source: String, id: String, code: String?, name: String, brand: String?, kcal: Double, swell: Double? = null) =
            refSql.execSQL(
                "INSERT INTO ref_product (source, source_id, barcode, name, brand, search_key, kcal, protein, fat, carbs, swell_k) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(source, id, code, name, brand, listOfNotNull(name, brand).joinToString(" ").lowercase().replace('ё', 'е'), kcal, 16.0, 5.0, 3.0, swell),
            )
        add("off", "4607052684873", "4607052684873", "Творог 5%", "Савушкин", 121.0)
        add("off", "4690228034428", "4690228034428", "Творог обезжиренный", "Простоквашино", 55.3)
        add("off", "4600000000099", "4600000000099", "Йогурт", null, 66.0)
        add("usda", "168877", null, "Рис белый длиннозёрный, сухой", null, 365.0, swell = 2.8)
        ref = ReferenceDb(refSql)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NutritionDatabase::class.java).allowMainThreadQueries().build()
        repo = NutritionRepo(db) { ref }
    }

    @After
    fun tearDown() {
        db.close()
        refSql.close()
    }

    @Test
    fun searchNeedsEveryWordAndPutsUsdaFirst() {
        assertEquals(2, ref.search("ТВОРОГ").size)
        assertEquals(listOf("Творог обезжиренный"), ref.search("творог простокв").map { it.name })
        assertEquals("Рис белый длиннозёрный, сухой", ref.search("рис длиннозерный").single().name)
        assertTrue(ref.search("   ").isEmpty())
    }

    @Test
    fun barcodeGoesOwnThenOfflineThenOnline() = runBlocking {
        val offline = repo.lookupBarcode("4607052684873") { error("онлайн не нужен — нашлось офлайн") }
        val p = (offline as BarcodeLookup.Off).product
        assertEquals("Творог 5%", p.name)
        assertEquals("4607052684873", p.code)

        var askedOnline = false
        repo.lookupBarcode("4600000000777") { askedOnline = true; OffResult.NotFound }
        assertTrue(askedOnline)
    }

    @Test
    fun referenceHidesWhatIsAlreadyOwned() = runBlocking {
        assertEquals(2, repo.findReference("творог").size)
        repo.saveProduct(
            app.dayplan.nutrition.data.ProductEntity(name = "Мой творог", kcal100 = 121.0, protein100 = 16.0, fat100 = 5.0, carbs100 = 3.0, source = ProductSource.OFF, verified = true, updatedAt = 0),
            emptyList(), listOf("4607052684873"),
        )
        assertEquals(listOf("Творог обезжиренный"), repo.findReference("творог").map { it.name })
    }

    @Test
    fun usdaIsAdoptedOnceWithSwell() = runBlocking {
        val r = repo.findReference("рис").single()
        val a = repo.adoptUsda(r)
        assertEquals(ProductSource.USDA, a.source)
        assertTrue(a.verified)
        assertEquals(2.8, a.swellK!!, 0.0)
        assertEquals(a.id, repo.adoptUsda(r).id)
        assertTrue(repo.findReference("рис").isEmpty())
        assertEquals("Рис белый длиннозёрный, сухой", repo.findProducts("рис").single().name)
    }

    @Test
    fun onlineSearchParsing() {
        val body = """{"hits":[
          {"code":"4607052684873","product_name":"Творог","brands":["Савушкин","x"],"nutriments":{"energy-kcal_100g":121,"proteins_100g":16,"fat_100g":5,"carbohydrates_100g":3}},
          {"code":"4610001997115","product_name":"творог","brands":["творог"],"nutriments":{}},
          {"code":"1","brands":"без названия"}
        ],"count":3}"""
        val found = (OffClient.parseSearch(body) as OffSearch.Found).products
        assertEquals(2, found.size)
        assertEquals("Савушкин", found[0].brand)
        assertTrue(found[0].complete)
        assertNull(found[1].kcal100)
        assertTrue(OffClient.parseSearch("oops") is OffSearch.Failed)
    }
}
