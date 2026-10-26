package app.dayplan.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.time.LocalDate

/** Хранит копии загруженных планов в памяти приложения — виджет работает без сети. */
class PlanStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir: File
        get() = File(appContext.filesDir, "plans").apply { mkdirs() }

    sealed interface ImportResult {
        data class Ok(val stored: StoredPlan, val replaced: Boolean) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    fun importUri(uri: Uri): ImportResult {
        val text = try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes(limit = MAX_BYTES) ?: return ImportResult.Error("Файл больше 2 МБ — это не план")
                bytes.toString(Charsets.UTF_8)
            } ?: return ImportResult.Error("Не удалось открыть файл")
        } catch (e: SecurityException) {
            return ImportResult.Error("Нет доступа к файлу")
        } catch (e: java.io.IOException) {
            return ImportResult.Error("Ошибка чтения файла: ${e.message}")
        }
        return importText(text)
    }

    fun importText(text: String, now: Long = System.currentTimeMillis()): ImportResult {
        return when (val parsed = PlanParser.parse(text)) {
            is PlanParser.Result.Error -> ImportResult.Error(parsed.message)
            is PlanParser.Result.Ok -> {
                val week = parsed.plan.week
                val name = safeName("${week.id}_${week.start}") + ".json"
                val file = File(dir, name)
                val replaced = file.exists()
                file.writeText(text, Charsets.UTF_8)
                file.setLastModified(now)
                prune()
                ImportResult.Ok(StoredPlan(parsed.plan, name, now), replaced)
            }
        }
    }

    fun all(): List<StoredPlan> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { f ->
                (PlanParser.parse(f.readText(Charsets.UTF_8)) as? PlanParser.Result.Ok)
                    ?.let { StoredPlan(it.plan, f.name, f.lastModified()) }
            }
            .sortedBy { it.plan.week.start }

    fun delete(fileName: String) {
        File(dir, safeName(fileName.removeSuffix(".json")) + ".json").delete()
    }

    /** Удаляет недели, закончившиеся больше двух недель назад. */
    fun prune(today: LocalDate = AppClock.today()) {
        all().filter { LocalDate.parse(it.plan.week.end).isBefore(today.minusDays(14)) }
            .forEach { File(dir, it.fileName).delete() }
    }

    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun java.io.InputStream.readBytes(limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024
    }
}

/** Источник «сегодня»; в тестах подменяется. */
object AppClock {
    @Volatile
    var today: () -> LocalDate = { LocalDate.now() }
}
