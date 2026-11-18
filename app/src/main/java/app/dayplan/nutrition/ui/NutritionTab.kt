package app.dayplan.nutrition.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import app.dayplan.nutrition.data.BarcodeLookup
import app.dayplan.nutrition.data.CookwareEntity
import app.dayplan.nutrition.data.DishInput
import app.dayplan.nutrition.data.DishRepo
import app.dayplan.nutrition.data.ProductKind
import app.dayplan.nutrition.data.TemplateView
import app.dayplan.nutrition.data.DayTargetsView
import app.dayplan.nutrition.data.LogEntryEntity
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionRepo
import app.dayplan.nutrition.data.OffClient
import app.dayplan.nutrition.data.OffProduct
import app.dayplan.nutrition.data.OffSearch
import app.dayplan.nutrition.data.RefProduct
import app.dayplan.nutrition.data.ReferenceDb
import app.dayplan.nutrition.data.ProductEntity
import app.dayplan.nutrition.data.ProductPortionEntity
import app.dayplan.nutrition.domain.Barcodes
import app.dayplan.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private sealed interface Route {
    data object Day : Route
    data class Add(val meal: String) : Route
    data class NotFound(val meal: String, val code: String, val offError: String?) : Route
    /** Привязка кода к карточке из своей базы. */
    data class Link(val meal: String, val code: String) : Route
    /** Редактор блюда: новое (productId = null), правка или новая партия (batchFrom — исходная партия). */
    data class Dish(val meal: String?, val productId: Long?, val back: Route, val batchFrom: Long? = null) : Route
    /** Выбор ингредиента для блюда. */
    data class PickIngredient(val dish: Dish) : Route
    /**
     * Карточка продукта: новая, правка или сверка карточки из Open Food Facts.
     * После новой карточки и сверки сразу открывается порция, если пришли из добавления (meal != null).
     */
    data class Form(
        val meal: String?,
        val mode: FormMode,
        val back: Route,
        val productId: Long? = null,
        val prefill: String = "",
        val barcode: String? = null,
        val off: OffProduct? = null,
        /** Карточка заводится для блюда: после сохранения продукт добавляется в его состав. */
        val forDish: Dish? = null,
    ) : Route
}

private data class Pick(val product: ProductEntity, val portions: List<ProductPortionEntity>, val meal: String)
private data class EditEntry(val entry: LogEntryEntity, val product: ProductEntity?, val portions: List<ProductPortionEntity>)

