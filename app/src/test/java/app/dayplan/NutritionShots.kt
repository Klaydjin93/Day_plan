package app.dayplan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.dayplan.data.PlanParser
import app.dayplan.nutrition.data.DayTargetsView
import app.dayplan.nutrition.data.LogEntryEntity
import app.dayplan.nutrition.data.WeightEntryEntity
import app.dayplan.nutrition.data.WeightView
import app.dayplan.nutrition.domain.GoalsMapper
import app.dayplan.nutrition.domain.WeightPoint
import app.dayplan.nutrition.domain.Weights
import app.dayplan.nutrition.ui.NutritionDayScreen
import app.dayplan.nutrition.ui.WeightScreen
import app.dayplan.ui.BottomTabs
import app.dayplan.ui.DayPlanTheme
import app.dayplan.ui.Tab
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Снимки экранов «Питание» и «Вес» на тестовых данных: ./gradlew recordRoborazziDebug --tests 'app.dayplan.NutritionShots' */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1500dp-xhdpi")
class NutritionShots {
    @get:Rule
    val compose = createComposeRule()

    private val out = File(System.getenv("SHOT_DIR") ?: "build/outputs/roborazzi").apply { mkdirs() }
    private val plan = (PlanParser.parse(javaClass.classLoader!!.getResource("fixture_plan_nutrition.json")!!.readText()) as PlanParser.Result.Ok).plan
    private val date = LocalDate.parse("2030-02-09")

    private fun entry(meal: String, name: String, g: Double, k: Double, p: Double, f: Double, c: Double, verified: Boolean = true) =
        LogEntryEntity(date = date.toString(), meal = meal, productId = null, productName = name, grams = g, kcal = k, protein = p, fat = f, carbs = c, productVerified = verified, createdAt = 0)

    @Test
    fun nutritionDay() {
        val n = plan.days[0].nutrition!!
        val targets = DayTargetsView(n.type, GoalsMapper.from(n), n.notes, "2030-02-10", date.toString())
        val entries = listOf(
            entry("Завтрак", "Творог 5%", 200.0, 242.0, 34.0, 10.0, 6.0),
            entry("Завтрак", "Тортилья", 40.0, 120.0, 3.6, 3.2, 19.2),
            entry("Обед", "Плов · 18.11", 400.0, 436.0, 44.8, 18.0, 22.4),
            entry("Обед", "Йогурт греческий", 140.0, 92.0, 11.0, 2.8, 5.0, verified = false),
            entry("Перекус", "Банан", 120.0, 107.0, 1.3, 0.4, 25.9),
            entry("Ужин", "Индейка", 250.0, 325.0, 52.0, 12.0, 0.0),
            entry("Ужин", "Картофель (аэрогриль)", 300.0, 291.0, 7.5, 9.0, 60.0),
            entry("Ужин", "Сыр лёгкий", 40.0, 108.0, 10.0, 7.0, 0.0),
        )
        compose.setContent {
            DayPlanTheme {
                Column {
                    NutritionDayScreen(date, true, targets, entries, {}, {}, {}, {}, {}, Modifier.weight(1f))
                    BottomTabs(Tab.FOOD) {}
                }
            }
        }
        compose.onRoot().captureRoboImage(File(out, "nutrition_$date.png").path)
    }

    @Test
    fun weight() {
        val pts = listOf(-6L to 82.0, -4L to 82.6, -2L to 81.9, 0L to 82.2).map { (d, kg) -> WeightPoint(date.plusDays(d), kg) }
        val view = WeightView(
            today = WeightEntryEntity(date.toString(), 82.2, 0),
            rolling = Weights.rolling7(pts, date),
            week = Weights.week(pts, LocalDate.parse("2030-02-04")),
            previousWeek = Weights.week(pts, LocalDate.parse("2030-01-28")),
            latest = pts.reversed().map { WeightEntryEntity(it.date.toString(), it.kg, 0) },
        )
        compose.setContent {
            DayPlanTheme {
                Column {
                    WeightScreen(date, true, view, "Вес утром", {}, {}, {}, {}, {}, Modifier.weight(1f))
                    BottomTabs(Tab.WEIGHT) {}
                }
            }
        }
        compose.onRoot().captureRoboImage(File(out, "weight_$date.png").path)
    }

