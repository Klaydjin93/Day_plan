package app.dayplan.nutrition.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
import app.dayplan.nutrition.data.DayTargetsView
import app.dayplan.nutrition.data.LogEntryEntity
import app.dayplan.nutrition.data.NutritionDatabase
import app.dayplan.nutrition.data.NutritionRepo
import app.dayplan.nutrition.data.ProductEntity
import app.dayplan.nutrition.data.ProductPortionEntity
import app.dayplan.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private sealed interface Route {
    data object Day : Route
    data class Add(val meal: String) : Route
    /** Карточка продукта: новая (productId = null) или правка. После новой — сразу порция, если пришли из добавления. */
    data class Form(val meal: String?, val productId: Long?, val prefill: String, val back: Route) : Route
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
    val repo = remember { NutritionRepo(NutritionDatabase.get(context)) }
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf<Route>(Route.Day) }
    var version by remember { mutableIntStateOf(0) }
    var targets by remember { mutableStateOf<DayTargetsView?>(null) }
    var entries by remember { mutableStateOf<List<LogEntryEntity>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var edit by remember { mutableStateOf<EditEntry?>(null) }
    var draft by remember { mutableStateOf<ProductDraft?>(null) }
    var draftRoute by remember { mutableStateOf<Route?>(null) }

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
        if (route is Route.Add) results = withContext(Dispatchers.IO) { repo.findProducts(query) }
    }
    LaunchedEffect(route) {
        val r = route
        if (r is Route.Form) {
            val (p, portions) = withContext(Dispatchers.IO) {
                val p = r.productId?.let { repo.product(it) }
                p to (p?.let { repo.portions(it.id) } ?: emptyList())
            }
            draft = ProductDraft(p, r.prefill).also { d -> portions.forEach { d.portions.add(it.label to fmtInput(it.grams)) } }
            draftRoute = r
        }
    }

    BackHandler(enabled = route != Route.Day) {
        route = when (val r = route) {
            is Route.Form -> r.back
            else -> Route.Day
        }
    }

    fun openPick(p: ProductEntity, meal: String) = scope.launch {
        val portions = withContext(Dispatchers.IO) { repo.portions(p.id) }
        pick = Pick(p, portions, meal)
    }

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
            meal = r.meal, query = query, onQuery = { query = it }, results = results,
            onPick = { openPick(it, r.meal) },
            onNewProduct = { route = Route.Form(r.meal, null, query.trim(), r) },
            onBack = { route = Route.Day },
            modifier = modifier,
        )
        is Route.Form -> draft?.takeIf { draftRoute == r }?.let { d ->
            ProductFormScreen(
                draft = d,
                onSave = {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            val id = repo.saveProduct(d.toEntity(), d.portionEntities())
                            repo.product(id)!! to repo.portions(id)
                        }
                        version++
                        route = r.back
                        if (r.productId == null && r.meal != null) pick = Pick(saved.first, saved.second, r.meal)
                    }
                },
                onBack = { route = r.back },
                modifier = modifier,
            )
        }
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
                route = Route.Form(p.meal, p.product.id, "", route)
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
