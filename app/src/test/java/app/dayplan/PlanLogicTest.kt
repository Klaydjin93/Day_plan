package app.dayplan

import app.dayplan.data.DayLookup
import app.dayplan.data.PlanParser
import app.dayplan.data.StoredPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanLogicTest {

    private val fixture = javaClass.classLoader!!.getResource("fixture_plan.json")!!.readText()

    private fun ok(text: String) = (PlanParser.parse(text) as PlanParser.Result.Ok).plan

    @Test
    fun parsesFixtureAndIgnoresUnknownFields() {
        val plan = ok(fixture)
        assertEquals("W99", plan.week.id)
        assertEquals(2, plan.days.size)
        val tue = plan.days[0]
        assertEquals(2000, tue.nutrition!!.kcal)
        assertEquals("после зала", tue.report[1].whenText)
        assertNull(tue.report[2].whenText)
        assertEquals(5, tue.sessions[0].table!!.head.size)
    }

    @Test
    fun rejectsWrongSchema() {
        val r = PlanParser.parse(fixture.replace("plan-day/1", "plan-day/2"))
        assertTrue(r is PlanParser.Result.Error)
        assertTrue((r as PlanParser.Result.Error).message.contains("plan-day/1"))
    }

    @Test
    fun rejectsNonJsonAndBrokenJson() {
        assertTrue(PlanParser.parse("<!DOCTYPE html><html>") is PlanParser.Result.Error)
        assertTrue(PlanParser.parse("{\"schema\": \"plan-day/1\"}") is PlanParser.Result.Error)
    }

    @Test
    fun rejectsBadDateAndDuplicates() {
        assertTrue(PlanParser.parse(fixture.replace("2030-01-09", "2030-13-09")) is PlanParser.Result.Error)
        assertTrue(PlanParser.parse(fixture.replace("2030-01-09", "2030-01-08")) is PlanParser.Result.Error)
    }

    @Test
    fun acceptsBomAtStart() {
        assertTrue(PlanParser.parse("﻿" + fixture) is PlanParser.Result.Ok)
    }

    @Test
    fun lookupFindsDayAndPrefersNewerReissue() {
        val v1 = StoredPlan(ok(fixture), "a.json", importedAt = 1)
        val v2plan = ok(fixture.replace("\"generated\": \"2030-01-06\"", "\"generated\": \"2030-01-08\"").replace("\"kcal\": 2000", "\"kcal\": 2100"))
        val v2 = StoredPlan(v2plan, "b.json", importedAt = 0)
        val found = DayLookup.find(listOf(v1, v2), LocalDate.parse("2030-01-08"))!!
        assertEquals(2100, found.day.nutrition!!.kcal)
        assertNull(DayLookup.find(listOf(v1), LocalDate.parse("2030-01-10")))
    }
}
