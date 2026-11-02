package de.blaintino.chip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private const val CELL_RATIO = 0.85f
private const val MONTH_SPAN_LANES = 2
private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** Monatsraster (Woche ab Montag) mit Farbpunkten, darunter die Termine des gewählten Tages. */
@Composable
fun MonthScreen(
    repo: CalendarRepository,
    version: Int,
    hidden: Set<Long>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
    onEventClick: (EventInstance) -> Unit,
    /** Langes Drücken auf einen Tag: neuer Termin an diesem Tag (Wunsch des Nutzers). */
    onCreateOn: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    // Der Monat folgt dem gewählten Tag, damit Zoomen (Woche/Jahr) beim selben Datum bleibt.
    val month = YearMonth.from(selected)
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    var byDay by remember { mutableStateOf<Map<LocalDate, List<EventInstance>>>(emptyMap()) }
    var spanning by remember { mutableStateOf<List<EventInstance>>(emptyList()) }
    LaunchedEffect(version, hidden, month) {
        val all = withContext(Dispatchers.IO) { repo.instances(first, last, hidden) }
        byDay = groupByDay(all, first, last).toMap()
        spanning = all.filter { it.isSpanning() }
    }
    val dayEvents = byDay[selected].orEmpty()
    val haptic = LocalHapticFeedback.current
    val dayRegistry = LocalDayBounds.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onSelect(selected.minusMonths(1)) }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                Text(
                    Formats.month(month),
                    modifier = Modifier.weight(1f).clickable {
                        onSelect(today)
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { onSelect(selected.plusMonths(1)) }) { Text("›", style = MaterialTheme.typography.titleLarge) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                WEEKDAYS.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
                var weekStart = first
                while (weekStart <= last) {
                    val ws = weekStart
                    val we = weekStart.plusDays(6)
                    // Mehrtägige Termine dieser Woche als verbundene Balken unten in den Zellen (Wunsch des Nutzers).
                    val weekSpans = spanning.filter { it.endDay >= ws && it.startDay <= we }
                    val idx = weekSpans.map { e ->
                        (maxOf(e.startDay, ws).toEpochDay() - ws.toEpochDay()).toInt() to
                            (minOf(e.endDay, we).toEpochDay() - ws.toEpochDay()).toInt()
                    }
                    val lanes = SpanLayout.lanes(idx)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cellW = maxWidth / 7
                    val cellH = cellW / CELL_RATIO
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0L until 7L) {
                            val day = weekStart.plusDays(i)
                            DayCell(
                                day = day,
                                inMonth = YearMonth.from(day) == month,
                                isToday = day == today,
                                isSelected = day == selected,
                                events = byDay[day].orEmpty().filter { !it.isSpanning() },
                                modifier = Modifier.weight(1f).dayBounds(dayRegistry, day),
                                onClick = { onSelect(day) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelect(day)
                                    onCreateOn(day)
                                },
                            )
                        }
                    }
                    weekSpans.forEachIndexed { i, ev ->
                        val lane = lanes[i]
                        if (lane < MONTH_SPAN_LANES) {
                            val (a, b) = idx[i]
                            SpanBar(
                                label = ev.title,
                                color = ev.color,
                                startRounded = ev.startDay >= ws,
                                endRounded = ev.endDay <= we,
                                showText = false,
                                modifier = Modifier
                                    .offset(x = cellW * a + 2.dp, y = cellH - 6.dp - 6.dp * lane)
                                    .width(cellW * (b - a + 1) - 4.dp)
                                    .height(4.dp),
                            )
                        }
                    }
                    }
                    weekStart = weekStart.plusWeeks(1)
                }
            }
        }
        item { SectionLabel(Formats.dayHeader(selected, today)) }
        if (dayEvents.isEmpty()) {
            item {
                Text(
                    "Keine Termine",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(dayEvents, key = { "${it.eventId}-${it.begin}" }) { ev ->
                EventRowItem(ev, selected, onClick = { onEventClick(ev) })
            }
        }
        item { Box(Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<EventInstance>,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .aspectRatio(CELL_RATIO)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (isSelected) Modifier.background(colors.secondaryContainer) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .then(if (isToday) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (inMonth) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            events.take(4).forEach { ColorDot(it.color, size = 6) }
        }
        if (events.size > 4) {
            Text("+${events.size - 4}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}
