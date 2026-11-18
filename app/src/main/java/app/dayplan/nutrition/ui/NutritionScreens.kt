package app.dayplan.nutrition.ui

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
        items(Meals.ALL) { meal -> MealCard(meal, entries.filter { it.meal == meal }, onAdd, onEntry) }
        val other = entries.filter { it.meal !in Meals.ALL }
        if (other.isNotEmpty()) item { MealCard("Другое", other, onAdd, onEntry, canAdd = false) }
    }
}

@Composable
private fun MealCard(meal: String, items: List<LogEntryEntity>, onAdd: (String) -> Unit, onEntry: (LogEntryEntity) -> Unit, canAdd: Boolean = true) {
    val sum = LogMath.total(items)
    Card {
        SectionLabel(meal, Palette.Muted, right = if (items.isEmpty()) null else "${fmt0(sum.kcal)} ккал", rightColor = Palette.Text)
        items.forEach { e -> EntryRow(e, onClick = { onEntry(e) }) }
        if (canAdd) {
            TextButton(onClick = { onAdd(meal) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("+ Добавить", color = Palette.Run, fontSize = 13.sp)
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
                    Button(onClick = onScan, colors = ButtonDefaults.buttonColors(containerColor = Palette.Body, contentColor = Palette.Bg)) {
                        Text("Сканировать штрихкод")
                    }
                }
                Button(onClick = onNewProduct, colors = ButtonDefaults.buttonColors(containerColor = Palette.Surface2, contentColor = Palette.Text)) {
                    Text("+ Новый продукт")
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
    portions: List<ProductPortionEntity>,
    initialGrams: Double?,
    confirmLabel: String,
    onConfirm: (grams: Double) -> Unit,
    onDismiss: () -> Unit,
    onEditProduct: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    extra: @Composable (() -> Unit)? = null,
) {
    val start = initialGrams ?: portions.firstOrNull { it.isDefault }?.grams ?: 100.0
    var text by remember { mutableStateOf(fmtInput(start)) }
    val grams = parseDecimal(text)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(product.name, fontSize = 17.sp, color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text("Граммы") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (portions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        portions.forEach { p -> Chip("${p.label} · ${fmt0(p.grams)} г") { text = fmtInput(p.grams) } }
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
        swellK = original?.swellK,
        swellKMin = original?.swellKMin,
        swellKMax = original?.swellKMax,
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
            barcodes.add(off.code)
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
private fun Field(
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
