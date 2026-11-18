package app.dayplan.nutrition.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dayplan.nutrition.data.CookwareEntity
import app.dayplan.nutrition.data.DishLine
import app.dayplan.nutrition.data.DishView
import app.dayplan.nutrition.data.ProductEntity
import app.dayplan.nutrition.data.YieldMethodCode
import app.dayplan.nutrition.data.normalizeSearch
import app.dayplan.nutrition.domain.DishMath
import app.dayplan.nutrition.domain.Nutrients
import app.dayplan.nutrition.domain.YieldMethod
import app.dayplan.nutrition.domain.parseDecimal
import app.dayplan.ui.Card
import app.dayplan.ui.Fonts
import app.dayplan.ui.Palette
import app.dayplan.ui.SectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DDMM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val BATCH_SUFFIX = Regex(""" · \d{2}\.\d{2}$""")

/** Черновик блюда в редакторе. Граммы и вес — строками, как их ввели. */
class DishDraft(
    val productId: Long,
    name: String,
    lines: List<DishLine>,
    method: YieldMethod,
    gross: Double?,
    cookwareId: Long?,
    val batchOf: Long?,
    val cookedOn: String?,
) {
    var name by mutableStateOf(name)
    val lines = mutableStateListOf<Pair<ProductEntity, String>>().apply { lines.forEach { add(it.product to fmtInput(it.grams)) } }
    var method by mutableStateOf(method)
    var gross by mutableStateOf(gross?.let(::fmtInput).orEmpty())
    var cookwareId by mutableStateOf(cookwareId)
    /** Вопрос про масло уже задан в этой правке. */
    var oilAsked = false

    fun parsedLines(): List<DishLine>? =
        lines.map { (p, g) -> DishLine(p, parseDecimal(g)?.takeIf { it > 0 } ?: return null) }.takeIf { it.isNotEmpty() }

    fun hasOil(): Boolean = lines.any { (p, _) -> "масл" in normalizeSearch(p.name) }

    fun add(product: ProductEntity) {
        if (lines.none { it.first.id == product.id }) lines.add(product to "")
    }

    companion object {
        fun empty(today: LocalDate) = DishDraft(0, "", emptyList(), YieldMethod.WEIGHED, null, null, null, today.toString())

        fun edit(v: DishView, tare: Double?): DishDraft {
            val method = when (v.dish.yieldMethod) {
                YieldMethodCode.SWELL -> YieldMethod.SWELL
                YieldMethodCode.RAW_SUM -> YieldMethod.RAW_SUM
                else -> YieldMethod.WEIGHED
            }
            val gross = if (method == YieldMethod.WEIGHED) v.dish.yieldG + (tare ?: 0.0) else null
            return DishDraft(v.product.id, v.product.name, v.lines, method, gross, v.dish.cookwareId, v.dish.batchOf, v.dish.cookedOn)
        }

        /** Новая партия того же рецепта: те же продукты и граммы, вес готового — заново. */
        fun newBatch(v: DishView, today: LocalDate) = DishDraft(
            0, "${v.product.name.replace(BATCH_SUFFIX, "")} · ${today.format(DDMM)}", v.lines,
            if (v.dish.yieldMethod == YieldMethodCode.WEIGHED) YieldMethod.WEIGHED else YieldMethod.SWELL,
            null, v.dish.cookwareId, v.dish.batchOf ?: v.product.id, today.toString(),
        )
    }
}

/** Расчёт для экрана: вес готового и КБЖУ на 100 г по выбранному способу. */
data class DishCalc(
    val lines: List<DishLine>,
    val totals: Nutrients,
    val yieldGrams: Double?,
    val per100: Nutrients?,
    /** Для оценки по разбуханию: КБЖУ на 100 г по краям диапазона k (меньше вес — больше ккал). */
    val rangeKcal: Pair<Double, Double>?,
    val hasSwell: Boolean,
)

fun calc(d: DishDraft, tare: Double): DishCalc? {
    val lines = d.parsedLines() ?: return null
    val ings = lines.map { it.ingredient() }
    val totals = DishMath.totals(ings)
    val hasSwell = ings.any { it.swellK != null }
    val yieldG = when (d.method) {
        YieldMethod.WEIGHED -> parseDecimal(d.gross)?.let { g -> (g - tare).takeIf { it > 0 } }
        YieldMethod.SWELL -> DishMath.swellYield(ings)
        YieldMethod.RAW_SUM -> DishMath.rawSum(ings)
    }
    val per100 = yieldG?.let { DishMath.per100(totals, it) }
    val range = if (d.method == YieldMethod.SWELL && ings.any { it.swellKMin != null || it.swellKMax != null }) {
        val (lo, hi) = DishMath.swellYieldRange(ings)
        DishMath.per100(totals, hi).kcal to DishMath.per100(totals, lo).kcal
    } else null
    return DishCalc(lines, totals, yieldG, per100, range, hasSwell)
}

