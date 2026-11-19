package app.dayplan.nutrition.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dayplan.nutrition.data.DayTargetsView
import app.dayplan.nutrition.data.LogEntryEntity
import app.dayplan.nutrition.data.LogMath
import app.dayplan.nutrition.data.Meals
import app.dayplan.nutrition.data.OffProduct
import app.dayplan.nutrition.data.ProductEntity
import app.dayplan.nutrition.data.ProductKind
import app.dayplan.nutrition.data.ProductPortionEntity
import app.dayplan.nutrition.data.ProductSource
import app.dayplan.nutrition.data.RefProduct
import app.dayplan.nutrition.data.TemplateView
import app.dayplan.nutrition.domain.Portions
import app.dayplan.nutrition.domain.PortionOption
import app.dayplan.nutrition.domain.Barcodes
import app.dayplan.nutrition.domain.parseDecimal
import app.dayplan.ui.Bullet
import app.dayplan.ui.Card
import app.dayplan.ui.Fonts
import app.dayplan.ui.NavButton
import app.dayplan.ui.Palette
import app.dayplan.ui.SectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DDMM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val DOW = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
private val RU = Locale.forLanguageTag("ru")

internal fun fmt1(x: Double): String = String.format(RU, "%.1f", x)
internal fun fmt0(x: Double): String = x.roundToInt().toString()

/** Заголовок с датой и стрелками — общий для вкладок «Питание» и «Вес». */
@Composable
fun DateBar(date: LocalDate, isToday: Boolean, subtitle: String?, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NavButton("‹", onPrev)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    listOfNotNull(DOW[date.dayOfWeek.value - 1], if (isToday) "сегодня" else null).joinToString(" · ").uppercase(),
                    fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.5.sp, color = Palette.Muted,
                )
                Text(date.format(DDMM), fontFamily = Fonts.Bebas, fontSize = 44.sp, lineHeight = 44.sp, color = Palette.Body)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Palette.Muted)
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

// ------------------------------------------------------------------ день

@Composable
fun NutritionDayScreen(
    date: LocalDate,
    isToday: Boolean,
    targets: DayTargetsView?,
    entries: List<LogEntryEntity>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAdd: (meal: String) -> Unit,
    onEntry: (LogEntryEntity) -> Unit,
    modifier: Modifier = Modifier,
    onSaveTemplate: ((meal: String, entries: List<LogEntryEntity>) -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    val total = LogMath.total(entries)
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DateBar(date, isToday, targets?.type, onPrev, onNext, onToday) }
        item {
            Card(accent = Palette.Body) {
                if (targets == null) {
                    SectionLabel("Итого", Palette.Body, right = "${fmt0(total.kcal)} ккал")
                    Text(
                        "Б ${fmt0(total.protein)} · Ж ${fmt0(total.fat)} · У ${fmt0(total.carbs)} г",
                        fontFamily = Fonts.Mono, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.padding(top = 4.dp),
                    )
                    Text("На этот день целей нет — загрузи план недели.", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 6.dp))
                } else {
                    SectionLabel("Цели дня", Palette.Body)
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoalBar("Ккал", targets.goals.kcal, total.kcal, "ккал")
                        GoalBar("Белки", targets.goals.protein, total.protein, "г")
                        GoalBar("Жиры", targets.goals.fat, total.fat, "г")
                        GoalBar("Углеводы", targets.goals.carbs, total.carbs, "г")
                    }
                    if (targets.shifted) {
                        Text(
                            "Цели перенесены с ${LocalDate.parse(targets.originDate).format(DDMM)}",
                            fontSize = 12.sp, color = Palette.Hema, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (!targets.goals.fromPlan) {
                        Text("Смысл целей не задан планом — показаны как простые цели.", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
                    }
                    targets.notes.forEach { Bullet(it, Palette.Muted) }
                }
            }
        }
        if (onExport != null) item {
            OutlinedButton(
                onClick = onExport, modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Palette.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text),
            ) { Text("Итог для чата") }
        }
        items(Meals.ALL) { meal -> MealCard(meal, entries.filter { it.meal == meal }, onAdd, onEntry, onSaveTemplate = onSaveTemplate) }
        val other = entries.filter { it.meal !in Meals.ALL }
        if (other.isNotEmpty()) item { MealCard("Другое", other, onAdd, onEntry, canAdd = false) }
    }
}