@Composable
fun NutritionTab(
    date: LocalDate,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember { NutritionRepo(NutritionDatabase.get(context)) { ReferenceDb.get(context) } }
    val off = remember { OffClient() }
    val dishRepo = remember { DishRepo(NutritionDatabase.get(context)) }
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf<Route>(Route.Day) }
    var version by remember { mutableIntStateOf(0) }
    var targets by remember { mutableStateOf<DayTargetsView?>(null) }
    var entries by remember { mutableStateOf<List<LogEntryEntity>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var refResults by remember { mutableStateOf<List<RefProduct>>(emptyList()) }
    var online by remember { mutableStateOf<OnlineState>(OnlineState.Idle) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var edit by remember { mutableStateOf<EditEntry?>(null) }
    var draft by remember { mutableStateOf<ProductDraft?>(null) }
    var draftRoute by remember { mutableStateOf<Route?>(null) }
    var owners by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var dishDraft by remember { mutableStateOf<DishDraft?>(null) }
    var dishKey by remember { mutableStateOf<Route.Dish?>(null) }
    var cookware by remember { mutableStateOf<List<CookwareEntity>>(emptyList()) }
    var templates by remember { mutableStateOf<List<TemplateView>>(emptyList()) }
    var templatePick by remember { mutableStateOf<Pair<TemplateView, String>?>(null) }
    var templateSave by remember { mutableStateOf<Pair<String, List<LogEntryEntity>>?>(null) }
    var askOil by remember { mutableStateOf<Route.Dish?>(null) }
    var reapply by remember { mutableStateOf<Pair<Long, Int>?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun io(block: suspend () -> Unit) = scope.launch {
        withContext(Dispatchers.IO) { block() }
        version++
    }

    LaunchedEffect(date, version) {
        val (t, e) = withContext(Dispatchers.IO) { repo.targets(date) to repo.entries(date) }
        targets = t
        entries = e
    }
    LaunchedEffect(query, version, route) {
        val r = route
        if (r is Route.Add || r is Route.Link || r is Route.PickIngredient) results = withContext(Dispatchers.IO) { repo.findProducts(query) }
        refResults = if ((r is Route.Add || r is Route.PickIngredient) && query.isNotBlank()) withContext(Dispatchers.IO) { repo.findReference(query) } else emptyList()
        if (r is Route.Add) templates = withContext(Dispatchers.IO) { dishRepo.templates() }
    }
    LaunchedEffect(route) {
        val r = route
        if (r is Route.Dish && dishKey != r) {
            val today = app.dayplan.data.AppClock.today()
            val (k, d) = withContext(Dispatchers.IO) {
                val k = dishRepo.cookware()
                val draftFor = when {
                    r.productId != null -> dishRepo.dish(r.productId)?.let { v -> DishDraft.edit(v, k.firstOrNull { it.id == v.dish.cookwareId }?.tareG) }
                    r.batchFrom != null -> dishRepo.dish(r.batchFrom)?.let { DishDraft.newBatch(it, today) }
                    else -> null
                }
                k to (draftFor ?: DishDraft.empty(today))
            }
            cookware = k
            dishDraft = d
            dishKey = r
        }
    }
    LaunchedEffect(query) { online = OnlineState.Idle }
    LaunchedEffect(route) {
        val r = route
        if (r is Route.Form) {
            draft = when {
                r.off != null -> ProductDraft.fromOff(r.off)
                r.productId != null -> withContext(Dispatchers.IO) {
                    val p = repo.product(r.productId)
                    ProductDraft(p, "").also { d ->
                        repo.portions(r.productId).forEach { d.portions.add(it.label to fmtInput(it.grams)) }
                        d.barcodes.addAll(repo.barcodes(r.productId))
                    }
                }
                else -> ProductDraft(null, r.prefill).also { d -> r.barcode?.let { d.barcodes.add(it) } }
            }
            draftRoute = r
        }
    }
    val codes = draft?.barcodes?.toList().orEmpty()
    LaunchedEffect(draft, codes) {
        val d = draft ?: return@LaunchedEffect
        owners = withContext(Dispatchers.IO) {
            codes.mapNotNull { c -> repo.barcodeOwner(c, exceptId = d.id)?.let { c to it.name } }.toMap()
        }
    }

    BackHandler(enabled = route != Route.Day) {
        route = when (val r = route) {
            is Route.Dish -> { dishDraft = null; dishKey = null; r.back }
            is Route.PickIngredient -> r.dish
            is Route.Form -> r.back
            is Route.Link -> Route.NotFound(r.meal, r.code, null)
            is Route.NotFound -> Route.Add(r.meal)
            else -> Route.Day
        }
    }

    fun openPick(p: ProductEntity, meal: String) = scope.launch {
        val portions = withContext(Dispatchers.IO) { repo.portions(p.id) }
        pick = Pick(p, portions, meal)
    }

    fun saveDish(r: Route.Dish, d: DishDraft, skipOil: Boolean = false) {
        if (!skipOil && !d.oilAsked && !d.hasOil()) {
            askOil = r
            return
        }
        scope.launch {
            val tare = cookware.firstOrNull { it.id == d.cookwareId }?.tareG ?: 0.0
            val c = calc(d, tare) ?: return@launch
            val y = c.yieldGrams ?: return@launch
            val input = DishInput(
                d.productId, d.name, c.lines, d.method, y,
                if (d.method == app.dayplan.nutrition.domain.YieldMethod.WEIGHED) d.cookwareId else null, d.batchOf, d.cookedOn,
            )
            val (id, entries, product) = withContext(Dispatchers.IO) {
                val id = dishRepo.save(input)
                Triple(id, if (d.productId != 0L) dishRepo.entriesOf(id).size else 0, repo.product(id)!!)
            }
            version++
            dishDraft = null
            dishKey = null
            if (entries > 0) reapply = id to entries
            val meal = r.meal
            if (meal != null && d.productId == 0L) {
                route = Route.Add(meal)
                openPick(product, meal)
            } else {
                route = r.back
            }
        }
    }

    fun addToDish(dish: Route.Dish, p: ProductEntity) {
        dishDraft?.add(p)
        route = dish
    }

    fun scanForIngredient(dish: Route.Dish) = Scanner.scan(
        context,
        onResult = { raw ->
            val code = raw?.let { Barcodes.normalize(it) } ?: return@scan
            scope.launch {
                busy = "Ищу $code…"
                val found = withContext(Dispatchers.IO) { repo.lookupBarcode(code) { off.fetch(it) } }
                busy = null
                when (found) {
                    is BarcodeLookup.Own -> addToDish(dish, found.product)
                    is BarcodeLookup.Off -> route = Route.Form(null, FormMode.CHECK_LABEL, Route.PickIngredient(dish), off = found.product, forDish = dish)
                    is BarcodeLookup.NotFound -> {
                        toast("Код не найден — заведи карточку")
                        route = Route.Form(null, FormMode.NEW, Route.PickIngredient(dish), barcode = found.code, forDish = dish)
                    }
                }
            }
        },
        onError = { toast(it) },
    )

    fun scanFor(meal: String) = Scanner.scan(
        context,
        onResult = { raw ->
            if (raw == null) return@scan
            val code = Barcodes.normalize(raw)
            if (code == null) {
                toast("Это не штрихкод товара")
                return@scan
            }
            scope.launch {
                busy = "Ищу $code…"
                val found = withContext(Dispatchers.IO) { repo.lookupBarcode(code) { off.fetch(it) } }
                busy = null
                when (found) {
                    is BarcodeLookup.Own -> {
                        route = Route.Add(meal)
                        openPick(found.product, meal)
                    }
                    is BarcodeLookup.Off -> route = Route.Form(meal, FormMode.CHECK_LABEL, Route.Add(meal), off = found.product)
                    is BarcodeLookup.NotFound -> route = Route.NotFound(meal, found.code, found.offError)
                }
            }
        },
        onError = { toast(it) },
    )

    when (val r = route) {
        Route.Day -> NutritionDayScreen(
            date = date, isToday = isToday, targets = targets, entries = entries,
            onPrev = onPrev, onNext = onNext, onToday = onToday,
            onAdd = { meal -> query = ""; route = Route.Add(meal) },
            onSaveTemplate = { meal, items -> templateSave = meal to items },
            onEntry = { e ->
                scope.launch {
                    val (p, portions) = withContext(Dispatchers.IO) {
                        val p = e.productId?.let { repo.product(it) }
                        p to (p?.let { repo.portions(it.id) } ?: emptyList())
                    }
                    edit = EditEntry(e, p, portions)
                }
            },
            modifier = modifier,
        )
        is Route.Add -> AddFoodScreen(
            title = "Добавить · ${r.meal}", query = query, onQuery = { query = it }, results = results,
            onPick = { openPick(it, r.meal) },
            onNewProduct = { route = Route.Form(r.meal, FormMode.NEW, r, prefill = query.trim()) },
            onBack = { route = Route.Day },
            onScan = { scanFor(r.meal) },
            refResults = refResults,
            onPickRef = { ref ->
                if (ref.adoptsDirectly) {
                    scope.launch {
                        val p = withContext(Dispatchers.IO) { repo.adoptReference(ref) }
                        version++
                        openPick(p, r.meal)
                    }
                } else {
                    route = Route.Form(r.meal, FormMode.CHECK_LABEL, r, off = ref.toOff())
                }
            },
            online = online,
            onSearchOnline = {
                val q = query
                online = OnlineState.Loading
                scope.launch {
                    val res = withContext(Dispatchers.IO) { off.search(q) }
                    if (q == query) {
                        online = when (res) {
                            is OffSearch.Found -> OnlineState.Results(res.products)
                            is OffSearch.Failed -> OnlineState.Failed(res.message)
                        }
                    }
                }
            },
            onPickOnline = { o -> route = Route.Form(r.meal, FormMode.CHECK_LABEL, r, off = o) },
            templates = templates,
            onTemplate = { templatePick = it to r.meal },
            onNewDish = { route = Route.Dish(r.meal, null, r) },
            modifier = modifier,
        )
        is Route.NotFound -> BarcodeNotFoundScreen(
            code = r.code, offError = r.offError,
            onLink = { query = ""; route = Route.Link(r.meal, r.code) },
            onNewProduct = { route = Route.Form(r.meal, FormMode.NEW, r, barcode = r.code) },
            onScanAgain = { scanFor(r.meal) },
            onBack = { route = Route.Add(r.meal) },
            modifier = modifier,
        )
        is Route.Link -> AddFoodScreen(
            title = "Привязать код",
            hint = "Выбери продукт — код ${r.code} добавится к его карточке. Если такого продукта ещё нет, заведи новую карточку.",
            query = query, onQuery = { query = it }, results = results,
            onPick = { p ->
                scope.launch {
                    withContext(Dispatchers.IO) { repo.linkBarcode(r.code, p.id) }
                    toast("Код привязан к «${p.name}»")
                    route = Route.Add(r.meal)
                    openPick(p, r.meal)
                }
            },
            onNewProduct = { route = Route.Form(r.meal, FormMode.NEW, r, prefill = query.trim(), barcode = r.code) },
            onBack = { route = Route.NotFound(r.meal, r.code, null) },
            modifier = modifier,
        )
        is Route.Dish -> dishDraft?.takeIf { dishKey == r }?.let { d ->
            DishEditorScreen(
                draft = d, cookware = cookware,
                onAddIngredient = { query = ""; route = Route.PickIngredient(r) },
                onAddCookware = { n, t ->
                    scope.launch {
                        val k = withContext(Dispatchers.IO) { dishRepo.addCookware(n, t); dishRepo.cookware() }
                        cookware = k
                        d.cookwareId = k.firstOrNull { it.name == n.trim() && it.tareG == t }?.id ?: d.cookwareId
                    }
                },
                onSave = { saveDish(r, d) },
                onNewBatch = if (d.productId != 0L) ({ route = Route.Dish(r.meal, null, r.back, batchFrom = d.productId) }) else null,
                onBack = { dishDraft = null; dishKey = null; route = r.back },
                modifier = modifier,
            )
        }
        is Route.PickIngredient -> AddFoodScreen(
            title = "Ингредиент", query = query, onQuery = { query = it },
            results = results.filter { it.id != r.dish.productId },
            onPick = { addToDish(r.dish, it) },
            onNewProduct = { route = Route.Form(null, FormMode.NEW, r, prefill = query.trim(), forDish = r.dish) },
            onBack = { route = r.dish },
            onScan = { scanForIngredient(r.dish) },
            refResults = refResults,
            onPickRef = { ref ->
                if (ref.adoptsDirectly) {
                    scope.launch {
                        val p = withContext(Dispatchers.IO) { repo.adoptReference(ref) }
                        version++
                        addToDish(r.dish, p)
                    }
                } else {
                    route = Route.Form(null, FormMode.CHECK_LABEL, r, off = ref.toOff(), forDish = r.dish)
                }
            },
            modifier = modifier,
        )
        is Route.Form -> draft?.takeIf { draftRoute == r }?.let { d ->
            ProductFormScreen(
                draft = d, mode = r.mode, owners = owners,
                onScan = {
                    Scanner.scan(
                        context,
                        onResult = { raw -> if (raw != null && d.addBarcode(raw) == null) toast("Это не штрихкод товара") },
                        onError = { toast(it) },
                    )
                },
                onSave = { verified ->
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            val id = repo.saveProduct(d.toEntity(verified), d.portionEntities(), d.barcodes.toList())
                            repo.product(id)!! to repo.portions(id)
                        }
                        version++
                        val meal = r.meal
                        if (r.forDish != null) {
                            addToDish(r.forDish, saved.first)
                        } else if (meal != null && (r.mode == FormMode.NEW || r.mode == FormMode.CHECK_LABEL)) {
                            route = Route.Add(meal)
                            pick = Pick(saved.first, saved.second, meal)
                        } else {
                            route = r.back
                        }
                    }
                },
                onBack = { route = r.back },
                modifier = modifier,
            )
        }
    }

    busy?.let { text ->
        AlertDialog(onDismissRequest = {}, confirmButton = {}, containerColor = Palette.Surface, text = { Text(text, color = Palette.Text) })
    }

    TemplateDialogs(
        templatePick = templatePick,
        onLog = { t, meal ->
            templatePick = null
            route = Route.Day
            io { t.lines.forEach { repo.addEntry(date, meal, it.product, it.grams) } }
        },
        onDelete = { t ->
            templatePick = null
            io { dishRepo.deleteTemplate(t.template.id) }
        },
        onDismissPick = { templatePick = null },
        templateSave = templateSave,
        onSave = { name, items ->
            templateSave = null
            io { dishRepo.saveTemplate(name, items) }
            toast("Шаблон «${name.trim()}» сохранён")
        },
        onDismissSave = { templateSave = null },
    )

    askOil?.let { r ->
        AlertDialog(
            onDismissRequest = { askOil = null },
            containerColor = Palette.Surface,
            title = { Text("Готовил на масле?", color = Palette.Text) },
            text = { Text("В составе нет масла. Если жарил или тушил на нём, добавь его ингредиентом: даже ложка заметно сдвигает жиры.", color = Palette.Text, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    askOil = null
                    dishDraft?.oilAsked = true
                    query = "масло"
                    route = Route.PickIngredient(r)
                }) { Text("Добавить масло", color = Palette.Green) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askOil = null
                    dishDraft?.let { it.oilAsked = true; saveDish(r, it, skipOil = true) }
                }) { Text("Без масла", color = Palette.Muted) }
            },
        )
    }

    reapply?.let { (id, n) ->
        AlertDialog(
            onDismissRequest = { reapply = null },
            containerColor = Palette.Surface,
            title = { Text("Пересчитать записи?", color = Palette.Text) },
            text = { Text("Этой партией уже записано $n раз(а). Пересчитать их по новому КБЖУ на 100 г?", color = Palette.Text, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { reapply = null; io { dishRepo.reapply(id) } }) { Text("Пересчитать", color = Palette.Green) } },
            dismissButton = { TextButton(onClick = { reapply = null }) { Text("Оставить как есть", color = Palette.Muted) } },
        )
    }

    pick?.let { p ->
        PortionDialog(
            product = p.product, portions = p.portions, initialGrams = null, confirmLabel = "Записать",
            onConfirm = { g ->
                pick = null
                route = Route.Day
                io { repo.addEntry(date, p.meal, p.product, g) }
            },
            onDismiss = { pick = null },
            onEditProduct = {
                pick = null
                route = if (p.product.kind == ProductKind.DISH) Route.Dish(p.meal, p.product.id, route)
                else Route.Form(p.meal, FormMode.EDIT, route, productId = p.product.id)
            },
        )
    }

    edit?.let { ed ->
        var meal by remember(ed) { mutableStateOf(ed.entry.meal) }
        val product = ed.product ?: ProductEntity(
            id = 0, name = ed.entry.productName,
            kcal100 = per100(ed.entry.kcal, ed.entry.grams), protein100 = per100(ed.entry.protein, ed.entry.grams),
            fat100 = per100(ed.entry.fat, ed.entry.grams), carbs100 = per100(ed.entry.carbs, ed.entry.grams),
            source = "", verified = ed.entry.productVerified, updatedAt = 0,
        )
        PortionDialog(
            product = product, portions = ed.portions, initialGrams = ed.entry.grams, confirmLabel = "Сохранить",
            onConfirm = { g ->
                edit = null
                io {
                    if (meal != ed.entry.meal) repo.moveEntry(ed.entry, meal)
                    repo.updateEntryGrams(ed.entry.copy(meal = meal), g)
                }
            },
            onDismiss = { edit = null },
            onDelete = {
                edit = null
                io { repo.deleteEntry(ed.entry) }
            },
            extra = {
                Column {
                    Text("Приём пищи", fontSize = 12.sp, color = Palette.Muted)
                    MealPicker(meal) { meal = it }
                }
            },
        )
    }
}

