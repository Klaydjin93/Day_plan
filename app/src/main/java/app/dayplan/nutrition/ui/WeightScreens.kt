package app.dayplan.nutrition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dayplan.data.DayLookup
import app.dayplan.data.PlanStore
import app.dayplan.nutrition.data.AutoChecks
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionRepo
import app.dayplan.nutrition.data.WeightView
import app.dayplan.nutrition.domain.WeightAverage
import app.dayplan.nutrition.domain.parseDecimal
import app.dayplan.ui.Card
import app.dayplan.ui.Fonts
import app.dayplan.ui.Palette
import app.dayplan.ui.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DDMM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

@Composable
fun WeightScreen(
    date: LocalDate,
    isToday: Boolean,
    view: WeightView?,
    protocol: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(date, view?.today) { mutableStateOf(view?.today?.kg?.let(::fmtInput).orEmpty()) }
    val kg = parseDecimal(text)?.takeIf { it in 20.0..400.0 }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DateBar(date, isToday, null, onPrev, onNext, onToday) }
        item {
            Card(accent = Palette.Body) {
                SectionLabel("Вес утром", Palette.Body)
                if (protocol != null) Text(protocol, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true, label = { Text("кг") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { kg?.let(onSave) }, enabled = kg != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Body, contentColor = Palette.Bg),
                    ) { Text(if (view?.today == null) "Записать" else "Исправить") }
                }
                if (view?.today != null) {
                    TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("Удалить замер за ${date.format(DDMM)}", color = Palette.Thr, fontSize = 13.sp)
                    }
                }
            }
        }
        if (view != null) {
            item {
                Card {
                    SectionLabel("Среднее", Palette.Muted)
                    AverageLine("7 дней по ${date.format(DDMM)}", view.rolling, big = true)
                    view.week?.let { w ->
                        AverageLine("Неделя плана ${w.from.format(DDMM)}–${w.to.format(DDMM)}", w, big = false)
                        val prev = view.previousWeek
                        if (w.kg != null && prev?.kg != null && !w.thin && !prev.thin) {
                            val d = w.kg - prev.kg
                            Text(
                                "к прошлой неделе: ${if (d >= 0) "+" else "−"}${fmt1(kotlin.math.abs(d))} кг",
                                fontFamily = Fonts.Mono, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Text(
                        "Дневной вес — вода, гликоген и креатин. Читаем только среднее.",
                        fontSize = 11.sp, color = Palette.Faint, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (view.latest.isNotEmpty()) {
                item { SectionLabel("Замеры", Palette.Muted) }
                items(view.latest, key = { it.date }) { w ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Text(LocalDate.parse(w.date).format(DDMM), fontFamily = Fonts.Mono, fontSize = 13.sp, color = Palette.Faint, modifier = Modifier.weight(1f))
                        Text("${fmt1(w.kg)} кг", fontFamily = Fonts.Mono, fontSize = 13.sp, color = Palette.Faint)
                    }
                }
            }
        }
    }
}

@Composable
private fun AverageLine(title: String, a: WeightAverage, big: Boolean) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(title, fontSize = 12.sp, color = Palette.Muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                a.kg?.let { fmt1(it) } ?: "—",
                fontFamily = Fonts.Bebas, fontSize = if (big) 40.sp else 26.sp, lineHeight = if (big) 40.sp else 26.sp,
                color = if (a.thin) Palette.Muted else Palette.Text,
            )
            Text(
                " кг · замеров: ${a.count}${if (a.thin && a.count > 0) " · мало данных" else ""}",
                fontSize = 12.sp, color = if (a.thin) Palette.Hema else Palette.Muted, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
fun WeightTab(
    date: LocalDate,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onChecklistChanged: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember { NutritionRepo(NutritionDatabase.get(context)) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<WeightView?>(null) }
    var protocol by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(date, version) {
        val (v, p) = withContext(Dispatchers.IO) {
            val p = DayLookup.find(PlanStore(context).all(), date)?.day?.report?.firstOrNull { it.auto == AutoChecks.WEIGHT }?.item
            repo.weightView(date) to p
        }
        view = v
        protocol = p
    }

    WeightScreen(
        date = date, isToday = isToday, view = view, protocol = protocol,
        onPrev = onPrev, onNext = onNext, onToday = onToday,
        onSave = { kg ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    repo.saveWeight(date, kg)
                    AutoChecks.mark(context, date, AutoChecks.WEIGHT)
                }
                version++
                onChecklistChanged()
            }
        },
        onDelete = {
            scope.launch {
                withContext(Dispatchers.IO) { repo.deleteWeight(date) }
                version++
            }
        },
        modifier = modifier,
    )
}