@Composable
private fun MealCard(
    meal: String,
    items: List<LogEntryEntity>,
    onAdd: (String) -> Unit,
    onEntry: (LogEntryEntity) -> Unit,
    canAdd: Boolean = true,
    onSaveTemplate: ((String, List<LogEntryEntity>) -> Unit)? = null,
) {
    val sum = LogMath.total(items)
    Card {
        SectionLabel(meal, Palette.Muted, right = if (items.isEmpty()) null else "${fmt0(sum.kcal)} ккал", rightColor = Palette.Text)
        items.forEach { e -> EntryRow(e, onClick = { onEntry(e) }) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canAdd) {
                TextButton(onClick = { onAdd(meal) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("+ Добавить", color = Palette.Run, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (onSaveTemplate != null && items.any { it.productId != null }) {
                TextButton(onClick = { onSaveTemplate(meal, items) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("В шаблон", color = Palette.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: LogEntryEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.productName, fontSize = 14.sp, color = Palette.Text)
            Text(
                listOfNotNull("${fmt0(e.grams)} г", "Б ${fmt1(e.protein)} · Ж ${fmt1(e.fat)} · У ${fmt1(e.carbs)}", if (!e.productVerified) "не проверено" else null)
                    .joinToString(" · "),
                fontSize = 11.sp, color = if (e.productVerified) Palette.Muted else Palette.Hema,
            )
        }
        Text(fmt0(e.kcal), fontFamily = Fonts.Mono, fontSize = 14.sp, color = Palette.Text)
    }
}

// ------------------------------------------------------------------ добавление и привязка

/**
 * Поиск по своей базе. Тот же экран служит для привязки штрихкода к карточке:
 * тогда title и hint объясняют, что выбор продукта привяжет к нему код.
 */
@Composable
fun AddFoodScreen(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    results: List<ProductEntity>,
    onPick: (ProductEntity) -> Unit,
    onNewProduct: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onScan: (() -> Unit)? = null,
    refResults: List<RefProduct> = emptyList(),
    onPickRef: (RefProduct) -> Unit = {},
    online: OnlineState? = null,
    onSearchOnline: () -> Unit = {},
    onPickOnline: (OffProduct) -> Unit = {},
    templates: List<TemplateView> = emptyList(),
    onTemplate: (TemplateView) -> Unit = {},
    onNewDish: (() -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Bg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BackTitle(title, onBack) }
        if (hint != null) item { Text(hint, fontSize = 13.sp, color = Palette.Muted) }
        item {
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                label = { Text("Название или бренд") }, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onScan != null) {
                    Button(onClick = onScan, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Palette.Body, contentColor = Palette.Bg)) {
                        Text("Сканировать", maxLines = 1)
                    }
                }
                Button(onClick = onNewProduct, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Palette.Surface2, contentColor = Palette.Text)) {
                    Text("+ Новый продукт", maxLines = 1)
                }
            }
        }
        if (onNewDish != null) {
            item {
                TextButton(onClick = onNewDish, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("+ Новое блюдо из продуктов базы", color = Palette.Run, fontSize = 13.sp)
                }
            }
        }
        if (query.isBlank() && templates.isNotEmpty()) {
            item { SourceLabel("Шаблоны") }
            items(templates, key = { "tpl-${it.template.id}" }) { t ->
                Card {
                    Column(Modifier.fillMaxWidth().clickable { onTemplate(t) }) {
                        Text(t.template.name, fontSize = 14.sp, color = Palette.Text)
                        Text(
                            "${t.lines.joinToString(", ") { "${it.product.name} ${fmt0(it.grams)} г" }} · ${fmt0(t.kcal)} ккал",
                            fontSize = 11.sp, color = Palette.Muted, maxLines = 2,
                        )
                    }
                }
            }
        }
        item {
            Text(
                if (query.isBlank()) "Недавние и последние заведённые" else if (results.isEmpty()) "В своей базе не найдено" else "Своя база",
                fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = Palette.Muted,
            )
        }
        items(results, key = { it.id }) { p -> ProductRow(p) { onPick(p) } }
        val mine = refResults.filter { it.isChat }
        val usda = refResults.filter { it.isUsda }
        val offline = refResults.filter { !it.adoptsDirectly }
        if (mine.isNotEmpty()) {
            item { SourceLabel("Мои блюда · расчёт из чата") }
            items(mine, key = { "chat-${it.sourceId}" }) { r ->
                ExternalRow(r.name, r.brand, r.kcal, r.protein, r.fat, r.carbs, "моё блюдо", Palette.Green) { onPickRef(r) }
            }
        }
        if (usda.isNotEmpty()) {
            item { SourceLabel("Справочник USDA · справочные значения") }
            items(usda, key = { "usda-${it.sourceId}" }) { r ->
                ExternalRow(r.name, r.brand, r.kcal, r.protein, r.fat, r.carbs, "справочное", Palette.Muted) { onPickRef(r) }
            }
        }
        if (offline.isNotEmpty()) {
            item { SourceLabel("Open Food Facts · офлайн") }
            items(offline, key = { "off-${it.sourceId}" }) { r ->
                ExternalRow(r.name, r.brand, r.kcal, r.protein, r.fat, r.carbs, "сверить с этикеткой", Palette.Hema) { onPickRef(r) }
            }
        }
        if (online != null && query.isNotBlank()) {
            when (online) {
                OnlineState.Idle -> item {
                    TextButton(onClick = onSearchOnline, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("Искать «${query.trim()}» в Open Food Facts онлайн", color = Palette.Run, fontSize = 13.sp)
                    }
                }
                OnlineState.Loading -> item { Text("Ищу в Open Food Facts…", fontSize = 13.sp, color = Palette.Muted) }
                is OnlineState.Failed -> item { Text(online.message, fontSize = 13.sp, color = Palette.Thr) }
                is OnlineState.Results -> {
                    item { SourceLabel(if (online.products.isEmpty()) "Open Food Facts · онлайн: ничего" else "Open Food Facts · онлайн") }
                    items(online.products, key = { "online-${it.code}-${it.name}" }) { o ->
                        ExternalRow(
                            o.name.orEmpty(), o.brand, o.kcal100, o.protein100, o.fat100, o.carbs100,
                            if (o.complete) "сверить с этикеткой" else "нет части КБЖУ", Palette.Hema,
                        ) { onPickOnline(o) }
                    }
                }
            }
        }
        if (refResults.isNotEmpty() || online is OnlineState.Results) {
            item {
                Text(
                    "Данные: Open Food Facts (ODbL), USDA FoodData Central (CC0).",
                    fontSize = 10.sp, color = Palette.Faint, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Поиск в Open Food Facts онлайн — только по кнопке: у OFF лимиты запросов. */
sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Results(val products: List<OffProduct>) : OnlineState
    data class Failed(val message: String) : OnlineState
}

@Composable
private fun SourceLabel(text: String) {
    Text(text, fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = Palette.Muted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ExternalRow(
    name: String, brand: String?, kcal: Double?, p: Double?, f: Double?, c: Double?,
    tag: String, tagColor: Color, onClick: () -> Unit,
) {
    fun v(x: Double?, digits: Int) = x?.let { if (digits == 0) fmt0(it) else fmt1(it) } ?: "—"
    Card {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Text(name, fontSize = 14.sp, color = Palette.Text)
            Text(
                listOfNotNull(brand, "на 100 г: ${v(kcal, 0)} ккал · Б ${v(p, 1)} · Ж ${v(f, 1)} · У ${v(c, 1)}").joinToString(" · "),
                fontSize = 11.sp, color = Palette.Muted,
            )
            Text(tag, fontSize = 11.sp, color = tagColor)
        }
    }
}

/** Штрихкод не нашёлся ни в своей базе, ни в Open Food Facts. */
@Composable
fun BarcodeNotFoundScreen(
    code: String,
    offError: String?,
    onLink: () -> Unit,
    onNewProduct: () -> Unit,
    onScanAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Bg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackTitle("Штрихкод не найден", onBack) }
        item {
            Card(accent = Palette.Hema) {
                Text(code, fontFamily = Fonts.Mono, fontSize = 18.sp, color = Palette.Text)
                Text(
                    if (offError == null) "Нет ни в своей базе, ни в Open Food Facts."
                    else "В своей базе нет. Open Food Facts не проверен: $offError.",
                    fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            Button(onClick = onLink, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Palette.Body, contentColor = Palette.Bg)) {
                Text("Привязать к продукту из базы")
            }
        }
        item { Text("Тот же продукт в другой упаковке или под другим кодом — код добавится к его карточке.", fontSize = 12.sp, color = Palette.Muted) }
        item {
            Button(onClick = onNewProduct, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Palette.Surface2, contentColor = Palette.Text)) {
                Text("+ Новый продукт с этим кодом")
            }
        }
        item {
            TextButton(onClick = onScanAgain, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Сканировать ещё раз", color = Palette.Run, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ProductRow(p: ProductEntity, onClick: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Text(p.name, fontSize = 14.sp, color = Palette.Text)
            Text(
                listOfNotNull(
                    if (p.kind == ProductKind.DISH) "блюдо" else null,
                    when (p.source) {
                        ProductSource.USDA -> "справочное"
                        ProductSource.CHAT -> "из чата"
                        else -> null
                    },
                    p.brand,
                    "на 100 г: ${fmt0(p.kcal100)} ккал · Б ${fmt1(p.protein100)} · Ж ${fmt1(p.fat100)} · У ${fmt1(p.carbs100)}",
                    if (!p.verified) "не проверено" else null,
                ).joinToString(" · "),
                fontSize = 11.sp, color = if (p.verified) Palette.Muted else Palette.Hema,
            )
        }
    }
}

@Composable
internal fun BackTitle(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("‹ Назад", color = Palette.Run, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, color = Palette.Text)
    }
}

// ------------------------------------------------------------------ порция

/** Сколько граммов съедено. Показывает КБЖУ порции до записи. */
@Composable
fun PortionDialog(
    product: ProductEntity,
    portions: List<PortionOption>,
    initialGrams: Double?,
    confirmLabel: String,
    onConfirm: (grams: Double) -> Unit,
    onDismiss: () -> Unit,
    onEditProduct: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    extra: @Composable (() -> Unit)? = null,
) {
    // Режим «порции» — если у продукта есть своя порция; «всё блюдо» одно не повод: 1 × кастрюля — редкий случай.
    val matched = initialGrams?.let { Portions.match(it, portions) }
    val defaultPortion = portions.firstOrNull { it.isDefault && !it.wholeDish } ?: portions.firstOrNull { !it.wholeDish }
    var byPortions by remember {
        mutableStateOf(if (initialGrams == null) defaultPortion != null else matched != null)
    }
    var option by remember { mutableStateOf(matched?.first ?: defaultPortion ?: portions.firstOrNull()) }
    var countText by remember { mutableStateOf(fmtCount(matched?.second ?: 1.0)) }
    var gramsText by remember { mutableStateOf(fmtInput(initialGrams ?: defaultPortion?.grams ?: 100.0)) }

    val count = parseDecimal(countText)?.takeIf { it > 0 }
    val grams = if (byPortions) option?.let { o -> count?.let { Portions.grams(o, it) } } else parseDecimal(gramsText)?.takeIf { it > 0 }

    fun switchTo(portionsMode: Boolean) {
        if (portionsMode == byPortions) return
        if (portionsMode) {
            // из граммов в порции: если вес ровно ложится в порцию — показать её число, иначе 1
            val m = parseDecimal(gramsText)?.let { Portions.match(it, portions) }
            if (m != null) { option = m.first; countText = fmtCount(m.second) } else countText = "1"
        } else {
            grams?.let { gramsText = fmtInput(it) }
        }
        byPortions = portionsMode
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(product.name, fontSize = 17.sp, color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (portions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Порции", selected = byPortions) { switchTo(true) }
                        Chip("Граммы", selected = !byPortions) { switchTo(false) }
                    }
                }
                if (byPortions && option != null) {
                    val o = option!!
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        portions.forEach { p ->
                            Chip("${p.label} · ${fmt0(p.grams)} г", selected = p == o) {
                                if (p != o) { option = p; countText = if (p.wholeDish) fmtCount(0.25) else "1" }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { countText = fmtCount(((count ?: o.step) - o.step).coerceAtLeast(o.step)) }) {
                            Text("−", fontSize = 20.sp, color = Palette.Text)
                        }
                        OutlinedTextField(
                            value = countText, onValueChange = { countText = it }, singleLine = true,
                            label = { Text("Сколько порций") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        TextButton(onClick = { countText = fmtCount((count ?: 0.0) + o.step) }) {
                            Text("+", fontSize = 20.sp, color = Palette.Text)
                        }
                    }
                    if (o.wholeDish) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.25, 0.5, 0.75).forEach { q ->
                                Chip("${Portions.countLabel(q)} блюда", selected = count != null && abs(count - q) < 1e-6) { countText = fmtCount(q) }
                            }
                        }
                    }
                    Text(
                        grams?.let { "= ${fmt0(it)} г" } ?: "Введи число порций больше нуля",
                        fontSize = 12.sp, color = if (grams == null) Palette.Hema else Palette.Muted,
                    )
                } else {
                    OutlinedTextField(
                        value = gramsText, onValueChange = { gramsText = it }, singleLine = true,
                        label = { Text("Граммы") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (portions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            portions.forEach { p -> Chip("${p.label} · ${fmt0(p.grams)} г") { gramsText = fmtInput(p.grams) } }
                        }
                    }
                }
                val n = grams?.let { LogMath.snapshot(product, it) }
                Text(
                    if (n == null) "Введи вес больше нуля"
                    else "${fmt0(n.kcal)} ккал · Б ${fmt1(n.protein)} · Ж ${fmt1(n.fat)} · У ${fmt1(n.carbs)}",
                    fontFamily = Fonts.Mono, fontSize = 13.sp, color = if (n == null) Palette.Hema else Palette.Text,
                )
                if (!product.verified) Text("Карточка не сверена с этикеткой.", fontSize = 12.sp, color = Palette.Hema)
                extra?.invoke()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onEditProduct != null) TextButton(onClick = onEditProduct) { Text("Изменить карточку", color = Palette.Run, fontSize = 13.sp) }
                    if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить запись", color = Palette.Thr, fontSize = 13.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { grams?.let(onConfirm) }, enabled = grams != null) { Text(confirmLabel, color = if (grams != null) Palette.Green else Palette.Faint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Palette.Muted) } },
    )
}

/** Итог дня для чата: короткий или с продуктами; копировать или отправить в другое приложение. */
@Composable
fun ExportDialog(
    short: String,
    full: String,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var withProducts by remember { mutableStateOf(false) }
    val text = if (withProducts) full else short
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Итог для чата", fontSize = 17.sp, color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Итог", selected = !withProducts) { withProducts = false }
                    Chip("Итог + продукты", selected = withProducts) { withProducts = true }
                }
                Text(
                    text, fontFamily = Fonts.Mono, fontSize = 11.sp, lineHeight = 15.sp, color = Palette.Text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .background(Palette.Surface2, RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCopy(text) }) { Text("Копировать", color = Palette.Green) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onShare(text) }) { Text("Отправить…", color = Palette.Run) }
                TextButton(onClick = onDismiss) { Text("Закрыть", color = Palette.Muted) }
            }
        },
    )
}

