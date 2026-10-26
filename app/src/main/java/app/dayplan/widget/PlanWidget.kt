package app.dayplan.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.dayplan.data.AppClock
import app.dayplan.data.CheckState
import app.dayplan.data.DaySnapshot
import app.dayplan.model.Day
import app.dayplan.model.Session
import app.dayplan.ui.MainActivity
import app.dayplan.ui.Palette

class PlanWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // «Тик» меняется при импорте, галочке и смене дня — по нему перечитываем данные.
            val tick = currentState<Preferences>()[WidgetRefresher.TICK] ?: 0L
            val ctx = LocalContext.current
            val today = AppClock.today()
            val snapshot = remember(tick, today) { DaySnapshot.load(ctx, today) }
            WidgetContent(snapshot)
        }
    }
}

private val condensed = FontFamily("sans-serif-condensed")
private fun c(color: Color) = ColorProvider(color)

@Composable
private fun WidgetContent(s: DaySnapshot) {
    val openApp = actionStartActivity<MainActivity>()
    val day = s.found?.day
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Palette.Bg)
            .cornerRadius(18.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Header(s, day, openApp)
        if (day == null) {
            Spacer(GlanceModifier.height(10.dp))
            Text(
                "План на сегодня не загружен",
                modifier = GlanceModifier.clickable(openApp),
                style = TextStyle(color = c(Palette.Text), fontSize = 14.sp),
            )
            Text(
                "Нажми, чтобы загрузить JSON недели",
                modifier = GlanceModifier.clickable(openApp),
                style = TextStyle(color = c(Palette.Muted), fontSize = 12.sp),
            )
            return@Column
        }
        Spacer(GlanceModifier.height(6.dp))
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            item { NutritionBlock(day, openApp) }
            if (day.sessions.isEmpty()) {
                item {
                    Text(
                        "ОТДЫХ",
                        modifier = GlanceModifier.padding(top = 8.dp).clickable(openApp),
                        style = TextStyle(color = c(Palette.Rest), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = condensed),
                    )
                }
            }
            items(day.sessions) { session -> SessionBlock(session, openApp) }
            if (s.checks.isNotEmpty()) {
                item { ChecklistHeader(s) }
                items(s.checks) { check -> ChecklistRow(check) }
            }
        }
    }
}

@Composable
private fun Header(s: DaySnapshot, day: Day?, openApp: androidx.glance.action.Action) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(openApp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${s.dow} ${s.ddmm}",
            style = TextStyle(
                color = c(if (day != null) Palette.kind(day.kind) else Palette.Text),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = condensed,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            s.found?.stored?.plan?.week?.id ?: "",
            style = TextStyle(color = c(Palette.Muted), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun NutritionBlock(day: Day, openApp: androidx.glance.action.Action) {
    val n = day.nutrition ?: return
    Column(modifier = GlanceModifier.fillMaxWidth().clickable(openApp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${n.kcal} ккал",
                style = TextStyle(color = c(Palette.Body), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                "Б ${n.proteinG} · Ж ${n.fatG} · У ${n.carbsG}",
                maxLines = 1,
                style = TextStyle(color = c(Palette.Text), fontSize = 14.sp, fontFamily = condensed),
            )
        }
        n.type?.let {
            Text(it, style = TextStyle(color = c(Palette.Muted), fontSize = 11.sp))
        }
        n.notes.take(2).forEach { note ->
            Text("· $note", maxLines = 2, style = TextStyle(color = c(Palette.Muted), fontSize = 11.sp))
        }
    }
}

@Composable
private fun SessionBlock(session: Session, openApp: androidx.glance.action.Action) {
    val color = Palette.kind(session.kind)
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp).clickable(openApp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                session.time,
                style = TextStyle(color = c(color), fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                session.title.uppercase(),
                maxLines = 1,
                style = TextStyle(color = c(color), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = condensed),
            )
        }
        session.summary.take(2).forEach { line ->
            Text("· $line", maxLines = 2, style = TextStyle(color = c(Palette.Muted), fontSize = 11.sp))
        }
    }
}

@Composable
private fun ChecklistHeader(s: DaySnapshot) {
    val allDone = s.doneCount == s.checks.size
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ПОДГОТОВИТЬ",
            style = TextStyle(color = c(Palette.Eval), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = condensed),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            "${s.doneCount}/${s.checks.size}",
            style = TextStyle(color = c(if (allDone) Palette.Green else Palette.Muted), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun ChecklistRow(check: CheckState) {
    val text = listOfNotNull(check.item.item, check.item.whenText).joinToString(" · ")
    CheckBox(
        checked = check.checked,
        onCheckedChange = actionRunCallback<ToggleChecklistAction>(
            actionParametersOf(ToggleChecklistAction.KEY to check.key),
        ),
        text = text,
        maxLines = 2,
        style = TextStyle(
            color = c(if (check.checked) Palette.Faint else Palette.Text),
            fontSize = 12.sp,
            textDecoration = if (check.checked) TextDecoration.LineThrough else TextDecoration.None,
        ),
        colors = CheckboxDefaults.colors(
            checkedColor = c(Palette.Green),
            uncheckedColor = c(Palette.Muted),
        ),
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

internal fun ActionParameters.checkKey(): String? = this[ToggleChecklistAction.KEY]
