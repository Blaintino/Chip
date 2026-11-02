package de.blaintino.chip.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val AGENDA_DAYS = 90L

/** Alle Termine der nächsten 90 Tage, nach Tagen gruppiert (mehrtägige an jedem Tag). */
@Composable
fun AgendaScreen(
    repo: CalendarRepository,
    version: Int,
    hidden: Set<Long>,
    contentPadding: PaddingValues,
    onEventClick: (EventInstance) -> Unit,
) {
    val today = LocalDate.now()
    var days by remember { mutableStateOf<List<Pair<LocalDate, List<EventInstance>>>?>(null) }
    LaunchedEffect(version, hidden, today) {
        days = withContext(Dispatchers.IO) {
            groupByDay(repo.instances(today, today.plusDays(AGENDA_DAYS), hidden), today, today.plusDays(AGENDA_DAYS))
        }
    }
    val list = days
    when {
        list == null -> Unit
        list.isEmpty() -> Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text("Keine Termine in den nächsten $AGENDA_DAYS Tagen", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            for ((day, events) in list) {
                item(key = "h$day") { SectionLabel(Formats.dayHeader(day, today)) }
                items(events, key = { "e$day-${it.eventId}-${it.begin}" }) { ev ->
                    EventRowItem(ev, day, onClick = { onEventClick(ev) })
                }
            }
            item { Box(Modifier.padding(bottom = 80.dp)) }
        }
    }
}

/** Ordnet jedes Vorkommen allen Tagen zwischen Start- und Endtag zu (innerhalb von [from]..[to]). */
fun groupByDay(events: List<EventInstance>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, List<EventInstance>>> {
    val map = sortedMapOf<LocalDate, MutableList<EventInstance>>()
    for (ev in events) {
        var d = maxOf(ev.startDay, from)
        val last = minOf(ev.endDay, to)
        while (d <= last) {
            map.getOrPut(d) { mutableListOf() } += ev
            d = d.plusDays(1)
        }
    }
    return map.map { (day, list) -> day to list.sortedWith(compareBy({ !it.allDay }, { it.begin })) }
}
