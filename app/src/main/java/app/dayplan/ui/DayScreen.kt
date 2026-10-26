package app.dayplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dayplan.data.CheckState
import app.dayplan.data.DaySnapshot
import app.dayplan.data.StoredPlan
import app.dayplan.data.label
import app.dayplan.model.Nutrition
import app.dayplan.model.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(8.dp)

@Composable
fun DayScreen(
    snapshot: DaySnapshot?,
    isToday: Boolean,
    plans: List<StoredPlan>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onImport: () -> Unit,
    onToggle: (key: String, checked: Boolean) -> Unit,
    onDelete: (fileName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DateHeader(snapshot, isToday, onPrev, onNext, onToday) }
        val day = snapshot?.found?.day
        if (snapshot != null && day == null) {
            item { EmptyDay(onImport) }
        }
        if (snapshot != null && day != null) {
            item { WeekLine(snapshot) }
            day.nutrition?.let { n -> item { NutritionCard(n) } }
            if (day.sessions.isEmpty()) item { RestCard() }
            items(day.sessions) { SessionCard(it) }
            if (snapshot.checks.isNotEmpty()) item { ChecklistCard(snapshot, onToggle) }
            val rules = snapshot.found.stored.plan.week.nutritionRules
            if (rules.isNotEmpty()) item { RulesCard(rules) }
        }
        item { PlansCard(plans, onImport, onDelete) }
    }
}

@Composable
private fun DateHeader(s: DaySnapshot?, isToday: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val color = s?.found?.day?.kind?.let(Palette::kind) ?: Palette.Text
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NavButton("‹", onPrev)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    listOfNotNull(s?.dow, if (isToday) "сегодня" else null).joinToString(" · ").uppercase(),
                    fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.5.sp, color = Palette.Muted,
                )
                Text(s?.ddmm ?: "", fontFamily = Fonts.Bebas, fontSize = 52.sp, lineHeight = 52.sp, color = color)
            }
            NavButton("›", onNext)
        }
        if (!isToday) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("К сегодняшнему дню", color = Palette.Run, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 34.sp, color = Palette.Muted, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun WeekLine(s: DaySnapshot) {
    val w = s.found!!.stored.plan.week
    Text(
        listOfNotNull(w.id, w.subtitle).joinToString(" · ").uppercase(),
        fontFamily = Fonts.Mono, fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Run,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Card(accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Palette.Surface, CardShape)
            .border(1.dp, Palette.Border, CardShape),
    ) {
        if (accent != null) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), content = content)
    }
}

@Composable
private fun SectionLabel(text: String, color: Color, right: String? = null, rightColor: Color = Palette.Muted) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.2.sp, color = color, modifier = Modifier.weight(1f))
        if (right != null) Text(right, fontFamily = Fonts.Mono, fontSize = 12.sp, color = rightColor)
    }
}

