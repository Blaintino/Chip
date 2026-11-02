package de.blaintino.chip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.blaintino.chip.data.CalendarRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MONTH_NAME = DateTimeFormatter.ofPattern("MMMM", Locale.GERMAN)

/** Jahresübersicht: 12 kleine Monate, Tage mit Terminen hervorgehoben. Tippen öffnet den Monat. */
@Composable
fun YearScreen(
    repo: CalendarRepository,
    version: Int,
    hidden: Set<Long>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpenMonth: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
) {
    val today = LocalDate.now()
    val dayRegistry = LocalDayBounds.current
    val year = selected.year
    var busyDays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    // Tage unter mehrtägigen Terminen → Farbe der Linie (Wunsch des Nutzers: Mehrtägiges verbunden zeigen).
    var spanDays by remember { mutableStateOf<Map<LocalDate, Int>>(emptyMap()) }
    LaunchedEffect(version, hidden, year) {
        val from = LocalDate.of(year, 1, 1)
        val to = LocalDate.of(year, 12, 31)
        val all = withContext(Dispatchers.IO) { repo.instances(from, to, hidden) }
        val (spans, single) = all.partition { it.isSpanning() }
        busyDays = groupByDay(single, from, to).map { it.first }.toSet()
        spanDays = buildMap {
            for (e in spans) {
                var d = maxOf(e.startDay, from)
                while (d <= minOf(e.endDay, to)) { putIfAbsent(d, e.color); d = d.plusDays(1) }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSelect(selected.minusYears(1)) }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            Text(
                year.toString(),
                modifier = Modifier.weight(1f).clickable { onSelect(today) },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { onSelect(selected.plusYears(1)) }) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        (1..12).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    MiniMonth(
                        month = YearMonth.of(year, m),
                        today = today,
                        busyDays = busyDays,
                        spanDays = spanDays,
                        modifier = Modifier.weight(1f).dayBounds(dayRegistry, YearMonth.of(year, m).atDay(1)),
                        onClick = {
                            val ym = YearMonth.of(year, m)
                            onOpenMonth(if (YearMonth.from(selected) == ym) selected else ym.atDay(1))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    today: LocalDate,
    busyDays: Set<LocalDate>,
    spanDays: Map<LocalDate, Int>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Text(
            MONTH_NAME.format(month).replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (YearMonth.from(today) == month) colors.primary else colors.onSurface,
        )
        var weekStart = first
        repeat(6) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0L until 7L) {
                    val d = weekStart.plusDays(i)
                    val inMonth = YearMonth.from(d) == month
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(vertical = 0.5.dp), contentAlignment = Alignment.Center) {
                        // Linie unter Tagen mehrtägiger Termine; ohne seitlichen Abstand, damit sie durchgeht.
                        if (inMonth) spanDays[d]?.let { c ->
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Color(c)))
                        }
                        if (inMonth) {
                            val isToday = d == today
                            val busy = d in busyDays
                            Box(
                                Modifier.fillMaxSize().padding(horizontal = 0.5.dp).clip(CircleShape).then(
                                    when {
                                        isToday -> Modifier.background(colors.primary)
                                        busy -> Modifier.background(colors.secondaryContainer)
                                        else -> Modifier
                                    },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.dayOfMonth.toString(),
                                    fontSize = 9.sp,
                                    lineHeight = 9.sp,
                                    fontWeight = if (busy || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isToday -> colors.onPrimary
                                        busy -> colors.onSecondaryContainer
                                        else -> colors.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
            weekStart = weekStart.plusWeeks(1)
        }
    }
}
