package de.blaintino.chip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.Formats
import java.time.LocalDate

/** Eine Terminzeile: Farbbalken, Uhrzeit, Titel, Ort. */
@Composable
fun EventRowItem(ev: EventInstance, day: LocalDate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(ev.color)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                Formats.timeRange(ev, day),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                ev.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ev.location.isNotBlank()) {
                Text(
                    ev.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ColorDot(color: Int, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(color)))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Feste Auswahl an Kalenderfarben für Abos. */
val CALENDAR_COLORS = listOf(
    0xFFD50000, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679,
    0xFF0B8043, 0xFF039BE5, 0xFF3F51B5, 0xFF7986CB, 0xFF8E24AA,
    0xFFD81B60, 0xFF00796B, 0xFF795548, 0xFF616161, 0xFF000000,
).map { it.toInt() }

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
