package app.dayplan.nutrition.domain

/**
 * Штрихкоды продуктов: EAN-13, EAN-8, UPC-A и DataMatrix «Честного знака».
 * DataMatrix по стандарту GS1 начинается с идентификатора 01 и 14 цифр GTIN; из него получается EAN-13.
 */
object Barcodes {

    private const val FNC1 = '\u001D'

    /** Код в том виде, в котором его хранить и искать. null — в отсканированном нет кода товара. */
    fun normalize(raw: String): String? {
        var s = raw.trim().trimStart(FNC1)
        // префикс символики (]d2 — DataMatrix GS1, ]C1 — GS1-128, ]Q3 — QR GS1)
        if (s.length > 3 && s[0] == ']') s = s.substring(3).trimStart(FNC1)
        if (s.startsWith("01") && s.length >= 16 && s.substring(2, 16).all(Char::isDigit)) {
            return fromGtin14(s.substring(2, 16))
        }
        if (s.isEmpty() || !s.all(Char::isDigit)) return null
        return when (s.length) {
            14 -> fromGtin14(s)
            8, 12, 13 -> s
            else -> null
        }
    }

    private fun fromGtin14(gtin: String): String = if (gtin.startsWith("0")) gtin.substring(1) else gtin

    /** Равнозначные записи кода: UPC-A (12) и тот же код как EAN-13 с ведущим нулём. */
    fun variants(code: String): List<String> = buildList {
        add(code)
        if (code.length == 12) add("0$code")
        if (code.length == 13 && code.startsWith("0")) add(code.substring(1))
    }
}