/** Число порций в поле: «1», «1,5», «0,25». */
internal fun fmtCount(x: Double): String {
    val r = Math.round(x * 100) / 100.0
    return if (r == Math.round(r).toDouble()) Math.round(r).toString() else String.format(RU, "%.2f", r).trimEnd('0').trimEnd(',')
}

@Composable
internal fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = if (selected) Palette.Bg else Palette.Text,
        modifier = Modifier
            .background(if (selected) Palette.Body else Palette.Surface2, RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Выбор приёма пищи — при правке записи. */
@Composable
fun MealPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Meals.ALL.forEach { m -> Chip(m, selected = m == selected) { onSelect(m) } }
    }
}

internal fun fmtInput(x: Double): String =
    if (x == x.roundToInt().toDouble()) x.roundToInt().toString() else String.format(RU, "%.1f", x)

// ------------------------------------------------------------------ карточка продукта

enum class FormMode { NEW, EDIT, CHECK_LABEL }

/** Черновик карточки в форме: числа строками, как их ввели. */
class ProductDraft(from: ProductEntity?, prefillName: String) {
    val id = from?.id ?: 0L
    var name by mutableStateOf(from?.name ?: prefillName)
    var brand by mutableStateOf(from?.brand.orEmpty())
    var kind by mutableStateOf(from?.kind ?: ProductKind.GENERIC)
    var kcal by mutableStateOf(from?.kcal100?.let(::fmtInput).orEmpty())
    var protein by mutableStateOf(from?.protein100?.let(::fmtInput).orEmpty())
    var fat by mutableStateOf(from?.fat100?.let(::fmtInput).orEmpty())
    var carbs by mutableStateOf(from?.carbs100?.let(::fmtInput).orEmpty())
    var fiber by mutableStateOf(from?.fiber100?.let(::fmtInput).orEmpty())
    var swell by mutableStateOf(from?.swellK?.let(::fmtInput).orEmpty())
    var swellMin by mutableStateOf(from?.swellKMin?.let(::fmtInput).orEmpty())
    var swellMax by mutableStateOf(from?.swellKMax?.let(::fmtInput).orEmpty())
    var source = from?.source ?: ProductSource.MANUAL
    var externalId = from?.externalId
    /** Ккал в карточке OFF пересчитаны из кДж — показать при сверке. */
    var kcalFromKj = false
    private val original = from
    val portions = mutableStateListOf<Pair<String, String>>()
    val barcodes = mutableStateListOf<String>()
    var barcodeInput by mutableStateOf("")

