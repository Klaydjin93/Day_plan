package app.dayplan.nutrition

import app.dayplan.data.PlanParser
import app.dayplan.model.Plan
import app.dayplan.nutrition.data.PlanImportEntity
import app.dayplan.nutrition.data.TargetsImport
import app.dayplan.nutrition.domain.DishMath
import app.dayplan.nutrition.domain.GoalMode
import app.dayplan.nutrition.domain.GoalsMapper
import app.dayplan.nutrition.domain.Ingredient
import app.dayplan.nutrition.domain.Nutrients
import app.dayplan.nutrition.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class NutritionLogicTest {

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun ok(text: String): Plan = (PlanParser.parse(text) as PlanParser.Result.Ok).plan

    private val nutritionPlan = ok(resource("fixture_plan_nutrition.json"))
    private val oldPlan = ok(resource("fixture_plan.json"))

    @Test
    fun parsesNewFields() {
        assertEquals(2250, nutritionPlan.week.nutrition!!.kcalAvg)
        val sat = nutritionPlan.days[0].nutrition!!
        assertEquals("2030-02-10", sat.fuelFor)
        assertEquals("min", sat.targets!!.proteinG!!.mode)
        assertEquals(75.0, sat.targets!!.fatG!!.max!!, 0.0)
        assertEquals("weight", nutritionPlan.days[0].report[0].auto)
        assertNull(nutritionPlan.days[0].report[2].auto)
    }

    @Test
    fun oldPlanStillParsesWithoutNewFields() {
        assertNull(oldPlan.week.nutrition)
        assertNull(oldPlan.days[0].nutrition!!.targets)
        assertNull(oldPlan.days[0].report[0].auto)
    }

    @Test
    fun goalsFromPlanTargets() {
        val g = GoalsMapper.from(nutritionPlan.days[0].nutrition!!)
        assertTrue(g.fromPlan)
        assertEquals(GoalMode.MIN, g.protein.mode)
        assertEquals(GoalMode.MIN, g.carbs.mode)
        assertEquals(GoalMode.TARGET, g.kcal.mode)
        assertEquals(5.0, g.kcal.tolPct!!, 0.0)
    }

    @Test
    fun goalsWithoutTargetsAreUnspecifiedPlainTargets() {
        val g = GoalsMapper.from(oldPlan.days[0].nutrition!!)
        assertFalse(g.fromPlan)
        assertEquals(GoalMode.TARGET, g.fat.mode)
        assertNull(g.fat.tolPct)
        assertNull(g.fat.max)
    }

    @Test
    fun zones() {
        val g = GoalsMapper.from(nutritionPlan.days[0].nutrition!!)
        // белок — минимум 190
        assertEquals(Zone.BELOW, g.protein.zone(189.0))
        assertEquals(Zone.OK, g.protein.zone(230.0))
        // ккал 2300 ±5 %: коридор 2185–2415
        assertEquals(Zone.BELOW, g.kcal.zone(2184.0))
        assertEquals(Zone.OK, g.kcal.zone(2185.0))
        assertEquals(Zone.OK, g.kcal.zone(2415.0))
        assertEquals(Zone.OVER, g.kcal.zone(2416.0))
        // жиры 60 ±5 % (57–63), потолок 75
        assertEquals(Zone.OK, g.fat.zone(62.0))
        assertEquals(Zone.WARN, g.fat.zone(70.0))
        assertEquals(Zone.WARN, g.fat.zone(75.0))
        assertEquals(Zone.OVER, g.fat.zone(75.5))
    }

    @Test
    fun importTakesOnlyDaysWithNutrition() {
        val rows = TargetsImport.rows(nutritionPlan)
        assertEquals(listOf("2030-02-09", "2030-02-10"), rows.map { it.date })
        val sat = rows[0]
        assertEquals("W98", sat.weekId)
        assertEquals("2030-02-09", sat.originDate)
        assertEquals("2030-02-10", sat.fuelFor)
        assertEquals("min", TargetsImport.decodeTargets(sat.targetsJson)!!.carbsG!!.mode)
        assertNull(rows[1].targetsJson)
    }

    @Test
    fun olderReissueDoesNotOverwrite() {
        fun stored(generated: String?) = PlanImportEntity("W98", "2030-02-04", "v1", generated, null, 0)
        assertTrue(TargetsImport.shouldApply(null, nutritionPlan))
        assertTrue(TargetsImport.shouldApply(stored("2030-02-03"), nutritionPlan)) // та же версия — перезапись
        assertTrue(TargetsImport.shouldApply(stored("2030-02-01"), nutritionPlan))
        assertFalse(TargetsImport.shouldApply(stored("2030-02-05"), nutritionPlan))
        assertTrue(TargetsImport.shouldApply(stored(null), nutritionPlan))
    }

    /**
     * Контрольное блюдо из чата: грудка 797 г, рис 150 г сухого (k = 2,8), морковь 195 г.
     * Значения на 100 г восстановлены из итогов по каждому ингредиенту: 877/167/20/0, 510/14/0/113, 80/2/0/19.
     */
    @Test
    fun dishFromChatMatches() {
        fun per100(total: Nutrients, grams: Double) = total.scaled(100.0 / grams)
        val chicken = Ingredient(per100(Nutrients(877.0, 167.0, 20.0, 0.0), 797.0), 797.0)
        val rice = Ingredient(per100(Nutrients(510.0, 14.0, 0.0, 113.0), 150.0), 150.0, swellK = 2.8, swellKMin = 2.5, swellKMax = 3.0)
        val carrot = Ingredient(per100(Nutrients(80.0, 2.0, 0.0, 19.0), 195.0), 195.0)
        val all = listOf(chicken, rice, carrot)

        val totals = DishMath.totals(all)
        assertEquals(1467.0, totals.kcal, 1e-6)
        assertEquals(183.0, totals.protein, 1e-6)

        val yieldG = DishMath.swellYield(all)
        assertEquals(1412.0, yieldG, 1e-6)

        val p = DishMath.per100(totals, yieldG)
        assertEquals(104, p.kcal.roundToInt())
        assertEquals(13.0, round1(p.protein), 0.0)
        assertEquals(1.4, round1(p.fat), 0.0)
        assertEquals(9.3, round1(p.carbs), 0.0)

        val (lo, hi) = DishMath.swellYieldRange(all)
        assertEquals(1367.0, lo, 1e-6) // 1142 + 150 × 1,5
        assertEquals(1442.0, hi, 1e-6) // 1142 + 150 × 2,0
        // в чате диапазон назван 102–107 ккал на 100 г
        assertEquals(102, DishMath.per100(totals, hi).kcal.roundToInt())
        assertEquals(107, DishMath.per100(totals, lo).kcal.roundToInt())

        assertEquals(1142.0, DishMath.rawSum(all), 1e-6)
    }

    @Test
    fun weighedYieldSubtractsTare() {
        assertEquals(1412.0, DishMath.weighedYield(2312.0, 900.0), 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun weighedYieldRejectsGrossBelowTare() {
        DishMath.weighedYield(800.0, 900.0)
    }

    private fun round1(x: Double) = Math.round(x * 10) / 10.0
}
