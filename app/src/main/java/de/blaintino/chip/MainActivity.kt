package de.blaintino.chip

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.blaintino.chip.data.QuickAddCalendars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import de.blaintino.chip.ui.CalendarsScreen
import de.blaintino.chip.ui.ChipTheme
import de.blaintino.chip.ui.EventDetails
import de.blaintino.chip.ui.EventEditor
import de.blaintino.chip.ui.MonthScreen
import de.blaintino.chip.ui.WeekScreen
import de.blaintino.chip.ui.YearScreen
import de.blaintino.chip.ui.CalendarZoom
import de.blaintino.chip.ui.calendarGestures
import androidx.compose.foundation.layout.Box
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

    /** Kalender eines Widget-Plus-Knopfs: öffnet sofort „Neuer Termin“ in diesem Kalender. */
    private var pendingNewEvent by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            pendingWebcal = webcalFrom(intent)
            pendingNewEvent = NewEventIntents.calendarIdFrom(intent)
        }
        Background.schedule(this)
        // Widget bei jedem Start neu zeichnen (falls es nach einem Update hängen geblieben ist).
        lifecycleScope.launch { TodayWidget.refresh(applicationContext) }
        setContent {
            ChipTheme {
                ChipApp(
                    pendingWebcal,
                    onWebcalHandled = { pendingWebcal = null },
                    pendingNewEvent = pendingNewEvent,
                    onNewEventHandled = { pendingNewEvent = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        webcalFrom(intent)?.let { pendingWebcal = it }
        NewEventIntents.calendarIdFrom(intent)?.let { pendingNewEvent = it }
    }

    private fun webcalFrom(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
}

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipApp(
    pendingWebcal: String?,
    onWebcalHandled: () -> Unit,
    pendingNewEvent: Long?,
    onNewEventHandled: () -> Unit,
) {
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

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var zoomMenu by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableStateOf(CalendarZoom.WEEK) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var hidden by remember { mutableStateOf(HiddenCalendars.get(context)) }
    var version by remember { mutableIntStateOf(0) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var editing by remember { mutableStateOf<EventDraft?>(null) }
    var details by remember { mutableStateOf<EventInstance?>(null) }
    var quickIds by remember { mutableStateOf(QuickAddCalendars.get(context) ?: emptyList()) }

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
    LaunchedEffect(version) {
        calendars = withContext(Dispatchers.IO) { repo.calendars() }
        // Beim ersten Start die Plus-Knöpfe nach Namen („Langners“, „Arbeit“) vorbelegen.
        if (QuickAddCalendars.get(context) == null && calendars.isNotEmpty()) {
            quickIds = QuickAddCalendars.defaults(calendars)
            QuickAddCalendars.set(context, quickIds)
        }
    }
    // Abos, die länger als einen Tag nicht geladen wurden, beim Öffnen nachholen.
    LaunchedEffect(Unit) {
        val stale = withContext(Dispatchers.IO) {
            SubscriptionStore.all(context).any { System.currentTimeMillis() - it.lastSync > DAY_MS }
        }
        if (stale) Background.syncNow(context)
    }
    LaunchedEffect(pendingWebcal) { if (pendingWebcal != null) showSettings = true }

    fun openEvent(ev: EventInstance) {
        scope.launch {
            val draft = withContext(Dispatchers.IO) { repo.event(ev.eventId) }
            val cal = calendars.firstOrNull { it.id == ev.calendarId }
            if (draft != null && cal != null && cal.writable && !draft.recurring) editing = draft else details = ev
        }
    }

    // Im Editor wählbar: beschreibbar und in den Einstellungen eingeschaltet (Wunsch des
    // Nutzers). Beim Bearbeiten bleibt der bisherige Kalender des Termins immer wählbar.
    fun editorCalendars(currentId: Long?) =
        calendars.filter { it.writable && it.accountName != SubscriptionSync.ACCOUNT_NAME && (it.id !in hidden || it.id == currentId) }

    /**
     * Neuer Termin. Ohne [at] (Plus-Knopf) heute zur nächsten vollen Stunde (Wunsch des Nutzers),
     * mit [at] (Tippen ins Raster) dort. [calendarId] kommt von einem Schnellauswahl-Plus.
     */
    fun newEvent(at: LocalDateTime? = null, calendarId: Long? = null) {
        val choices = editorCalendars(null)
        // Bevorzugt der Hauptkalender eines Kontos (Name = Kontoname, bei Google die Mailadresse).
        val cal = calendarId?.let { id -> calendars.firstOrNull { it.id == id && it.writable } }
            ?: choices.firstOrNull { it.name == it.accountName } ?: choices.firstOrNull()
        if (cal == null) {
            Toast.makeText(context, "Kein beschreibbarer Kalender eingeschaltet (Einstellungen ⚙)", Toast.LENGTH_LONG).show()
            return
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val start = at ?: now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
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

    // Einstellungen (früher Tab „Kalender“) als eigener Bildschirm über das Zahnrad; Zurück schließt ihn.
    BackHandler(enabled = showSettings) { showSettings = false }

    // Widget-Plus: sobald die Kalender geladen sind, „Neuer Termin“ im gewählten Kalender öffnen.
    LaunchedEffect(pendingNewEvent, calendars) {
        val id = pendingNewEvent ?: return@LaunchedEffect
        if (calendars.isEmpty()) return@LaunchedEffect
        showSettings = false
        newEvent(calendarId = id)
        onNewEventHandled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showSettings) TextButton(onClick = { showSettings = false }) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
                title = {
                    if (showSettings) {
                        Text("Einstellungen")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Titel = aktuelle Ansicht; Antippen öffnet die Auswahl (Fallback zur Pinch-Geste).
                            Box {
                                TextButton(onClick = { zoomMenu = true }) {
                                    Text("${zoom.label} ▾", style = MaterialTheme.typography.titleLarge)
                                }
                                DropdownMenu(expanded = zoomMenu, onDismissRequest = { zoomMenu = false }) {
                                    CalendarZoom.entries.forEach { z ->
                                        DropdownMenuItem(
                                            text = { Text(z.label, fontWeight = if (z == zoom) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                zoom = z
                                                zoomMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            // Springt in der aktuellen Ansicht zu heute (Wunsch des Nutzers).
                            OutlinedButton(
                                onClick = { selectedDay = LocalDate.now() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp),
                            ) { Text("Heute", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        TextButton(onClick = { showSettings = true }) { Text("⚙", style = MaterialTheme.typography.headlineSmall) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!showSettings) {
                // Ein Plus je Schnellauswahl-Kalender in dessen Farbe; ohne Auswahl ein einfaches Plus.
                val quick = quickIds.mapNotNull { id -> calendars.firstOrNull { it.id == id && it.writable } }
                if (quick.isEmpty()) {
                    FloatingActionButton(onClick = { newEvent() }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                } else {
                    // Nebeneinander, beschriftet mit den ersten drei Buchstaben (Wunsch des Nutzers).
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        quick.forEach { cal ->
                            val bg = Color(cal.color)
                            ExtendedFloatingActionButton(
                                onClick = { newEvent(calendarId = cal.id) },
                                containerColor = bg,
                                contentColor = if (bg.luminance() > 0.5f) Color.Black else Color.White,
                            ) { Text("+ ${cal.name.trim().take(3)}") }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (showSettings) {
            CalendarsScreen(
                repo = repo,
                version = version,
                hidden = hidden,
                onHiddenChange = {
                    hidden = it
                    HiddenCalendars.set(context, it)
                    scope.launch { TodayWidget.refresh(context) }
                },
                onColorsChanged = {
                    version++
                    scope.launch { TodayWidget.refresh(context) }
                },
                quickIds = quickIds,
                onQuickIdsChange = {
                    quickIds = it
                    QuickAddCalendars.set(context, it)
                },
                prefillUrl = pendingWebcal,
                onPrefillHandled = onWebcalHandled,
                contentPadding = padding,
            )
        } else {
            // Zwei Finger aufziehen = feiner (Jahr → Monat → Woche → Tag), zusammenschieben = gröber;
            // ein Finger waagerecht wischen = vorheriger/nächster Zeitraum.
            Box(
                Modifier.fillMaxSize().calendarGestures(
                    onZoomIn = { zoom = zoom.zoomIn() },
                    onZoomOut = { zoom = zoom.zoomOut() },
                    onSwipe = { selectedDay = zoom.shift(selectedDay, it) },
                ),
            ) {
                when (zoom) {
                    CalendarZoom.DAY, CalendarZoom.WEEK -> WeekScreen(
                        repo = repo,
                        dayCount = if (zoom == CalendarZoom.DAY) 1 else 7,
                        version = version,
                        hidden = hidden,
                        selected = selectedDay,
                        onSelect = { selectedDay = it },
                        contentPadding = padding,
                        onEventClick = ::openEvent,
                        onCreateAt = { newEvent(it) },
                    )
                    CalendarZoom.MONTH -> MonthScreen(
                        repo, version, hidden, selectedDay,
                        onSelect = { selectedDay = it },
                        contentPadding = padding,
                        onEventClick = ::openEvent,
                        onCreateOn = { day ->
                            val now = LocalDateTime.now()
                            newEvent(
                                at = if (day == now.toLocalDate()) now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                                else day.atTime(9, 0),
                            )
                        },
                    )
                    CalendarZoom.YEAR -> YearScreen(
                        repo = repo,
                        version = version,
                        hidden = hidden,
                        selected = selectedDay,
                        onSelect = { selectedDay = it },
                        onOpenMonth = {
                            selectedDay = it
                            zoom = CalendarZoom.MONTH
                        },
                        contentPadding = padding,
                    )
                }
            }
        }
    }

    editing?.let { draft ->
        EventEditor(
            draft = draft,
            calendars = editorCalendars(draft.calendarId),
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