@Composable
private fun TemplateDialogs(
    templatePick: Pair<TemplateView, String>?,
    onLog: (TemplateView, String) -> Unit,
    onDelete: (TemplateView) -> Unit,
    onDismissPick: () -> Unit,
    templateSave: Pair<String, List<LogEntryEntity>>?,
    onSave: (String, List<LogEntryEntity>) -> Unit,
    onDismissSave: () -> Unit,
) {
    templatePick?.let { (t, meal) ->
        AlertDialog(
            onDismissRequest = onDismissPick,
            containerColor = Palette.Surface,
            title = { Text(t.template.name, color = Palette.Text) },
            text = {
                Column {
                    t.lines.forEach { Text("${it.product.name} · ${fmt0(it.grams)} г", fontSize = 13.sp, color = Palette.Text) }
                    Text("Итого ${fmt0(t.kcal)} ккал · в «$meal»", fontSize = 12.sp, color = Palette.Muted)
                    TextButton(onClick = { onDelete(t) }) { Text("Удалить шаблон", color = Palette.Thr, fontSize = 13.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { onLog(t, meal) }) { Text("Записать", color = Palette.Green) } },
            dismissButton = { TextButton(onClick = onDismissPick) { Text("Отмена", color = Palette.Muted) } },
        )
    }
    templateSave?.let { (meal, items) ->
        var name by remember(templateSave) { mutableStateOf(meal) }
        AlertDialog(
            onDismissRequest = onDismissSave,
            containerColor = Palette.Surface,
            title = { Text("Шаблон из «$meal»", color = Palette.Text) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Название") })
                    Text(items.filter { it.productId != null }.joinToString(", ") { "${it.productName} ${fmt0(it.grams)} г" }, fontSize = 12.sp, color = Palette.Muted)
                }
            },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name, items) }) { Text("Сохранить", color = Palette.Green) } },
            dismissButton = { TextButton(onClick = onDismissSave) { Text("Отмена", color = Palette.Muted) } },
        )
    }
}

private fun per100(value: Double, grams: Double) = if (grams > 0) value * 100 / grams else 0.0
