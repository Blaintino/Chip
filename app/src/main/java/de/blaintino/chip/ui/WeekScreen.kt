package de.blaintino.chip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Beim Öffnen sichtbarer Zeitraum (Wunsch des Nutzers): 8–18 Uhr füllt die Höhe des Rasters. */
private const val VISIBLE_FROM = 8
private const val VISIBLE_TO = 18
private val MIN_HOUR_HEIGHT = 28.dp
private val TIME_COLUMN = 36.dp
private val WEEKDAY = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)
private const val MAX_ALL_DAY = 2

/**
 * Wochen- (Mo–So) bzw. Tagesansicht mit Stundenraster. Ganztägige Termine oben, zeitgebundene im Raster
 * (mehrtägige je Tag zugeschnitten, Überschneidungen nebeneinander über [DayLayout]).
 * Tippen auf eine freie Stelle legt dort einen Termin an.
 */
@Composable
fun WeekScreen(
    repo: CalendarRepository,
    /** 7 = Woche (Mo–So), 1 = einzelner Tag ([selected]). */
    dayCount: Int,
    version: Int,
    hidden: Set<Long>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDateTime) -> Unit,
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val single = dayCount == 1
    val weekStart = if (single) selected else selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0L until dayCount.toLong()).map { weekStart.plusDays(it) }
    val maxAllDay = if (single) 4 else MAX_ALL_DAY
    var events by remember { mutableStateOf<List<EventInstance>>(emptyList()) }
    LaunchedEffect(version, hidden, weekStart, dayCount) {
        events = withContext(Dispatchers.IO) { repo.instances(weekStart, days.last(), hidden) }
    }
    val allDayByDay = days.associateWith { d -> events.filter { it.allDay && d in it.startDay..it.endDay } }
    val anyAllDay = allDayByDay.values.any { it.isNotEmpty() }

    val scroll = rememberScrollState()
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        // Kopf: Woche wechseln, Tippen auf den Titel springt zu heute.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSelect(selected.minusDays(dayCount.toLong())) }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            Text(
                if (single) Formats.dayLong(selected)
                else "KW ${weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${Formats.short(weekStart)} – ${Formats.short(weekStart.plusDays(6))}",
                modifier = Modifier.weight(1f).clickable { onSelect(today) },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { onSelect(selected.plusDays(dayCount.toLong())) }) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }

        // Wochentage
        Row(Modifier.fillMaxWidth().padding(end = 4.dp)) {
            Box(Modifier.width(TIME_COLUMN))
            days.forEach { d ->
                val isToday = d == today
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .then(if (d == selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                        .clickable { onSelect(d) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(WEEKDAY.format(d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Ganztägige Termine
        if (anyAllDay) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp, top = 2.dp)) {
                Box(Modifier.width(TIME_COLUMN))
                days.forEach { d ->
                    val list = allDayByDay[d].orEmpty()
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        list.take(maxAllDay).forEach { ev -> EventChip(ev, Modifier.fillMaxWidth().height(18.dp), onClick = { onEventClick(ev) }) }
                        if (list.size > maxAllDay) {
                            Text(
                                "+${list.size - maxAllDay}",
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(d) },
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))

        // Stundenraster
        val lineColor = MaterialTheme.colorScheme.outlineVariant
        val nowColor = MaterialTheme.colorScheme.error
        // Stundenhöhe aus der verfügbaren Höhe: genau VISIBLE_FROM–VISIBLE_TO passt hinein,
        // unabhängig von Bildschirmgröße und Schriftgröße. Beim Öffnen auf VISIBLE_FROM scrollen.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val hourHeight = maxOf(maxHeight / (VISIBLE_TO - VISIBLE_FROM), MIN_HOUR_HEIGHT)
        LaunchedEffect(hourHeight) {
            scroll.scrollTo(with(density) { (hourHeight * VISIBLE_FROM).roundToPx() })
        }
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Row(Modifier.fillMaxWidth().height(hourHeight * 24).padding(end = 4.dp)) {
                Box(Modifier.width(TIME_COLUMN).fillMaxHeight()) {
                    for (h in 1 until 24) {
                        Text(
                            "%02d".format(h),
                            modifier = Modifier.offset(y = hourHeight * h - 7.dp).fillMaxWidth().padding(end = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                days.forEach { d ->
                    val dayStart = d.atStartOfDay(zone).toInstant().toEpochMilli()
                    val dayEnd = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val timed = events.filter {
                        !it.allDay && (it.end > dayStart && it.begin < dayEnd || it.begin == it.end && it.begin in dayStart until dayEnd)
                    }
                    val segments = timed.map { ev ->
                        val s = ((maxOf(ev.begin, dayStart) - dayStart) / 60_000).toInt().coerceIn(0, 1440)
                        val e = ((minOf(ev.end, dayEnd) - dayStart) / 60_000).toInt().coerceIn(0, 1440)
                        s to e
                    }
                    val slots = DayLayout.layout(segments)
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxHeight()
                            .drawBehind {
                                val hourPx = hourHeight.toPx()
                                for (h in 1 until 24) {
                                    drawLine(lineColor, Offset(0f, hourPx * h), Offset(size.width, hourPx * h), strokeWidth = 1f)
                                }
                                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
                            }
                            .pointerInput(d, hourHeight) {
                                detectTapGestures { pos ->
                                    val hour = (pos.y / hourHeight.toPx()).toInt().coerceIn(0, 23)
                                    onCreateAt(d.atTime(hour, 0))
                                }
                            },
                    ) {
                        val colWidth = maxWidth
                        slots.forEach { slot ->
                            val ev = timed[slot.index]
                            val w = colWidth / slot.columns
                            EventChip(
                                ev,
                                Modifier
                                    .offset(x = w * slot.column, y = hourHeight * (slot.startMin / 60f))
                                    .width(w)
                                    .height(hourHeight * ((slot.endMin - slot.startMin) / 60f))
                                    .padding(1.dp),
                                onClick = { onEventClick(ev) },
                                showTime = slot.endMin - slot.startMin >= 45,
                                showLocation = single,
                            )
                        }
                        if (d == today) {
                            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
                            Box(
                                Modifier.offset(y = hourHeight * (nowMin / 60f) - 1.dp).fillMaxWidth().height(2.dp)
                                    .background(nowColor),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EventChip(
    ev: EventInstance,
    modifier: Modifier,
    onClick: () -> Unit,
    showTime: Boolean = false,
    showLocation: Boolean = false,
) {
    val bg = Color(ev.color)
    val fg = if (bg.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(0.5.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    ) {
        Column {
            Text(ev.title, color = fg, fontSize = 11.sp, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            if (showTime) {
                Text(Formats.time(ev.begin), color = fg.copy(alpha = 0.85f), fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1)
            }
            if (showLocation && ev.location.isNotBlank()) {
                Text(ev.location, color = fg.copy(alpha = 0.85f), fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1)
            }
        }
    }
}
