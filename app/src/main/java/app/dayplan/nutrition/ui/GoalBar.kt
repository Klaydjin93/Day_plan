package app.dayplan.nutrition.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dayplan.nutrition.domain.Goal
import app.dayplan.nutrition.domain.Zone
import app.dayplan.nutrition.domain.bar
import app.dayplan.nutrition.domain.label
import app.dayplan.nutrition.domain.remainder
import app.dayplan.ui.Fonts
import app.dayplan.ui.Palette
import kotlin.math.roundToInt

fun zoneColor(zone: Zone): Color = when (zone) {
    Zone.BELOW -> Palette.Faint
    Zone.OK -> Palette.Green
    Zone.WARN -> Palette.Hema
    Zone.OVER -> Palette.Thr
}

/**
 * Полоса цели: заполнение до факта, цвет — зона, белая метка — цель, пунктир — коридор.
 * Подписи: слева название, справа «факт / цель», снизу остаток словами.
 */
@Composable
fun GoalBar(title: String, goal: Goal, actual: Double, unit: String, modifier: Modifier = Modifier) {
    val g = goal.bar(actual)
    // без смысла из плана оценку не ставим: полоса нейтральная, без зелёного и красного
    val color = if (goal.specified) zoneColor(g.zone) else Palette.Muted
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.weight(1f))
            Text(
                "${actual.roundToInt()} / ${goal.label()} $unit",
                fontFamily = Fonts.Mono, fontSize = 12.sp, color = Palette.Muted,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(18.dp).padding(vertical = 3.dp)) {
            val h = size.height
            val r = CornerRadius(h / 2, h / 2)
            drawRoundRect(Palette.Surface2, size = size, cornerRadius = r)
            if (g.fill > 0f) {
                drawRoundRect(color, size = Size(size.width * g.fill, h), cornerRadius = r)
            }
            val cs = g.corridorStart
            val ce = g.corridorEnd
            if (cs != null && ce != null) {
                drawRoundRect(
                    Palette.Green,
                    topLeft = Offset(size.width * cs, -2.dp.toPx()),
                    size = Size(size.width * (ce - cs), h + 4.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            g.marker?.let { m ->
                val x = (size.width * m).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
                drawLine(Palette.Text, Offset(x, -3.dp.toPx()), Offset(x, h + 3.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            }
        }
        Text(
            goal.remainder(actual, unit),
            fontSize = 12.sp,
            color = if (goal.specified && (g.zone == Zone.WARN || g.zone == Zone.OVER)) color else Palette.Muted,
        )
    }
}