@Composable
private fun Bullet(text: String, color: Color = Palette.Text) {
    Row(Modifier.padding(top = 4.dp)) {
        Text("·", color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.width(12.dp))
        Text(text, color = color, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun NutritionCard(n: Nutrition) {
    Card(accent = Palette.Body) {
        SectionLabel("Питание", Palette.Body, right = n.type)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("${n.kcal}", fontFamily = Fonts.Bebas, fontSize = 46.sp, lineHeight = 46.sp, color = Palette.Body)
            Text(" ккал", fontSize = 14.sp, color = Palette.Muted, modifier = Modifier.padding(bottom = 7.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Macro("Белок", n.proteinG, Modifier.weight(1f))
            Macro("Жиры", n.fatG, Modifier.weight(1f))
            Macro("Углеводы", n.carbsG, Modifier.weight(1f))
        }
        n.notes.forEach { Bullet(it) }
    }
}

@Composable
private fun Macro(label: String, grams: Int, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$grams", fontFamily = Fonts.Bebas, fontSize = 28.sp, lineHeight = 28.sp, color = Palette.Text)
            Text(" г", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(label.uppercase(), fontFamily = Fonts.Mono, fontSize = 9.sp, letterSpacing = 1.sp, color = Palette.Muted)
    }
}

@Composable
private fun RestCard() {
    Card(accent = Palette.Rest) {
        Text("ОТДЫХ", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 1.sp, color = Palette.Rest)
        Text("Тренировок нет — главная задача дня в питании", fontSize = 13.sp, color = Palette.Muted)
    }
}

@Composable
private fun SessionCard(s: Session) {
    val color = Palette.kind(s.kind)
    Card(accent = color) {
        Text(s.time, fontFamily = Fonts.Mono, fontSize = 12.sp, color = color)
        Text(s.title.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.8.sp, lineHeight = 24.sp, color = color)
        s.meta?.let { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp)) }
        s.summary.forEach { Bullet(it) }
        s.table?.let { t ->
            t.rows.forEach { row ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
                Text(row.firstOrNull() ?: "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Text, modifier = Modifier.padding(top = 8.dp))
                row.drop(1).forEachIndexed { i, value ->
                    if (value.isBlank() || value == "—") return@forEachIndexed
                    Row(Modifier.padding(top = 3.dp)) {
                        Text(
                            t.head.getOrNull(i + 1)?.lowercase() ?: "",
                            fontFamily = Fonts.Mono, fontSize = 10.sp, lineHeight = 16.sp, color = Palette.Muted,
                            modifier = Modifier.widthIn(min = 92.dp, max = 92.dp),
                        )
                        Text(value, fontFamily = Fonts.Mono, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistCard(s: DaySnapshot, onToggle: (String, Boolean) -> Unit) {
    val allDone = s.doneCount == s.checks.size
    Card(accent = Palette.Eval) {
        SectionLabel(
            "Подготовить к передаче", Palette.Eval,
            right = "${s.doneCount} / ${s.checks.size}", rightColor = if (allDone) Palette.Green else Palette.Muted,
        )
        s.checks.forEach { ChecklistRow(it, onToggle) }
    }
}

@Composable
private fun ChecklistRow(c: CheckState, onToggle: (String, Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(c.key, !c.checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = c.checked,
            onCheckedChange = { onToggle(c.key, it) },
            colors = CheckboxDefaults.colors(
                checkedColor = Palette.Green,
                uncheckedColor = Palette.Muted,
                checkmarkColor = Palette.Bg,
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                c.item.item, fontSize = 14.sp, lineHeight = 19.sp,
                color = if (c.checked) Palette.Faint else Palette.Text,
                textDecoration = if (c.checked) TextDecoration.LineThrough else null,
            )
            c.item.whenText?.let { Text(it, fontFamily = Fonts.Mono, fontSize = 10.sp, color = Palette.Muted) }
        }
    }
}

@Composable
private fun RulesCard(rules: List<String>) {
    Card(accent = Palette.Accent2) {
        SectionLabel("Правила недели", Palette.Accent2)
        rules.forEach { Bullet(it) }
    }
}

@Composable
private fun EmptyDay(onImport: () -> Unit) {
    Card {
        Text("План на этот день не загружен", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.Text)
        Text(
            "Скачай JSON недели из проекта и открой его этим приложением — или нажми кнопку ниже.",
            fontSize = 13.sp, lineHeight = 19.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        ImportButton(onImport)
    }
}

@Composable
private fun ImportButton(onImport: () -> Unit) {
    Button(
        onClick = onImport,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Run, contentColor = Palette.Bg),
        shape = RoundedCornerShape(6.dp),
    ) { Text("Загрузить план") }
}

@Composable
private fun PlansCard(plans: List<StoredPlan>, onImport: () -> Unit, onDelete: (String) -> Unit) {
    val fmt = SimpleDateFormat("dd.MM HH:mm", Locale("ru"))
    Card {
        SectionLabel("Загруженные недели", Palette.Muted)
        if (plans.isEmpty()) {
            Text("Пока нет", fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        plans.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.label(), fontFamily = Fonts.Mono, fontSize = 13.sp, color = Palette.Text)
                    Text("загружен ${fmt.format(Date(p.importedAt))}", fontSize = 11.sp, color = Palette.Muted)
                }
                TextButton(onClick = { onDelete(p.fileName) }) { Text("Удалить", color = Palette.Thr, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        ImportButton(onImport)
    }
}
