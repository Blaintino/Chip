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
import androidx.glance.text.TextStyle
import de.blaintino.chip.MainActivity
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.Formats
import de.blaintino.chip.data.HiddenCalendars
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
            var state by remember { mutableStateOf<WidgetData>(WidgetData.Loading) }
            LaunchedEffect(tick) {
                val day = LocalDate.now()
                state = withContext(Dispatchers.IO) { load(context, day) }
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
            val events = repo.instances(today, today, HiddenCalendars.get(context))
            WidgetStatus.record(context, "${events.size} Termine geladen")
            WidgetData.Loaded(events)
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

private sealed interface WidgetData {
    data object Loading : WidgetData
    data object NoPermission : WidgetData
    data class Loaded(val events: List<EventInstance>) : WidgetData
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

@Composable
private fun WidgetContent(today: LocalDate, state: WidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            text = Formats.dayHeader(today, today),
            style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp),
        )
        Spacer(GlanceModifier.height(6.dp))
        when (state) {
            WidgetData.Loading -> Hint("Lädt …")
            WidgetData.NoPermission -> Hint("Kalenderzugriff in Chip erlauben")
            is WidgetData.Failed -> Hint("Fehler: ${state.message}")
            is WidgetData.Loaded -> if (state.events.isEmpty()) {
                Hint("Keine Termine heute")
            } else {
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
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
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
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Text(
                text = ev.title,
                maxLines = 2,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            )
            if (ev.location.isNotBlank()) {
                Text(
                    text = ev.location,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }
}
