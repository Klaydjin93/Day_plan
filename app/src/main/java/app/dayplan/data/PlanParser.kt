package app.dayplan.data

import app.dayplan.model.Plan
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Разбор и проверка файла плана. Без Android-зависимостей — проверяется обычными тестами. */
object PlanParser {

    const val SCHEMA = "plan-day/1"

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Ok(val plan: Plan) : Result
        data class Error(val message: String) : Result
    }

    fun parse(text: String): Result {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        if (!trimmed.startsWith("{")) {
            return Result.Error("Это не JSON-файл плана")
        }
        val plan = try {
            json.decodeFromString<Plan>(trimmed)
        } catch (e: SerializationException) {
            return Result.Error("Файл не похож на план: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}")
        } catch (e: IllegalArgumentException) {
            return Result.Error("Файл не похож на план: ${e.message?.take(160)}")
        }
        if (plan.schema != SCHEMA) {
            return Result.Error("Неизвестная схема «${plan.schema}» — приложение понимает $SCHEMA")
        }
        if (plan.days.isEmpty()) {
            return Result.Error("В плане нет ни одного дня")
        }
        try {
            val start = LocalDate.parse(plan.week.start)
            val end = LocalDate.parse(plan.week.end)
            if (end.isBefore(start)) return Result.Error("Конец недели раньше начала")
            plan.days.forEach { LocalDate.parse(it.date) }
        } catch (e: DateTimeParseException) {
            return Result.Error("Неверная дата в плане: ${e.parsedString}")
        }
        val dup = plan.days.groupBy { it.date }.filterValues { it.size > 1 }.keys
        if (dup.isNotEmpty()) {
            return Result.Error("Дата повторяется: ${dup.joinToString()}")
        }
        return Result.Ok(plan)
    }
}
