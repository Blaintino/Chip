package de.blaintino.chip

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.blaintino.chip.data.CalendarInfo
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.EventDraft
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.HiddenCalendars
import de.blaintino.chip.data.SubscriptionStore
import de.blaintino.chip.sync.Background
import de.blaintino.chip.sync.SubscriptionSync
import de.blaintino.chip.ui.AgendaScreen
import de.blaintino.chip.ui.CalendarsScreen
import de.blaintino.chip.ui.ChipTheme
import de.blaintino.chip.ui.EventDetails
import de.blaintino.chip.ui.EventEditor
import de.blaintino.chip.ui.MonthScreen
import de.blaintino.chip.widget.TodayWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    /** webcal://-Link, mit dem die App geöffnet wurde (öffnet den Abo-Dialog). */
    private var pendingWebcal by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingWebcal = webcalFrom(intent)
        Background.schedule(this)
        setContent {
            ChipTheme {
                ChipApp(pendingWebcal, onWebcalHandled = { pendingWebcal = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        webcalFrom(intent)?.let { pendingWebcal = it }
    }

    private fun webcalFrom(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
}

private const val TAB_AGENDA = 0
private const val TAB_MONTH = 1
private const val TAB_CALENDARS = 2
private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipApp(pendingWebcal: String?, onWebcalHandled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { CalendarRepository(context) }
    var granted by remember { mutableStateOf(repo.hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = repo.hasPermission()
        if (granted) {
            Background.syncNow(context)
            scope.launch { TodayWidget.refresh(context) }
        }
    }
    val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(permissions) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Chip braucht Zugriff auf deine Kalender", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                "Damit werden Google-Kalender (auch geteilte) angezeigt und ICS-Abos als eigene Kalender angelegt. Die Daten bleiben auf dem Handy.",
                textAlign = TextAlign.Center,
            )
            Button(onClick = { permissionLauncher.launch(permissions) }) { Text("Zugriff erlauben") }
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(TAB_AGENDA) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var hidden by remember { mutableStateOf(HiddenCalendars.get(context)) }
    var version by remember { mutableIntStateOf(0) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var editing by remember { mutableStateOf<EventDraft?>(null) }
    var details by remember { mutableStateOf<EventInstance?>(null) }

    // Jede Änderung in der Kalenderdatenbank (Google-Sync, Abo, andere Apps) lädt die Ansicht neu.
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(version) { calendars = withContext(Dispatchers.IO) { repo.calendars() } }
    // Abos, die länger als einen Tag nicht geladen wurden, beim Öffnen nachholen.
    LaunchedEffect(Unit) {
        val stale = withContext(Dispatchers.IO) {
            SubscriptionStore.all(context).any { System.currentTimeMillis() - it.lastSync > DAY_MS }
        }
        if (stale) Background.syncNow(context)
    }
    LaunchedEffect(pendingWebcal) { if (pendingWebcal != null) tab = TAB_CALENDARS }

    fun openEvent(ev: EventInstance) {
        scope.launch {
            val draft = withContext(Dispatchers.IO) { repo.event(ev.eventId) }
            val cal = calendars.firstOrNull { it.id == ev.calendarId }
            if (draft != null && cal != null && cal.writable && !draft.recurring) editing = draft else details = ev
        }
    }

    fun newEvent() {
        val writable = calendars.filter { it.writable && it.accountName != SubscriptionSync.ACCOUNT_NAME && it.id !in hidden }
            .ifEmpty { calendars.filter { it.writable && it.accountName != SubscriptionSync.ACCOUNT_NAME } }
        // Bevorzugt der Hauptkalender eines Kontos (Name = Kontoname, bei Google die Mailadresse).
        val cal = writable.firstOrNull { it.name == it.accountName } ?: writable.firstOrNull() ?: return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val start = if (selectedDay == now.toLocalDate()) now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        else selectedDay.atTime(9, 0)
        editing = EventDraft(
            id = null,
            calendarId = cal.id,
            title = "",
            location = "",
            description = "",
            allDay = false,
            start = start.atZone(zone).toInstant().toEpochMilli(),
            end = start.plusHours(1).atZone(zone).toInstant().toEpochMilli(),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            TAB_AGENDA -> "Termine"
                            TAB_MONTH -> "Monat"
                            else -> "Kalender & Abos"
                        },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == TAB_AGENDA, onClick = { tab = TAB_AGENDA }, icon = { Text("☰") }, label = { Text("Termine") })
                NavigationBarItem(selected = tab == TAB_MONTH, onClick = { tab = TAB_MONTH }, icon = { Text("▦") }, label = { Text("Monat") })
                NavigationBarItem(selected = tab == TAB_CALENDARS, onClick = { tab = TAB_CALENDARS }, icon = { Text("◉") }, label = { Text("Kalender") })
            }
        },
        floatingActionButton = {
            if (tab != TAB_CALENDARS) {
                FloatingActionButton(onClick = { newEvent() }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            }
        },
    ) { padding ->
        when (tab) {
            TAB_AGENDA -> AgendaScreen(repo, version, hidden, padding, onEventClick = ::openEvent)
            TAB_MONTH -> MonthScreen(
                repo, version, hidden, selectedDay,
                onSelect = { selectedDay = it },
                contentPadding = padding,
                onEventClick = ::openEvent,
            )
            else -> CalendarsScreen(
                repo = repo,
                version = version,
                hidden = hidden,
                onHiddenChange = {
                    hidden = it
                    HiddenCalendars.set(context, it)
                    scope.launch { TodayWidget.refresh(context) }
                },
                prefillUrl = pendingWebcal,
                onPrefillHandled = onWebcalHandled,
                contentPadding = padding,
            )
        }
    }

    editing?.let { draft ->
        EventEditor(
            draft = draft,
            calendars = calendars.filter { it.writable && it.accountName != SubscriptionSync.ACCOUNT_NAME },
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { repo.save(updated) }
                    version++
                    TodayWidget.refresh(context)
                }
            },
            onDelete = draft.id?.let { id ->
                {
                    editing = null
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.delete(id) }
                        version++
                        TodayWidget.refresh(context)
                    }
                }
            },
        )
    }
    details?.let { ev ->
        EventDetails(ev, calendars.firstOrNull { it.id == ev.calendarId }, onDismiss = { details = null })
    }
}
