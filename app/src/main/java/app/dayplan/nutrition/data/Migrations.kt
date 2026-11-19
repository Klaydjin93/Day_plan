package app.dayplan.nutrition.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    /**
     * v1 → v2: штрихкоды уходят из product в product_barcode (у продукта может быть несколько кодов).
     * Таблица product пересобирается без столбца barcode; ссылки на неё из других таблиц сохраняются.
     */
    val M1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `product_barcode` (`barcode` TEXT NOT NULL, `product_id` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, PRIMARY KEY(`barcode`), FOREIGN KEY(`product_id`) REFERENCES `product`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_barcode_product_id` ON `product_barcode` (`product_id`)")
            db.execSQL(
                "INSERT OR IGNORE INTO product_barcode (barcode, product_id, created_at) " +
                    "SELECT TRIM(barcode), id, updated_at FROM product WHERE barcode IS NOT NULL AND TRIM(barcode) != ''",
            )
            db.execSQL("DROP INDEX IF EXISTS `index_product_barcode`")
            db.execSQL("DROP INDEX IF EXISTS `index_product_search_key`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_product` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`brand` TEXT, `search_key` TEXT NOT NULL, `kind` TEXT NOT NULL, `kcal_100` REAL NOT NULL, `protein_100` REAL NOT NULL, " +
                    "`fat_100` REAL NOT NULL, `carbs_100` REAL NOT NULL, `fiber_100` REAL, `swell_k` REAL, `swell_k_min` REAL, " +
                    "`swell_k_max` REAL, `source` TEXT NOT NULL, `external_id` TEXT, `verified` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
            )
            val cols = "`id`, `name`, `brand`, `search_key`, `kind`, `kcal_100`, `protein_100`, `fat_100`, `carbs_100`, `fiber_100`, " +
                "`swell_k`, `swell_k_min`, `swell_k_max`, `source`, `external_id`, `verified`, `updated_at`"
            db.execSQL("INSERT INTO `_new_product` ($cols) SELECT $cols FROM `product`")
            db.execSQL("DROP TABLE `product`")
            db.execSQL("ALTER TABLE `_new_product` RENAME TO `product`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_search_key` ON `product` (`search_key`)")
        }
    }
}