    fun errors(): List<String> = buildList {
        if (name.isBlank()) add("Название")
        listOf("Ккал" to kcal, "Белки" to protein, "Жиры" to fat, "Углеводы" to carbs).forEach { (n, v) ->
            if ((parseDecimal(v) ?: -1.0) < 0) add(n)
        }
        if (fiber.isNotBlank() && (parseDecimal(fiber) ?: -1.0) < 0) add("Клетчатка")
        if (swell.isNotBlank() && (parseDecimal(swell) ?: 0.0) <= 1.0) add("Разбухание (больше 1)")
        val k = parseDecimal(swell)
        val lo = parseDecimal(swellMin)
        val hi = parseDecimal(swellMax)
        if ((swellMin.isNotBlank() || swellMax.isNotBlank()) && k == null) add("Разбухание: диапазон без коэффициента")
        if (k != null && ((lo != null && (lo <= 1.0 || lo > k)) || (hi != null && hi < k) || (swellMin.isNotBlank() && lo == null) || (swellMax.isNotBlank() && hi == null))) add("Диапазон разбухания (от ≤ k ≤ до)")
        portions.forEachIndexed { i, (l, g) -> if (l.isBlank() || (parseDecimal(g) ?: 0.0) <= 0) add("Порция ${i + 1}") }
    }

