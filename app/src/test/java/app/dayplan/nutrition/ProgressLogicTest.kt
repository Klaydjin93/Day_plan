package app.dayplan.nutrition

import app.dayplan.nutrition.domain.Goal
import app.dayplan.nutrition.domain.GoalMode
import app.dayplan.nutrition.domain.WeightPoint
import app.dayplan.nutrition.domain.Weights
import app.dayplan.nutrition.domain.Zone
import app.dayplan.nutrition.domain.bar
import app.dayplan.nutrition.domain.label
import app.dayplan.nutrition.domain.parseDecimal
import app.dayplan.nutrition.domain.remainder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressLogicTest {

    private val protein = Goal(190.0, GoalMode.MIN)
    private val kcal = Goal(2200.0, GoalMode.TARGET, tolPct = 5.0)
    private val fat = Goal(65.0, GoalMode.TARGET, tolPct = 5.0, max = 75.0)
    private val plain = Goal(214.0, GoalMode.TARGET, specified = false)

    @Test
    fun barScaleEndsAtTheHardEdge() {
        // минимум у правого края
        assertEquals(0.5f, protein.bar(95.0).fill, 1e-4f)
        assertEquals(1f, protein.bar(250.0).fill, 0f)
        assertNull(protein.bar(0.0).corridorStart)
        // цель с коридором: шкала до верха коридора 2310, метка цели на 2200/2310
        val k = kcal.bar(2200.0)
        assertEquals(2200f / 2310f, k.marker!!, 1e-4f)
        assertEquals(2090f / 2310f, k.corridorStart!!, 1e-4f)
        assertEquals(1f, k.corridorEnd!!, 1e-4f)
        // цель с потолком: шкала до 75
        val f = fat.bar(70.0)
        assertEquals(70f / 75f, f.fill, 1e-4f)
        assertEquals(65f / 75f, f.marker!!, 1e-4f)
        assertEquals(Zone.WARN, f.zone)
        // простая цель: шкала до неё самой
        assertEquals(1f, plain.bar(300.0).fill, 0f)
        assertEquals(Zone.OVER, plain.bar(300.0).zone)
    }

    @Test
    fun remainderTexts() {
        assertEquals("до минимума 62 г", protein.remainder(128.0, "г"))
        assertEquals("минимум набран, +10 г", protein.remainder(200.0, "г"))
        assertEquals("осталось 640 ккал", kcal.remainder(1560.0, "ккал"))
        assertEquals("в коридоре", kcal.remainder(2250.0, "ккал"))
        assertEquals("сверх цели +90 ккал", kcal.remainder(2400.0, "ккал"))
        assertEquals("выше коридора, до потолка 5 г", fat.remainder(70.0, "г"))
        assertEquals("сверх потолка +3 г", fat.remainder(78.0, "г"))
        assertEquals("цель достигнута", plain.remainder(214.0, "г"))
    }

    @Test
    fun labels() {
        assertEquals("≥ 190", protein.label())
        assertEquals("2200 ± 5 %", kcal.label())
        assertEquals("65 ± 5 % (≤ 75)", fat.label())
        assertEquals("214", plain.label())
    }

    @Test
    fun weightAverages() {
        val d = LocalDate.parse("2026-11-22")
        val pts = listOf(
            WeightPoint(d.minusDays(9), 90.0), // вне 7 дней
            WeightPoint(d.minusDays(6), 82.0),
            WeightPoint(d.minusDays(3), 82.4),
            WeightPoint(d, 82.2),
        )
        val r = Weights.rolling7(pts, d)
        assertEquals(3, r.count)
        assertEquals(82.2, r.kg!!, 1e-9)
        assertFalse(r.thin)
        val w = Weights.week(pts, LocalDate.parse("2026-11-16"))
        assertEquals(LocalDate.parse("2026-11-22"), w.to)
        assertEquals(3, w.count)
        val empty = Weights.rolling7(emptyList(), d)
        assertNull(empty.kg)
        assertTrue(empty.thin)
    }

    @Test
    fun decimalsFromPhoneKeyboard() {
        assertEquals(82.4, parseDecimal("82,4")!!, 0.0)
        assertEquals(1000.0, parseDecimal(" 1 000 ")!!, 0.0)
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal(""))
    }
}
