package de.blaintino.chip.ui

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.blaintino.chip.data.CalendarInfo
import de.blaintino.chip.data.EventDraft
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val DATE = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN)

/**
 * Editor für Einzeltermine. Bei ganztägigen Terminen ist [EventDraft.end] exklusiv (Mitternacht
 * nach dem letzten Tag); angezeigt wird der letzte Tag selbst.
 */
@Composable
fun EventEditor(
    draft: EventDraft,
    calendars: List<CalendarInfo>,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val zone = ZoneId.systemDefault()
    val startZ = Instant.ofEpochMilli(draft.start).atZone(zone)
    val endZ = Instant.ofEpochMilli(draft.end).atZone(zone)

    var title by remember { mutableStateOf(draft.title) }
    var location by remember { mutableStateOf(draft.location) }
    var description by remember { mutableStateOf(draft.description) }
    var calendarId by remember { mutableStateOf(draft.calendarId) }
    var allDay by remember { mutableStateOf(draft.allDay) }
    var startDate by remember { mutableStateOf(startZ.toLocalDate()) }
    var startTime by remember { mutableStateOf(startZ.toLocalTime().withSecond(0).withNano(0)) }
    var endDate by remember {
        mutableStateOf(if (draft.allDay) maxOf(endZ.toLocalDate().minusDays(1), startZ.toLocalDate()) else endZ.toLocalDate())
    }
    var endTime by remember { mutableStateOf(endZ.toLocalTime().withSecond(0).withNano(0)) }

    var pickDate by remember { mutableStateOf<Boolean?>(null) } // true = Beginn, false = Ende
    var pickTime by remember { mutableStateOf<Boolean?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var calendarMenu by remember { mutableStateOf(false) }

    val selectedCalendar = calendars.firstOrNull { it.id == calendarId }

    fun save() {
        if (title.isBlank()) return
        val start: Long
        val end: Long
        if (allDay) {
            start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            end = maxOf(endDate, startDate).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            start = startDate.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
            val e = endDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
            end = maxOf(e, start)
        }
        onSave(
            draft.copy(
                calendarId = calendarId,
                title = title.trim(),
                location = location.trim(),
                description = description.trim(),
                allDay = allDay,
                start = start,
                end = end,
            ),
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // Kopfzeile: ✕ / Überschrift / Speichern – oben, damit die Tastatur sie nicht verdeckt.
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("✕", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        if (draft.id == null) "Neuer Termin" else "Termin bearbeiten",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = ::save, enabled = title.isNotBlank()) { Text("Speichern", fontWeight = FontWeight.Bold) }
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titel") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Ort") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Box {
                        OutlinedButton(onClick = { calendarMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            selectedCalendar?.let { ColorDot(it.color, size = 12) }
                            Spacer(Modifier.width(8.dp))
                            Text(selectedCalendar?.let { "${it.name} · ${it.accountName}" } ?: "Kalender wählen", modifier = Modifier.weight(1f))
                        }
                        DropdownMenu(expanded = calendarMenu, onDismissRequest = { calendarMenu = false }) {
                            calendars.forEach { cal ->
                                DropdownMenuItem(
                                    text = { Text("${cal.name} · ${cal.accountName}") },
                                    leadingIcon = { ColorDot(cal.color, size = 12) },
                                    onClick = { calendarId = cal.id; calendarMenu = false },
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ganztägig", modifier = Modifier.weight(1f))
                        Switch(checked = allDay, onCheckedChange = { allDay = it })
                    }
                    DateTimeRow("Beginn", startDate, startTime, allDay, onDate = { pickDate = true }, onTime = { pickTime = true })
                    DateTimeRow("Ende", endDate, endTime, allDay, onDate = { pickDate = false }, onTime = { pickTime = false })

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Notiz") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (onDelete != null) {
                        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Termin löschen", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }

    pickDate?.let { isStart ->
        val initial = if (isStart) startDate else endDate
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (isStart) {
                            // Ende mitschieben, damit die Dauer gleich bleibt.
                            val shift = d.toEpochDay() - startDate.toEpochDay()
                            startDate = d
                            endDate = endDate.plusDays(shift)
                        } else {
                            endDate = maxOf(d, startDate)
                        }
                    }
                    pickDate = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = null }) { Text("Abbrechen") } },
        ) { DatePicker(state = state) }
    }

    pickTime?.let { isStart ->
        val initial = if (isStart) startTime else endTime
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(state.hour, state.minute)
                    if (isStart) {
                        val oldStart = startDate.atTime(startTime)
                        val oldEnd = endDate.atTime(endTime)
                        val duration = java.time.Duration.between(oldStart, oldEnd).coerceAtLeast(java.time.Duration.ZERO)
                        startTime = t
                        val newEnd = startDate.atTime(t).plus(duration)
                        endDate = newEnd.toLocalDate()
                        endTime = newEnd.toLocalTime()
                    } else {
                        endTime = t
                    }
                    pickTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Abbrechen") } },
            text = { TimePicker(state = state) },
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Termin löschen?") },
            text = { Text("„${draft.title}“ wird aus dem Kalender gelöscht.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    allDay: Boolean,
    onDate: () -> Unit,
    onTime: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDate, modifier = Modifier.weight(1f)) { Text(DATE.format(date)) }
            if (!allDay) OutlinedButton(onClick = onTime) { Text(TIME.format(time)) }
        }
    }
}

/**
 * Nur-Lese-Ansicht für Termine, die Chip nicht selbst bearbeitet: Serien, Abo-Termine und
 * Kalender ohne Schreibrecht. Bearbeiten geht dann in der Kalender-App des Kontos.
 */
@Composable
fun EventDetails(ev: EventInstance, calendar: CalendarInfo?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val day = ev.startDay
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ev.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Formats.dayLong(day) + if (ev.endDay != ev.startDay) " – " + Formats.dayLong(ev.endDay) else "")
                Text(Formats.timeRange(ev, day))
                if (ev.location.isNotBlank()) Text(ev.location)
                calendar?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(it.color, size = 10)
                        Spacer(Modifier.width(6.dp))
                        Text("${it.name} · ${it.accountName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val reason = when {
                    calendar == null || !calendar.writable -> "Dieser Kalender ist nur lesbar."
                    ev.recurring -> "Serientermine bearbeitest du vorerst in der Kalender-App des Kontos (z. B. Google Kalender)."
                    else -> null
                }
                reason?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        dismissButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.eventId))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ev.begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ev.end)
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                }
                onDismiss()
            }) { Text("In Kalender-App öffnen") }
        },
    )
}