    /** Добавить код из поля ввода или со сканера. Возвращает нормализованный код или null. */
    fun addBarcode(raw: String): String? {
        val code = Barcodes.normalize(raw) ?: return null
        if (code !in barcodes) barcodes.add(code)
        if (raw == barcodeInput) barcodeInput = ""
        return code
    }

    /**
     * Ручной ввод с упаковки считается проверенным. Карточка из Open Food Facts становится
     * проверенной, только если её сверили с этикеткой (verified = true при сохранении).
     */
    fun toEntity(verified: Boolean = true): ProductEntity = ProductEntity(
        id = id,
        name = name.trim(),
        brand = brand.trim().ifEmpty { null },
        kind = kind,
        kcal100 = parseDecimal(kcal)!!,
        protein100 = parseDecimal(protein)!!,
        fat100 = parseDecimal(fat)!!,
        carbs100 = parseDecimal(carbs)!!,
        fiber100 = parseDecimal(fiber),
        swellK = parseDecimal(swell),
        swellKMin = parseDecimal(swell)?.let { parseDecimal(swellMin) },
        swellKMax = parseDecimal(swell)?.let { parseDecimal(swellMax) },
        source = source,
        externalId = externalId,
        verified = verified,
        updatedAt = 0,
    )

    fun portionEntities(): List<ProductPortionEntity> =
        portions.mapIndexed { i, (l, g) -> ProductPortionEntity(productId = id, label = l.trim(), grams = parseDecimal(g)!!, isDefault = i == 0) }

