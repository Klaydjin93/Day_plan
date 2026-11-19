package app.dayplan.nutrition.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Карточка из Open Food Facts. Пустые поля так и остаются пустыми — досчитывать их нельзя. */
data class OffProduct(
    val code: String,
    val name: String?,
    val brand: String?,
    val kcal100: Double?,
    val protein100: Double?,
    val fat100: Double?,
    val carbs100: Double?,
    val fiber100: Double?,
    /** Ккал пересчитаны из кДж (в карточке не было ккал). */
    val kcalFromKj: Boolean,
) {
    val complete: Boolean get() = name != null && kcal100 != null && protein100 != null && fat100 != null && carbs100 != null
}

sealed interface OffSearch {
    data class Found(val products: List<OffProduct>) : OffSearch
    data class Failed(val message: String) : OffSearch
}

sealed interface OffResult {
    data class Found(val product: OffProduct) : OffResult
    data object NotFound : OffResult
    data class Failed(val message: String) : OffResult
}

/**
 * Онлайн-API Open Food Facts: GET /api/v2/product/{code}. Условия OFF: свой User-Agent в каждом запросе.
 * Данные — ODbL, источник указывается в приложении.
 */
class OffClient(
    private val base: String = "https://world.openfoodfacts.org",
    private val searchBase: String = "https://search.openfoodfacts.org",
    private val userAgent: String = "DayPlan/0.2 (Android; github.com/Klaydjin93/Day_plan)",
) {
    fun fetch(code: String): OffResult {
        val url = URL("$base/api/v2/product/$code?fields=code,product_name,product_name_ru,brands,nutriments")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }
            when {
                status == 404 -> OffResult.NotFound
                status !in 200..299 || body == null -> OffResult.Failed("Open Food Facts ответил $status")
                else -> parse(code, body)
            }
        } catch (e: IOException) {
            OffResult.Failed("Нет связи с Open Food Facts")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Поиск по названию через поисковый сервис OFF (search-a-licious), только товары с пометкой страны «Россия».
     * Карточки без части КБЖУ тоже возвращаются: их дозаполняют с упаковки при сверке.
     */
    fun search(query: String, size: Int = 20): OffSearch {
        val q = java.net.URLEncoder.encode("${query.trim()} countries_tags:\"en:russia\"", "UTF-8")
        val url = URL("$searchBase/search?q=$q&page_size=$size&fields=code,product_name,product_name_ru,brands,nutriments")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            val status = conn.responseCode
            if (status !in 200..299) return OffSearch.Failed("Поиск Open Food Facts ответил $status")
            parseSearch(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } catch (e: IOException) {
            OffSearch.Failed("Нет связи с Open Food Facts")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseSearch(body: String): OffSearch {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return OffSearch.Failed("Непонятный ответ поиска Open Food Facts")
            val hits = runCatching { root["hits"]!!.jsonArray }.getOrNull() ?: return OffSearch.Found(emptyList())
            return OffSearch.Found(hits.mapNotNull { h -> runCatching { product(h.jsonObject) }.getOrNull() }.filter { it.name != null })
        }

        fun parse(code: String, body: String): OffResult {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return OffResult.Failed("Непонятный ответ Open Food Facts")
            if (root["status"]?.jsonPrimitive?.contentOrNull != "1") return OffResult.NotFound
            val p = root["product"]?.jsonObject ?: return OffResult.NotFound
            return OffResult.Found(product(p, code))
        }

        /** Карточка из объекта товара: и ответа /product, и попадания поиска. */
        private fun product(p: JsonObject, code: String? = null): OffProduct {
            val n = runCatching { p["nutriments"]?.jsonObject }.getOrNull() ?: JsonObject(emptyMap())
            fun text(key: String): String? = when (val e = p[key]) {
                is JsonArray -> e.firstOrNull()?.jsonPrimitive?.contentOrNull
                null -> null
                else -> runCatching { e.jsonPrimitive.contentOrNull }.getOrNull()
            }?.trim()?.takeIf { it.isNotEmpty() }
            fun num(key: String) = n[key].number()
            val kcal = num("energy-kcal_100g")
            val kj = num("energy-kj_100g")
            return OffProduct(
                code = code ?: text("code").orEmpty(),
                name = text("product_name_ru") ?: text("product_name"),
                brand = text("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
                kcal100 = kcal ?: kj?.let { it / 4.184 },
                protein100 = num("proteins_100g"),
                fat100 = num("fat_100g"),
                carbs100 = num("carbohydrates_100g"),
                fiber100 = num("fiber_100g"),
                kcalFromKj = kcal == null && kj != null,
            )
        }

        private fun JsonElement?.number(): Double? {
            val prim = runCatching { this?.jsonPrimitive }.getOrNull() ?: return null
            return (prim.doubleOrNull ?: prim.contentOrNull?.replace(',', '.')?.toDoubleOrNull())?.takeIf { it.isFinite() && it >= 0 }
        }
    }
}
