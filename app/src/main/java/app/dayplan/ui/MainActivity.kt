package app.dayplan.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.dayplan.data.AppClock
import app.dayplan.data.ChecklistStore
import app.dayplan.data.DaySnapshot
import app.dayplan.data.PlanStore
import app.dayplan.data.StoredPlan
import app.dayplan.system.MidnightScheduler
import app.dayplan.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val message = mutableStateOf<String?>(null)
    private val dataVersion = mutableIntStateOf(0)
    private val requestedDate = mutableStateOf<LocalDate?>(null)
    private val resumeTick = mutableIntStateOf(0)

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { DayPlanTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::importUri)
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> importUri(stream)
                    text != null -> importText(text)
                }
            }
        }
        // повторный приход того же интента (поворот экрана) не должен импортировать дважды
        intent?.action = Intent.ACTION_MAIN
    }

    private fun importUri(uri: Uri) = runImport { it.importUri(uri) }
    private fun importText(text: String) = runImport { it.importText(text) }

    private fun runImport(block: (PlanStore) -> PlanStore.ImportResult) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { block(PlanStore(this@MainActivity)) }
            when (result) {
                is PlanStore.ImportResult.Error -> message.value = result.message
                is PlanStore.ImportResult.Ok -> {
                    val w = result.stored.plan.week
                    val verb = if (result.replaced) "Обновлён" else "Загружен"
                    message.value = "$verb план ${listOfNotNull(w.id, w.planVersion).joinToString(" ")}"
                    val today = AppClock.today()
                    val start = LocalDate.parse(w.start)
                    val end = LocalDate.parse(w.end)
                    requestedDate.value = if (!today.isBefore(start) && !today.isAfter(end)) today else start
                    dataVersion.intValue++
                    WidgetRefresher.refreshAll(applicationContext)
                    MidnightScheduler.schedule(applicationContext)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        var date by remember { mutableStateOf(AppClock.today()) }
        var followToday by remember { mutableStateOf(true) }
        var plans by remember { mutableStateOf<List<StoredPlan>>(emptyList()) }
        var snapshot by remember { mutableStateOf<DaySnapshot?>(null) }
        val snackbar = remember { SnackbarHostState() }

        val requested = requestedDate.value
        LaunchedEffect(requested) {
            if (requested != null) {
                date = requested
                followToday = requested == AppClock.today()
                requestedDate.value = null
            }
        }
        // приложение могли оставить открытым через полночь
        LaunchedEffect(resumeTick.intValue) {
            if (followToday) date = AppClock.today()
        }
        LaunchedEffect(date, dataVersion.intValue, resumeTick.intValue) {
            val loaded = withContext(Dispatchers.IO) {
                val all = PlanStore(this@MainActivity).all()
                all to DaySnapshot.load(this@MainActivity, date, all)
            }
            plans = loaded.first
            snapshot = loaded.second
        }
        LaunchedEffect(message.value) {
            message.value?.let {
                snackbar.showSnackbar(it)
                message.value = null
            }
        }

        fun go(d: LocalDate) {
            date = d
            followToday = d == AppClock.today()
        }

        Scaffold(containerColor = Palette.Bg, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            DayScreen(
                snapshot = snapshot,
                isToday = date == AppClock.today(),
                plans = plans,
                onPrev = { go(date.minusDays(1)) },
                onNext = { go(date.plusDays(1)) },
                onToday = { go(AppClock.today()) },
                onImport = { openDocument.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")) },
                onToggle = { key, checked ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { ChecklistStore(this@MainActivity).set(key, checked) }
                        dataVersion.intValue++
                        WidgetRefresher.refreshAll(applicationContext)
                    }
                },
                onDelete = { file ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { PlanStore(this@MainActivity).delete(file) }
                        dataVersion.intValue++
                        WidgetRefresher.refreshAll(applicationContext)
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}
