package app.dayplan

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import app.dayplan.data.AppClock
import app.dayplan.data.ChecklistStore
import app.dayplan.data.DaySnapshot
import app.dayplan.data.PlanStore
import app.dayplan.ui.DayPlanTheme
import app.dayplan.ui.DayScreen
import app.dayplan.widget.PlanWidget
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Снимки экрана дня и виджета. По умолчанию — на тестовых данных.
 * Для проверки реального плана: PLAN_JSON=<путь к файлу> PLAN_DATES=2026-10-27,2026-10-28 ./gradlew recordRoborazziDebug
 */
private object Input {
    val text: String = System.getenv("PLAN_JSON")?.let { File(it).takeIf(File::exists)?.readText() }
        ?: javaClass.classLoader!!.getResource("fixture_plan.json")!!.readText()
    val dates: List<LocalDate> = (System.getenv("PLAN_DATES") ?: "2030-01-08,2030-01-09")
        .split(',').map { LocalDate.parse(it.trim()) }
    val out = File(System.getenv("SHOT_DIR") ?: "build/outputs/roborazzi").apply { mkdirs() }
}

private fun prepare(markFirstChecked: Boolean = true) {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    PlanStore(ctx).importText(Input.text)
    if (markFirstChecked) {
        Input.dates.forEach { d ->
            DaySnapshot.load(ctx, d).checks.firstOrNull()?.let { ChecklistStore(ctx).set(it.key, true) }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2600dp-xhdpi")
class DayScreenShots {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = prepare()

    @Test
    fun days() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val current = androidx.compose.runtime.mutableStateOf(Input.dates.first())
        compose.setContent {
            DayPlanTheme {
                DayScreen(
                    snapshot = DaySnapshot.load(ctx, current.value),
                    isToday = true,
                    plans = PlanStore(ctx).all(),
                    onPrev = {}, onNext = {}, onToday = {}, onImport = {}, onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }
        Input.dates.forEach { d ->
            current.value = d
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(Input.out, "screen_$d.png").path)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xxhdpi")
class WidgetShots {
    @Before
    fun setUp() = prepare()

    @Test
    fun widgets() = runBlocking {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val density = activity.resources.displayMetrics.density
        listOf(DpSize(330.dp, 420.dp), DpSize(250.dp, 180.dp)).forEach { size ->
            Input.dates.forEach { d ->
                AppClock.today = { d }
                val rv = PlanWidget().compose(activity, size = size, state = emptyPreferences())
                val w = (size.width.value * density).toInt()
                val h = (size.height.value * density).toInt()
                val host = FrameLayout(activity).apply { setBackgroundColor(0xFF2B2F36.toInt()) }
                val view: View = rv.apply(activity, host)
                RemoteListRender.fillLists(view, RemoteListRender.collectionItems(rv), activity)
                host.addView(view, FrameLayout.LayoutParams(w, h))
                activity.setContentView(host, FrameLayout.LayoutParams(w, h))
                host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                host.layout(0, 0, w, h)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                host.captureRoboImage(File(Input.out, "widget_${size.width.value.toInt()}x${size.height.value.toInt()}_$d.png").path)
            }
        }
        AppClock.today = { LocalDate.now() }
    }
}
