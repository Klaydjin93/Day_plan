package app.dayplan.nutrition.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import java.io.File

/** Запись справочной базы: российский товар Open Food Facts (офлайн) или продукт USDA. */
data class RefProduct(
    val source: String,
    val sourceId: String,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double?,
    val kcalFromKj: Boolean,
    val swellK: Double? = null,
    val swellKMin: Double? = null,
    val swellKMax: Double? = null,
) {
    val isUsda: Boolean get() = source == ProductSource.USDA

    fun toOff() = OffProduct(barcode ?: sourceId, name, brand, kcal, protein, fat, carbs, fiber, kcalFromKj)
}

interface ReferenceSource {
    fun byBarcode(codes: List<String>): RefProduct?
    /** Каждое слово запроса должно встретиться в названии или бренде. Сначала USDA, потом OFF. */
    fun search(query: String, limit: Int = 40): List<RefProduct>
}

/**
 * Справочная база из сборки (assets/reference.db), только чтение. Строится tools/build_reference_db.py.
 * При обновлении приложения файл копируется заново; своя база пользователя при этом не трогается.
 */
class ReferenceDb(private val db: SQLiteDatabase) : ReferenceSource {

    override fun byBarcode(codes: List<String>): RefProduct? {
        if (codes.isEmpty()) return null
        val marks = codes.joinToString(",") { "?" }
        return db.rawQuery("SELECT $COLS FROM ref_product WHERE barcode IN ($marks) LIMIT 1", codes.toTypedArray())
            .use { c -> if (c.moveToFirst()) c.row() else null }
    }

    override fun search(query: String, limit: Int): List<RefProduct> {
        val words = normalizeSearch(query).split(' ').filter { it.isNotBlank() }.take(5)
        if (words.isEmpty()) return emptyList()
        val where = words.joinToString(" AND ") { "search_key LIKE ?" }
        val args = words.map { "%$it%" }.toTypedArray()
        return db.rawQuery(
            "SELECT $COLS FROM ref_product WHERE $where ORDER BY source = 'usda' DESC, length(name), name LIMIT $limit",
            args,
        ).use { c -> buildList { while (c.moveToNext()) add(c.row()) } }
    }

    fun meta(key: String): String? =
        db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun Cursor.row() = RefProduct(
        source = getString(0), sourceId = getString(1), barcode = getStringOrNull(2), name = getString(3), brand = getStringOrNull(4),
        kcal = getDouble(5), protein = getDouble(6), fat = getDouble(7), carbs = getDouble(8), fiber = getDoubleOrNull(9),
        kcalFromKj = getInt(10) == 1, swellK = getDoubleOrNull(11), swellKMin = getDoubleOrNull(12), swellKMax = getDoubleOrNull(13),
    )

    private fun Cursor.getStringOrNull(i: Int) = if (isNull(i)) null else getString(i)
    private fun Cursor.getDoubleOrNull(i: Int) = if (isNull(i)) null else getDouble(i)

    companion object {
        private const val ASSET = "reference.db"
        private const val COLS =
            "source, source_id, barcode, name, brand, kcal, protein, fat, carbs, fiber, kcal_from_kj, swell_k, swell_k_min, swell_k_max"

        /** Схема — та же, что пишет tools/build_reference_db.py; для тестов. */
        const val SCHEMA = """
            CREATE TABLE ref_product (id INTEGER PRIMARY KEY, source TEXT NOT NULL, source_id TEXT NOT NULL, barcode TEXT,
              name TEXT NOT NULL, brand TEXT, search_key TEXT NOT NULL, kcal REAL NOT NULL, protein REAL NOT NULL, fat REAL NOT NULL,
              carbs REAL NOT NULL, fiber REAL, kcal_from_kj INTEGER NOT NULL DEFAULT 0, swell_k REAL, swell_k_min REAL, swell_k_max REAL)
        """

        @Volatile
        private var instance: ReferenceDb? = null

        /** null — в сборке нет справочной базы (например, она ещё не собрана). */
        fun get(context: Context): ReferenceDb? = instance ?: synchronized(this) {
            instance ?: open(context.applicationContext)?.also { instance = it }
        }

        private fun open(context: Context): ReferenceDb? {
            val file = File(context.noBackupFilesDir, ASSET)
            val prefs = context.getSharedPreferences("reference_db", Context.MODE_PRIVATE)
            val version = appVersion(context)
            if (!file.exists() || prefs.getLong("copied_for", -1) != version) {
                val copied = runCatching {
                    context.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
                }.isSuccess
                if (!copied) return null
                prefs.edit().putLong("copied_for", version).apply()
            }
            return runCatching { ReferenceDb(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)) }.getOrNull()
        }

        private fun appVersion(context: Context): Long {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        }
    }
}