    @Test
    fun barcodeFlows() {
        val step = androidx.compose.runtime.mutableIntStateOf(0)
        val results = listOf(
            app.dayplan.nutrition.data.ProductEntity(id = 1, name = "Творог 5%", brand = "Савушкин", kcal100 = 121.0, protein100 = 17.0, fat100 = 5.0, carbs100 = 1.8, source = "manual", verified = true, updatedAt = 0),
            app.dayplan.nutrition.data.ProductEntity(id = 2, name = "Йогурт греческий", kcal100 = 66.0, protein100 = 8.0, fat100 = 2.0, carbs100 = 3.6, source = "off", verified = false, updatedAt = 0),
        )
        val off = app.dayplan.nutrition.data.OffProduct("4601234567890", "Творог мягкий 5%", "Простоквашино", 121.0, 16.0, 5.0, null, null, kcalFromKj = false)
        compose.setContent {
            DayPlanTheme {
                Column {
                    when (step.intValue) {
                        0 -> app.dayplan.nutrition.ui.BarcodeNotFoundScreen("4601234567890", null, {}, {}, {}, {}, Modifier.weight(1f))
                        1 -> app.dayplan.nutrition.ui.AddFoodScreen(
                            title = "Привязать код",
                            hint = "Выбери продукт — код 4601234567890 добавится к его карточке. Если такого продукта ещё нет, заведи новую карточку.",
                            query = "", onQuery = {}, results = results, onPick = {}, onNewProduct = {}, onBack = {}, modifier = Modifier.weight(1f),
                        )
                        else -> app.dayplan.nutrition.ui.ProductFormScreen(
                            draft = app.dayplan.nutrition.ui.ProductDraft.fromOff(off),
                            mode = app.dayplan.nutrition.ui.FormMode.CHECK_LABEL,
                            owners = mapOf("4601234567890" to "Творог 5%"),
                            onScan = {}, onSave = {}, onBack = {}, modifier = Modifier.weight(1f),
                        )
                    }
                    BottomTabs(Tab.FOOD) {}
                }
            }
        }
        listOf("notfound", "link", "checklabel").forEachIndexed { i, name ->
            step.intValue = i
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(out, "barcode_$name.png").path)
        }
    }

    @Test
    fun addWithReference() {
        val own = listOf(app.dayplan.nutrition.data.ProductEntity(id = 1, name = "Творог 5%", brand = "Савушкин", kcal100 = 121.0, protein100 = 17.0, fat100 = 5.0, carbs100 = 1.8, source = "manual", verified = true, updatedAt = 0))
        val refs = listOf(
            app.dayplan.nutrition.data.RefProduct("off", "4690228034428", "4690228034428", "Творог обезжиренный", "Простоквашино", 55.3, 10.5, 0.1, 3.0, null, false),
            app.dayplan.nutrition.data.RefProduct("off", "4602014004683", "4602014004683", "Творог 9%", "Домик в деревне", 160.0, 16.0, 9.0, 3.0, null, false),
        )
        val online = app.dayplan.nutrition.ui.OnlineState.Results(listOf(
            app.dayplan.nutrition.data.OffProduct("4627112680317", "Творог 5%", "Вкусвилл", 121.0, 16.0, 5.0, 3.0, null, false),
            app.dayplan.nutrition.data.OffProduct("4610001997115", "творог", null, null, null, null, null, null, false),
        ))
        compose.setContent {
            DayPlanTheme {
                Column {
                    app.dayplan.nutrition.ui.AddFoodScreen(
                        title = "Добавить · Завтрак", query = "творог", onQuery = {}, results = own, onPick = {}, onNewProduct = {}, onBack = {},
                        onScan = {}, refResults = refs, online = online, modifier = Modifier.weight(1f),
                    )
                    BottomTabs(Tab.FOOD) {}
                }
            }
        }
        compose.onRoot().captureRoboImage(File(out, "add_reference.png").path)
    }

    @Test
    fun dishEditor() {
        fun p(id: Long, name: String, k: Double, pr: Double, f: Double, c: Double, swell: Double? = null, verified: Boolean = true) =
            app.dayplan.nutrition.data.ProductEntity(id = id, name = name, kcal100 = k, protein100 = pr, fat100 = f, carbs100 = c,
                swellK = swell, swellKMin = swell?.let { 2.5 }, swellKMax = swell?.let { 3.0 }, source = "manual", verified = verified, updatedAt = 0)
        val lines = listOf(
            app.dayplan.nutrition.data.DishLine(p(1, "Куриная грудка", 110.0, 21.0, 2.5, 0.0), 797.0),
            app.dayplan.nutrition.data.DishLine(p(2, "Рис басмати", 340.0, 9.3, 0.0, 75.3, 2.8), 150.0),
            app.dayplan.nutrition.data.DishLine(p(3, "Морковь", 41.0, 1.0, 0.0, 9.7, verified = false), 195.0),
        )
        val pots = listOf(app.dayplan.nutrition.data.CookwareEntity(1, "Кастрюля 3 л", 900.0))
        val d = app.dayplan.nutrition.ui.DishDraft(0, "Грудка с рисом и морковью", lines, app.dayplan.nutrition.domain.YieldMethod.SWELL, 2312.0, 1, null, "2030-02-09")
        compose.setContent {
            DayPlanTheme {
                Column {
                    app.dayplan.nutrition.ui.DishEditorScreen(d, pots, {}, { _, _ -> }, {}, null, {}, Modifier.weight(1f))
                    BottomTabs(Tab.FOOD) {}
                }
            }
        }
        compose.onRoot().captureRoboImage(File(out, "dish_swell.png").path)
        compose.runOnIdle { d.method = app.dayplan.nutrition.domain.YieldMethod.WEIGHED }
        compose.onRoot().captureRoboImage(File(out, "dish_weighed.png").path)
    }
}
