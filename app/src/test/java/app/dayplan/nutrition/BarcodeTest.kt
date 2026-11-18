package app.dayplan.nutrition

import app.dayplan.nutrition.data.OffClient
import app.dayplan.nutrition.data.OffResult
import app.dayplan.nutrition.domain.Barcodes
import app.dayplan.nutrition.ui.ProductDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeTest {

    @Test
    fun plainCodes() {
        assertEquals("4601234567890", Barcodes.normalize(" 4601234567890 "))
        assertEquals("46012345", Barcodes.normalize("46012345"))
        assertEquals("012345678905", Barcodes.normalize("012345678905"))
        assertEquals("4601234567890", Barcodes.normalize("04601234567890")) // GTIN-14 с ведущим нулём
        assertNull(Barcodes.normalize("12345"))
        assertNull(Barcodes.normalize("https://example.com"))
    }

    @Test
    fun honestSignDataMatrix() {
        // GS1 DataMatrix «Честного знака»: 01 + GTIN-14, дальше 21 серийный номер, разделитель FNC1, 93 ключ
        val dm = "0104601234567890215abcDEF\u001D93Ab12"
        assertEquals("4601234567890", Barcodes.normalize(dm))
        assertEquals("4601234567890", Barcodes.normalize("]d2$dm"))
        assertEquals("4601234567890", Barcodes.normalize("\u001D$dm"))
    }

    @Test
    fun variantsCoverUpcAndEan() {
        assertEquals(listOf("012345678905", "0012345678905"), Barcodes.variants("012345678905"))
        assertEquals(listOf("0012345678905", "012345678905"), Barcodes.variants("0012345678905"))
        assertEquals(listOf("4601234567890"), Barcodes.variants("4601234567890"))
    }

    @Test
    fun offParsing() {
        val body = """{"code":"3017624010701","status":1,"status_verbose":"product found","product":{"product_name":"Nutella","product_name_ru":"Нутелла","brands":"Ferrero, Nutella","nutriments":{"energy-kcal_100g":539,"energy-kj_100g":2228,"proteins_100g":6.3,"fat_100g":30.9,"carbohydrates_100g":57.5}}}"""
        val p = (OffClient.parse("3017624010701", body) as OffResult.Found).product
        assertEquals("Нутелла", p.name)
        assertEquals("Ferrero", p.brand)
        assertEquals(539.0, p.kcal100!!, 0.0)
        assertNull(p.fiber100)
        assertTrue(p.complete)
        assertFalse(p.kcalFromKj)
        assertEquals(OffResult.NotFound, OffClient.parse("1", """{"code":"1","status":0,"status_verbose":"product not found"}"""))
        assertTrue(OffClient.parse("1", "<html>") is OffResult.Failed)
    }

    @Test
    fun offWithoutKcalUsesKjAndMarksIt() {
        val body = """{"status":1,"product":{"product_name":"Тест","nutriments":{"energy-kj_100g":418.4,"proteins_100g":"10,5","fat_100g":1}}}"""
        val p = (OffClient.parse("4600000000000", body) as OffResult.Found).product
        assertEquals(100.0, p.kcal100!!, 1e-9)
        assertTrue(p.kcalFromKj)
        assertEquals(10.5, p.protein100!!, 0.0)
        assertNull(p.carbs100)
        assertFalse(p.complete)
        // незаполненное в OFF остаётся пустым в форме — не досчитывается
        val d = ProductDraft.fromOff(p)
        assertEquals("", d.carbs)
        assertTrue("Углеводы" in d.errors())
        assertEquals(listOf("4600000000000"), d.barcodes.toList())
    }
}
