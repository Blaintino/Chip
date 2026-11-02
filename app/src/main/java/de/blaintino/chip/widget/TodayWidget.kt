package de.blaintino.chip.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.blaintino.chip.MainActivity
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.Formats
import de.blaintino.chip.data.HiddenCalendars
import de.blaintino.chip.data.QuickAddCalendars
import de.blaintino.chip.weather.Weather
import de.blaintino.chip.weather.WeatherText
import java.time.LocalDateTime
import de.blaintino.chip.NewEventIntents
import de.blaintino.chip.QuaelgeistActivity
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.luminance
import de.blaintino.chip.sync.Background
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Widget „Heute“: scrollbare Liste der heutigen Termine mit Uhrzeit, Titel und Ort. */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetStatus.record(context, "Widget gestartet")
        provideContent {
            // Laden innerhalb der Komposition: Glance zeigt sofort eigenen Inhalt (statt der
            // Glance-Ladeanzeige), und ein Fehler beim Laden erscheint als Text im Widget.
            // Läuft die Sitzung schon, ruft Glance provideGlance bei einem Update nicht erneut auf;
            // deshalb lädt ein Zähler (refresh) die Daten innerhalb der laufenden Sitzung neu.
            val tick by refreshTick.collectAsState()
            var today by remember { mutableStateOf(LocalDate.now()) }
            // Mit dem letzten Inhalt starten (kein Flackern durch einen Ladezustand).
            var state by remember { mutableStateOf<WidgetData>(WidgetCache.load(context) ?: WidgetData.Loading) }
            LaunchedEffect(tick) {
                val day = LocalDate.now()
                val fresh = withContext(Dispatchers.IO) { load(context, day) }
                if (fresh is WidgetData.Loaded) WidgetCache.save(context, fresh)
                state = fresh
                today = day
            }
            GlanceTheme {
                WidgetContent(today, state)
            }
        }
    }

    private fun load(context: Context, today: LocalDate): WidgetData = try {
        val repo = CalendarRepository(context)
        if (!repo.hasPermission()) {
            WidgetStatus.record(context, "Kein Kalenderzugriff")
            WidgetData.NoPermission
        } else {
            val now = System.currentTimeMillis()
            // Nur laufende und kommende Termine; zum nächsten Terminende erneut zeichnen.
            val events = WidgetFilter.upcoming(repo.instances(today, today, HiddenCalendars.get(context)), now)
            Background.scheduleWidgetRefreshAt(context, WidgetFilter.nextChange(events, now))
            val calendars = repo.calendars()
            val quick = (QuickAddCalendars.get(context) ?: emptyList()).mapNotNull { id ->
                calendars.firstOrNull { it.id == id && it.writable }?.let { QuickButton(it.id, it.name, it.color) }
            }
            // Wetterzeile (Wunsch des Nutzers): Höchst-/Tiefstwert und Regenhinweis für den Rest des Tages.
            val weather = Weather.today(context, allowFetch = true)?.let { WeatherText.line(it, LocalDateTime.now()) }
            WidgetStatus.record(
                context,
                "${events.size} anstehende Termine geladen, Wetter: " +
                    (weather ?: if (Weather.place(context) == null) "aus" else "keine Daten"),
            )
            WidgetData.Loaded(events, quick, weather)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Widget konnte nicht laden", e)
        WidgetStatus.record(context, "Fehler: ${e.javaClass.simpleName}: ${e.message}")
        WidgetData.Failed(e.message ?: e.javaClass.simpleName)
    }

    companion object {
        private const val TAG = "ChipWidget"
        private val refreshTick = MutableStateFlow(0)

        suspend fun refresh(context: Context) {
            refreshTick.value++
            try {
                TodayWidget().updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Widget-Update fehlgeschlagen", e)
                WidgetStatus.record(context, "Update fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}

internal data class QuickButton(val calendarId: Long, val name: String, val color: Int)

internal sealed interface WidgetData {
    data object Loading : WidgetData
    data object NoPermission : WidgetData
    data class Loaded(val events: List<EventInstance>, val quick: List<QuickButton>, val weather: String?) : WidgetData
    data class Failed(val message: String) : WidgetData
}

/** Letzter Stand des Widgets (Zeit + Text), angezeigt in den Einstellungen (⚙) zur Fehlersuche ohne adb. */
object WidgetStatus {
    private const val PREFS = "widget"

    fun record(context: Context, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("time", System.currentTimeMillis()).putString("message", message).commit()
    }

    /** (Zeit, Text) oder null, wenn das Widget noch nie gezeichnet wurde. */
    fun last(context: Context): Pair<Long, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val msg = prefs.getString("message", null) ?: return null
        return prefs.getLong("time", 0) to msg
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Background.schedule(context)
    }
}

// Fester schwarzer Hintergrund (Wunsch des Nutzers), unabhängig vom Systemdesign.
private val BG = Color.Black
private val TEXT = ColorProvider(Color.White)
private val TEXT_DIM = ColorProvider(Color(0xFFB0B0B0))

@Composable
private fun WidgetContent(today: LocalDate, state: WidgetData) {
    // Widget selbst durchsichtig; nur der Terminbereich ist schwarz. Ohne anstehende Termine bleibt nur
    // die Knopfreihe sichtbar (Wunsch des Nutzers: Größe kann ein Widget nicht selbst ändern).
    // Keine Kopfzeile: Datum steht schon auf dem Startbildschirm, das Widget zeigt nur heute.
    // Ohne anstehende Termine stehen die Knöpfe oben (Wunsch des Nutzers), sonst unter den Terminen.
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Ohne Termine – und solange noch nichts geladen ist – keine schwarze Fläche.
        val empty = (state is WidgetData.Loaded && state.events.isEmpty()) || state is WidgetData.Loading
        val quick = (state as? WidgetData.Loaded)?.quick.orEmpty()
        val weather = (state as? WidgetData.Loaded)?.weather
        if (empty) {
            // Ohne Termine: Wetter als eigene kleine Fläche über den Knöpfen.
            if (weather != null) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().background(BG).cornerRadius(16.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { WeatherLine(weather) }
                Spacer(GlanceModifier.height(6.dp))
            }
            ButtonRow(quick)
        } else {
            Box(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth().background(BG).cornerRadius(16.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                when (state) {
                    WidgetData.Loading -> Unit
                    WidgetData.NoPermission -> Hint("Kalenderzugriff in Chip erlauben")
                    is WidgetData.Failed -> Hint("Fehler: ${state.message}")
                    is WidgetData.Loaded -> Column(modifier = GlanceModifier.fillMaxSize()) {
                        if (weather != null) {
                            WeatherLine(weather)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                        // Position als ID: eindeutig, auch wenn ein Termin mehrfach vorkommt.
                        val indexed = state.events.withIndex().toList()
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(indexed, itemId = { it.index.toLong() }) { item ->
                                EventLine(item.value, today)
                            }
                        }
                    }
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            ButtonRow(quick)
        }
    }
}

/** Plus-Knöpfe der App (gleiche Kalender und Farben) und ein neuer Quälgeist. */
@Composable
private fun ButtonRow(quick: List<QuickButton>) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        quick.forEach { q ->
            val bg = Color(q.color)
            WidgetButton(
                label = "+ ${q.name}",
                background = ColorProvider(bg),
                content = ColorProvider(if (bg.luminance() > 0.5f) Color.Black else Color.White),
                action = actionStartActivity(NewEventIntents.forCalendar(LocalContext.current, q.calendarId)),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        // Gleiche Hauptfarbe wie die Überschriften und die Heute-Markierung in Chip (Material You).
        WidgetButton(
            label = "+ Quälgeist",
            background = GlanceTheme.colors.primary,
            content = GlanceTheme.colors.onPrimary,
            action = actionStartActivity<QuaelgeistActivity>(),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun WidgetButton(
    label: String,
    background: ColorProvider,
    content: ColorProvider,
    action: Action,
    modifier: GlanceModifier,
) {
    Box(
        modifier = modifier.height(36.dp).cornerRadius(18.dp).background(background).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = content, fontWeight = FontWeight.Medium, fontSize = 13.sp),
        )
    }
}

@Composable
private fun WeatherLine(text: String) {
    // Zentriert (Wunsch des Nutzers).
    Text(
        text = text,
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth(),
        style = TextStyle(color = TEXT, fontWeight = FontWeight.Medium, fontSize = 13.sp, textAlign = TextAlign.Center),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = TextStyle(color = TEXT_DIM, fontSize = 13.sp))
}

@Composable
private fun EventLine(ev: EventInstance, today: LocalDate) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = GlanceModifier.width(4.dp).height(40.dp).cornerRadius(2.dp).background(Color(ev.color))) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = Formats.timeRange(ev, today),
                style = TextStyle(color = TEXT_DIM, fontSize = 12.sp),
            )
            Text(
                text = ev.title,
                maxLines = 2,
                style = TextStyle(color = TEXT, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            )
            if (ev.location.isNotBlank()) {
                Text(
                    text = ev.location,
                    maxLines = 1,
                    style = TextStyle(color = TEXT_DIM, fontSize = 12.sp),
                )
            }
        }
    }
}