@Composable
fun DishEditorScreen(
    draft: DishDraft,
    cookware: List<CookwareEntity>,
    onAddIngredient: () -> Unit,
    onAddCookware: (name: String, tare: Double) -> Unit,
    onSave: () -> Unit,
    onNewBatch: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var addingCookware by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    val tare = cookware.firstOrNull { it.id == draft.cookwareId }?.tareG ?: 0.0
    val c = calc(draft, tare)
    val errors = buildList {
        if (draft.name.isBlank()) add("название")
        if (draft.lines.isEmpty()) add("ингредиенты")
        else if (c == null) add("граммы ингредиентов")
        if (c != null && c.yieldGrams == null) add(if (draft.method == YieldMethod.WEIGHED) "вес готового больше веса посуды" else "вес готового")
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Bg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BackTitle(if (draft.productId == 0L) "Новое блюдо" else "Блюдо", onBack) }
        item {
            OutlinedTextField(
                value = draft.name, onValueChange = { draft.name = it }, singleLine = true,
                label = { Text("Название *") }, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Card {
                SectionLabel("Ингредиенты — как взвешивал: сырыми или сухими", Palette.Muted)
                draft.lines.forEachIndexed { i, (p, g) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 14.sp, color = Palette.Text)
                            Text(
                                listOfNotNull(
                                    "на 100 г: ${fmt0(p.kcal100)} ккал",
                                    p.swellK?.let { "разбухает ×${fmtInput(it)}" },
                                    if (!p.verified) "не проверено" else null,
                                ).joinToString(" · "),
                                fontSize = 11.sp, color = if (p.verified) Palette.Muted else Palette.Hema,
                            )
                        }
                        OutlinedTextField(
                            value = g, onValueChange = { draft.lines[i] = p to it }, singleLine = true,
                            label = { Text("г") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(96.dp),
                        )
                        TextButton(onClick = { draft.lines.removeAt(i) }) { Text("✕", color = Palette.Muted) }
                    }
                }
                TextButton(onClick = onAddIngredient, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("+ Ингредиент", color = Palette.Run, fontSize = 13.sp)
                }
            }
        }
        if (c != null) item { TotalsTable(c) }
        item {
            Card {
                SectionLabel("Вес готового блюда", Palette.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Chip("Взвесить готовое", selected = draft.method == YieldMethod.WEIGHED) { draft.method = YieldMethod.WEIGHED }
                    Chip("По разбуханию", selected = draft.method == YieldMethod.SWELL) { draft.method = YieldMethod.SWELL }
                    Chip("Сырой вес", selected = draft.method == YieldMethod.RAW_SUM) { draft.method = YieldMethod.RAW_SUM }
                }
                when (draft.method) {
                    YieldMethod.WEIGHED -> {
                        Text("Посуда", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Chip("Без посуды", selected = draft.cookwareId == null) { draft.cookwareId = null }
                            cookware.forEach { k -> Chip("${k.name} · ${fmt0(k.tareG)} г", selected = draft.cookwareId == k.id) { draft.cookwareId = k.id } }
                            Chip("+ Посуда") { addingCookware = true }
                        }
                        OutlinedTextField(
                            value = draft.gross, onValueChange = { draft.gross = it }, singleLine = true,
                            label = { Text(if (draft.cookwareId == null) "Вес готового, г" else "Вес готового вместе с посудой, г") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text("Самый точный способ: учитывает и разбухание круп, и потерю воды мясом.", fontSize = 11.sp, color = Palette.Muted)
                    }
                    YieldMethod.SWELL -> Text(
                        if (c?.hasSwell == true) "Сырой вес плюс вода, которую впитали крупы и макароны: сухой вес × (k − 1). Потеря воды мясом и овощами не учтена — реальные значения на 100 г выше."
                        else "Ни у одного ингредиента нет коэффициента разбухания — получится то же, что сырой вес. Коэффициент задаётся в карточке крупы.",
                        fontSize = 12.sp, color = if (c?.hasSwell == true) Palette.Muted else Palette.Hema, modifier = Modifier.padding(top = 8.dp),
                    )
                    YieldMethod.RAW_SUM -> Text(
                        "Сумма ингредиентов как есть. Самый грубый способ: годится для блюд без круп, где вода почти не уходит.",
                        fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                c?.yieldGrams?.let {
                    Text("Вес готового: ${fmt0(it)} г", fontFamily = Fonts.Mono, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        val per100 = c?.per100
        if (per100 != null) {
            item {
                Card(accent = Palette.Body) {
                    SectionLabel(
                        "На 100 г готового",
                        Palette.Body,
                        right = when (draft.method) {
                            YieldMethod.WEIGHED -> "взвешено"
                            YieldMethod.SWELL -> "оценка"
                            YieldMethod.RAW_SUM -> "по сырому весу"
                        },
                    )
                    Text(
                        "${fmt0(per100.kcal)} ккал · Б ${fmt1(per100.protein)} · Ж ${fmt1(per100.fat)} · У ${fmt1(per100.carbs)}" +
                            (per100.fiber?.let { " · Кл ${fmt1(it)}" } ?: ""),
                        fontFamily = Fonts.Mono, fontSize = 15.sp, color = Palette.Text, modifier = Modifier.padding(top = 6.dp),
                    )
                    c.rangeKcal?.let { (lo, hi) ->
                        Text("При разбухании по краям диапазона: ${fmt0(lo)}–${fmt0(hi)} ккал на 100 г", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (c.lines.any { !it.product.verified }) {
                        Text("Есть непроверенные ингредиенты — блюдо будет помечено «не проверено».", fontSize = 12.sp, color = Palette.Hema, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (showErrors && errors.isNotEmpty()) {
            item { Text("Проверь: ${errors.joinToString(", ")}", fontSize = 13.sp, color = Palette.Thr) }
        }
        item {
            Button(
                onClick = { if (errors.isEmpty()) onSave() else showErrors = true },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Body, contentColor = Palette.Bg),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить блюдо") }
        }
        if (onNewBatch != null) {
            item {
                TextButton(onClick = onNewBatch, modifier = Modifier.fillMaxWidth()) {
                    Text("Новая партия этого блюда", color = Palette.Run, fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (addingCookware) CookwareDialog(onDismiss = { addingCookware = false }) { n, t -> addingCookware = false; onAddCookware(n, t) }
}

@Composable
private fun TotalsTable(c: DishCalc) {
    Card {
        SectionLabel("Итог по ингредиентам", Palette.Muted)
        Row(Modifier.padding(top = 6.dp)) {
            Cell("", 2.4f, header = true); Cell("г", 1f, header = true); Cell("ккал", 1.1f, header = true)
            Cell("Б", 0.9f, header = true); Cell("Ж", 0.9f, header = true); Cell("У", 0.9f, header = true)
        }
        c.lines.forEach { l ->
            val n = l.ingredient().per100.scaled(l.grams / 100)
            Row(Modifier.padding(top = 4.dp)) {
                Cell(l.product.name, 2.4f, left = true); Cell(fmt0(l.grams), 1f); Cell(fmt0(n.kcal), 1.1f)
                Cell(fmt0(n.protein), 0.9f); Cell(fmt0(n.fat), 0.9f); Cell(fmt0(n.carbs), 0.9f)
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Cell("Итого", 2.4f, left = true, bold = true); Cell(fmt0(c.lines.sumOf { it.grams }), 1f, bold = true)
            Cell(fmt0(c.totals.kcal), 1.1f, bold = true); Cell(fmt0(c.totals.protein), 0.9f, bold = true)
            Cell(fmt0(c.totals.fat), 0.9f, bold = true); Cell(fmt0(c.totals.carbs), 0.9f, bold = true)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, w: Float, header: Boolean = false, left: Boolean = false, bold: Boolean = false) {
    Text(
        text, maxLines = 2,
        fontFamily = if (left) Fonts.Plex else Fonts.Mono,
        fontSize = if (header) 10.sp else 12.sp,
        color = if (header) Palette.Muted else if (bold) Palette.Text else Palette.Text.copy(alpha = 0.85f),
        textAlign = if (left) TextAlign.Start else TextAlign.End,
        modifier = Modifier.weight(w),
    )
}

@Composable
private fun CookwareDialog(onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var tare by remember { mutableStateOf("") }
    val t = parseDecimal(tare)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Посуда", color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Название («кастрюля 3 л»)") })
                OutlinedTextField(
                    value = tare, onValueChange = { tare = it }, singleLine = true, label = { Text("Вес пустой, г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && t != null) onSave(name, t) }, enabled = name.isNotBlank() && t != null) {
                Text("Сохранить", color = if (name.isNotBlank() && t != null) Palette.Green else Palette.Faint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Palette.Muted) } },
    )
}
