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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import de.blaintino.chip.data.ReminderRules
import de.blaintino.chip.data.Repeat
import de.blaintino.chip.data.RepeatKind
import de.blaintino.chip.data.RepeatRules
import de.blaintino.chip.data.SeriesScope
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
 * Editor für Termine und Serien (Wunsch des Nutzers). Bei einem Vorkommen einer Serie fragt Speichern
 * bzw. Löschen, ob nur dieser Termin, dieser und alle folgenden oder die ganze Serie gemeint ist.
 * Einzeln geänderte Serientermine ([EventDraft.originalId]) bekommen keine eigene Wiederholung.
 * Bei ganztägigen Terminen ist [EventDraft.end] exklusiv (Mitternacht
 * nach dem letzten Tag); angezeigt wird der letzte Tag selbst.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditor(
    draft: EventDraft,
    calendars: List<CalendarInfo>,
    onDismiss: () -> Unit,
    /** Bei Vorkommen einer Serie mit gewähltem Umfang, sonst null. */
    onSave: (EventDraft, SeriesScope?) -> Unit,
    onDelete: ((SeriesScope) -> Unit)?,
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
    var repeat by remember { mutableStateOf(draft.repeat) }
    var repeatMenu by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }
    var askScope by remember { mutableStateOf<EventDraft?>(null) }
    var reminders by remember { mutableStateOf(draft.reminders) }
    val isSeries = draft.id != null && draft.recurring && draft.originalId == null

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
        val updated = draft.copy(
            calendarId = calendarId,
            title = title.trim(),
            location = location.trim(),
            description = description.trim(),
            allDay = allDay,
            start = start,
            end = end,
            repeat = if (draft.originalId != null) Repeat.NONE else repeat,
            reminders = ReminderRules.normalize(reminders),
        )
        if (isSeries) askScope = updated else onSave(updated, null)
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

                    if (draft.originalId != null) {
                        Text(
                            "Einzeln geänderter Termin einer Serie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column {
                            Text("Wiederholen", style = MaterialTheme.typography.labelLarge)
                            Box {
                                OutlinedButton(onClick = { repeatMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(RepeatRules.label(repeat.kind, startDate), modifier = Modifier.weight(1f))
                                }
                                DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                                    val kinds = RepeatRules.choices(startDate).let {
                                        if (draft.repeat.kind == RepeatKind.CUSTOM) it + RepeatKind.CUSTOM else it
                                    }
                                    kinds.forEach { kind ->
                                        DropdownMenuItem(
                                            text = { Text(RepeatRules.label(kind, startDate)) },
                                            onClick = {
                                                repeat = if (kind == RepeatKind.NONE || kind == RepeatKind.CUSTOM) Repeat(kind)
                                                else Repeat(kind, repeat.until)
                                                repeatMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (repeat.kind != RepeatKind.NONE && repeat.kind != RepeatKind.CUSTOM) {
                            Column {
                                Text("Endet", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { pickUntil = true }, modifier = Modifier.weight(1f)) {
                                        Text(repeat.until?.let { "am " + DATE.format(it) } ?: "Nie")
                                    }
                                    if (repeat.until != null) {
                                        TextButton(onClick = { repeat = repeat.copy(until = null) }) { Text("Nie") }
                                    }
                                }
                            }
                            if (repeat.kind == RepeatKind.MONTHLY_DATE && startDate.dayOfMonth > 28) {
                                Text(
                                    "In Monaten ohne den ${startDate.dayOfMonth}. entfällt der Termin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Erinnerungen", style = MaterialTheme.typography.labelLarge)
                        reminders.forEachIndexed { i, minutes ->
                            ReminderRow(
                                minutes = minutes,
                                onChange = { m -> reminders = reminders.toMutableList().also { it[i] = m } },
                                onRemove = { reminders = reminders.filterIndexed { j, _ -> j != i } },
                            )
                        }
                        // Bis zu drei (Wunsch des Nutzers); aus Google mitgebrachte weitere bleiben sichtbar.
                        if (reminders.size < ReminderRules.MAX) {
                            TextButton(onClick = {
                                val next = (ReminderRules.FACTORY + ReminderRules.OPTIONS).firstOrNull { it !in reminders } ?: 0
                                reminders = reminders + next
                            }) { Text("+ Erinnerung") }
                        }
                    }

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

    if (pickUntil) {
        val initial = repeat.until ?: startDate.plusMonths(3)
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickUntil = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        repeat = repeat.copy(until = maxOf(d, startDate))
                    }
                    pickUntil = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickUntil = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = state) }
    }

    askScope?.let { updated ->
        // Neue Regel (andere Art) gilt ab diesem Termin; „Nur dieser“ und „Alle“ nur bei gleicher Art.
        val sameKind = updated.repeat.kind == draft.repeat.kind
        val sameRule = updated.repeat == draft.repeat
        val first = draft.instanceStart != null && draft.instanceStart == draft.seriesStart
        ScopeDialog(
            title = "Serientermin speichern",
            text = if (sameKind) "Welche Termine sollen sich ändern?" else "Die neue Wiederholung gilt ab diesem Termin.",
            options = buildList {
                if (sameRule) add(SeriesScope.THIS to "Nur diesen Termin")
                if (!first || !sameKind) add(SeriesScope.FOLLOWING to if (first) "Ganze Serie" else "Diesen und alle folgenden")
                if (sameKind) add(SeriesScope.ALL to "Alle Termine der Serie")
            },
            onPick = { scope -> askScope = null; onSave(updated, scope) },
            onDismiss = { askScope = null },
        )
    }

    if (confirmDelete && onDelete != null) {
        when {
            isSeries -> ScopeDialog(
                title = "Serientermin löschen",
                text = "„${draft.title}“ – welche Termine sollen gelöscht werden?",
                options = listOf(
                    SeriesScope.THIS to "Nur diesen Termin",
                    SeriesScope.FOLLOWING to "Diesen und alle folgenden",
                    SeriesScope.ALL to "Ganze Serie",
                ),
                destructive = true,
                onPick = { scope -> confirmDelete = false; onDelete(scope) },
                onDismiss = { confirmDelete = false },
            )
            draft.originalId != null -> ScopeDialog(
                title = "Serientermin löschen",
                text = "„${draft.title}“ wurde einzeln geändert. Was soll gelöscht werden?",
                options = listOf(
                    SeriesScope.THIS to "Nur diesen Termin",
                    SeriesScope.FOLLOWING to "Diesen und alle folgenden",
                    SeriesScope.ALL to "Ganze Serie",
                ),
                destructive = true,
                onPick = { scope -> confirmDelete = false; onDelete(scope) },
                onDismiss = { confirmDelete = false },
            )
            else -> AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Termin löschen?") },
                text = { Text("„${draft.title}“ wird aus dem Kalender gelöscht.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(SeriesScope.THIS) }) { Text("Löschen") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
            )
        }
    }
}

/** Eine Erinnerung: Zeitpunkt wählen oder entfernen. */
@Composable
fun ReminderRow(minutes: Int?, onChange: (Int) -> Unit, onRemove: (() -> Unit)?, emptyLabel: String = "– keine –") {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(minutes?.let { ReminderRules.label(it) } ?: emptyLabel, modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                val options = (ReminderRules.OPTIONS + listOfNotNull(minutes)).distinct().sortedDescending()
                options.forEach { m ->
                    DropdownMenuItem(text = { Text(ReminderRules.label(m)) }, onClick = { onChange(m); open = false })
                }
            }
        }
        if (onRemove != null) TextButton(onClick = onRemove) { Text("✕") }
    }
}

/** Auswahl des Umfangs bei Serien: ein Knopf je Möglichkeit, untereinander. */
@Composable
private fun ScopeDialog(
    title: String,
    text: String,
    options: List<Pair<SeriesScope, String>>,
    onPick: (SeriesScope) -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text)
                Spacer(Modifier.padding(top = 4.dp))
                options.forEach { (scope, label) ->
                    TextButton(onClick = { onPick(scope) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
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