    companion object {
        fun fromOff(off: OffProduct): ProductDraft = ProductDraft(null, off.name.orEmpty()).apply {
            brand = off.brand.orEmpty()
            kind = if (off.brand != null) ProductKind.BRAND else ProductKind.GENERIC
            kcal = off.kcal100?.let { fmtInput(Math.round(it * 10) / 10.0) }.orEmpty()
            protein = off.protein100?.let(::fmtInput).orEmpty()
            fat = off.fat100?.let(::fmtInput).orEmpty()
            carbs = off.carbs100?.let(::fmtInput).orEmpty()
            fiber = off.fiber100?.let(::fmtInput).orEmpty()
            source = ProductSource.OFF
            externalId = off.code
            kcalFromKj = off.kcalFromKj
            Barcodes.normalize(off.code)?.let { barcodes.add(it) }
        }
    }
}

/**
 * Карточка продукта. owners — коды, уже привязанные к другим карточкам: код → название той карточки.
 * onSave(verified): в режиме сверки две кнопки — «сверено» и «без сверки».
 */
@Composable
fun ProductFormScreen(
    draft: ProductDraft,
    mode: FormMode,
    owners: Map<String, String>,
    onScan: () -> Unit,
    onSave: (verified: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showErrors by remember { mutableStateOf(false) }
    var badCode by remember { mutableStateOf(false) }
    val errors = draft.errors()
    fun trySave(verified: Boolean) = if (errors.isEmpty()) onSave(verified) else showErrors = true
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Bg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BackTitle(
                when (mode) {
                    FormMode.NEW -> "Новый продукт"
                    FormMode.EDIT -> "Карточка продукта"
                    FormMode.CHECK_LABEL -> "Сверь с этикеткой"
                },
                onBack,
            )
        }
        if (mode == FormMode.CHECK_LABEL) {
            item {
                Card(accent = Palette.Hema) {
                    Text("Карточка из Open Food Facts. Сверь цифры с этикеткой на упаковке и поправь, если не совпадают.", fontSize = 13.sp, color = Palette.Text)
                    if (draft.kcalFromKj) Text("Ккал в карточке не было — пересчитаны из кДж.", fontSize = 12.sp, color = Palette.Hema, modifier = Modifier.padding(top = 4.dp))
                    if (draft.errors().isNotEmpty()) Text("Каких-то чисел в карточке нет — их нужно ввести с упаковки.", fontSize = 12.sp, color = Palette.Hema, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { Field("Название *", draft.name) { draft.name = it } }
        item { Field("Бренд", draft.brand) { draft.brand = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Без бренда", selected = draft.kind == ProductKind.GENERIC) { draft.kind = ProductKind.GENERIC }
                Chip("Брендовый", selected = draft.kind == ProductKind.BRAND) { draft.kind = ProductKind.BRAND }
            }
        }
        item { Text("Штрихкоды", fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = Palette.Muted) }
        items(draft.barcodes.size) { i ->
            val code = draft.barcodes[i]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(code, fontFamily = Fonts.Mono, fontSize = 14.sp, color = Palette.Text)
                    owners[code]?.let { Text("Сейчас у «$it» — перейдёт к этой карточке", fontSize = 11.sp, color = Palette.Hema) }
                }
                TextButton(onClick = { draft.barcodes.removeAt(i) }) { Text("✕", color = Palette.Muted) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Ввести код", draft.barcodeInput, KeyboardType.Number, Modifier.weight(1f)) { draft.barcodeInput = it; badCode = false }
                TextButton(onClick = { badCode = draft.barcodeInput.isNotBlank() && draft.addBarcode(draft.barcodeInput) == null }) {
                    Text("Добавить", color = Palette.Run, fontSize = 13.sp)
                }
            }
        }
        if (badCode) item { Text("Это не код товара: нужны 8, 12, 13 или 14 цифр.", fontSize = 12.sp, color = Palette.Thr) }
        item {
            TextButton(onClick = onScan, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Сканировать штрихкод камерой", color = Palette.Run, fontSize = 13.sp)
            }
        }
        item { Text("На 100 г — как на этикетке", fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = Palette.Muted) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Ккал *", draft.kcal, KeyboardType.Decimal, Modifier.weight(1f)) { draft.kcal = it }
                Field("Белки *", draft.protein, KeyboardType.Decimal, Modifier.weight(1f)) { draft.protein = it }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Жиры *", draft.fat, KeyboardType.Decimal, Modifier.weight(1f)) { draft.fat = it }
                Field("Углеводы *", draft.carbs, KeyboardType.Decimal, Modifier.weight(1f)) { draft.carbs = it }
            }
        }
        item { Field("Клетчатка", draft.fiber, KeyboardType.Decimal) { draft.fiber = it } }
        item { Field("Разбухание при варке, раз (крупы, макароны)", draft.swell, KeyboardType.Decimal) { draft.swell = it } }
        if (draft.swell.isNotBlank()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Диапазон от", draft.swellMin, KeyboardType.Decimal, Modifier.weight(1f)) { draft.swellMin = it }
                Field("до", draft.swellMax, KeyboardType.Decimal, Modifier.weight(1f)) { draft.swellMax = it }
            }
        }
        item { Text("Порции — первая по умолчанию", fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = Palette.Muted) }
        items(draft.portions.size) { i ->
            val (label, grams) = draft.portions[i]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Порция", label, modifier = Modifier.weight(2f)) { draft.portions[i] = it to grams }
                Field("г", grams, KeyboardType.Decimal, Modifier.weight(1f)) { draft.portions[i] = label to it }
                TextButton(onClick = { draft.portions.removeAt(i) }) { Text("✕", color = Palette.Muted) }
            }
        }
        item {
            TextButton(onClick = { draft.portions.add("" to "") }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("+ Порция («1 яйцо», «1 тортилья»)", color = Palette.Run, fontSize = 13.sp)
            }
        }
        if (showErrors && errors.isNotEmpty()) {
            item { Text("Проверь: ${errors.joinToString(", ")}", fontSize = 13.sp, color = Palette.Thr) }
        }
        item {
            Button(
                onClick = { trySave(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Body, contentColor = Palette.Bg),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (mode == FormMode.CHECK_LABEL) "Сверено с этикеткой — сохранить" else "Сохранить") }
        }
        if (mode == FormMode.CHECK_LABEL) {
            item {
                TextButton(onClick = { trySave(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить без сверки — будет помечено «не проверено»", color = Palette.Hema, fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = modifier,
    )
}

/** Пустое состояние с подсказкой — для вкладок без данных. */
@Composable
internal fun Hint(text: String, color: Color = Palette.Muted) {
    Text(text, fontSize = 13.sp, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
}
