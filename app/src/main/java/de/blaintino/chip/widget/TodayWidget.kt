package de.blaintino.chip.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Widget „Heute“: scrollbare Liste der heutigen Termine mit Uhrzeit, Titel und Ort. */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val repo = CalendarRepository(context)
        val events: List<EventInstance>? = withContext(Dispatchers.IO) {
            if (repo.hasPermission()) repo.instances(today, today, HiddenCalendars.get(context)) else null
        }
        provideContent {
            GlanceTheme {
                WidgetContent(today, events)
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { TodayWidget().updateAll(context) }
        }
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
private fun WidgetContent(today: LocalDate, events: List<EventInstance>?) {
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
        when {
            events == null -> Hint("Kalenderzugriff in Chip erlauben")
            events.isEmpty() -> Hint("Keine Termine heute")
            else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(events, itemId = { it.eventId * 100_003L + it.begin }) { ev ->
                    EventLine(ev, today)
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
