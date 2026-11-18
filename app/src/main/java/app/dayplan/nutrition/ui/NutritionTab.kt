package app.dayplan.nutrition.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
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
        if (route is Route.Add || route is Route.Link) results = withContext(Dispatchers.IO) { repo.findProducts(query) }
        refResults = if (route is Route.Add && query.isNotBlank()) withContext(Dispatchers.IO) { repo.findReference(query) } else emptyList()
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
                if (ref.isUsda) {
                    scope.launch {
                        val p = withContext(Dispatchers.IO) { repo.adoptUsda(ref) }
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
                        if (meal != null && (r.mode == FormMode.NEW || r.mode == FormMode.CHECK_LABEL)) {
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
                route = Route.Form(p.meal, FormMode.EDIT, route, productId = p.product.id)
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

private fun per100(value: Double, grams: Double) = if (grams > 0) value * 100 / grams else 0.0
