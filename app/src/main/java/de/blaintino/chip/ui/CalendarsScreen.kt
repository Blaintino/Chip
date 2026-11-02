package de.blaintino.chip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import de.blaintino.chip.data.CalendarColors
import de.blaintino.chip.data.CalendarInfo
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.Formats
import de.blaintino.chip.data.QuickAddCalendars
import de.blaintino.chip.data.Subscription
import de.blaintino.chip.data.SubscriptionStore
import de.blaintino.chip.sync.SubscriptionSync
import de.blaintino.chip.widget.TodayWidget
import de.blaintino.chip.widget.WidgetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Abos verwalten und auswählen, welche Kalender Chip anzeigt. */
@Composable
fun CalendarsScreen(
    repo: CalendarRepository,
    version: Int,
    hidden: Set<Long>,
    onHiddenChange: (Set<Long>) -> Unit,
    onColorsChanged: () -> Unit,
    quickIds: List<Long?>,
    onQuickIdsChange: (List<Long?>) -> Unit,
    prefillUrl: String?,
    onPrefillHandled: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localVersion by remember { mutableIntStateOf(0) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var subs by remember { mutableStateOf<List<Subscription>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addUrl by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Subscription?>(null) }
    var colorFor by remember { mutableStateOf<CalendarInfo?>(null) }

    LaunchedEffect(version, localVersion) {
        val (c, s) = withContext(Dispatchers.IO) { repo.calendars() to SubscriptionStore.all(context) }
        calendars = c
        subs = s.sortedBy { it.name.lowercase() }
    }
    LaunchedEffect(prefillUrl) {
        if (prefillUrl != null) {
            addUrl = prefillUrl
            showAdd = true
            onPrefillHandled()
        }
    }

    fun runBusy(label: String, block: suspend () -> String?) {
        if (busy != null) return
        busy = label
        message = null
        scope.launch {
            message = try { block() } catch (e: Exception) { "Fehler: ${e.message}" }
            busy = null
            localVersion++
            TodayWidget.refresh(context)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { SectionLabel("Abos (ICS-Adressen)") }
        item {
            Text(
                "Werden täglich und beim Antippen von „Aktualisieren“ neu geladen. Abo-Kalender sind nur lesbar.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(subs, key = { "s${it.calendarId}" }) { sub ->
            SubscriptionCard(
                sub = sub.copy(color = calendars.firstOrNull { it.id == sub.calendarId }?.color ?: sub.color),
                enabled = busy == null,
                onRefresh = {
                    runBusy("Aktualisiere „${sub.name}“ …") {
                        describe(withContext(Dispatchers.IO) { SubscriptionSync.refresh(context, sub) })
                    }
                },
                onDelete = { confirmDelete = sub },
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Button(onClick = { addUrl = ""; showAdd = true }, enabled = busy == null) { Text("Abo hinzufügen") }
                if (subs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            runBusy("Aktualisiere alle Abos …") {
                                val results = withContext(Dispatchers.IO) { SubscriptionSync.refreshAll(context) }
                                val failed = results.count { it.second is SubscriptionSync.Outcome.Failed }
                                if (failed == 0) "Alle Abos aktualisiert" else "$failed von ${results.size} Abos fehlgeschlagen"
                            }
                        },
                        enabled = busy == null,
                    ) { Text("Alle aktualisieren") }
                }
            }
        }
        if (busy != null || message != null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (busy != null) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(busy ?: message.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item { SectionLabel("Plus-Knöpfe (Schnellauswahl)") }
        item {
            val writable = calendars.filter { it.writable }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Je Knopf ein Kalender; der Knopf übernimmt dessen Farbe. Ohne Auswahl gibt es ein einfaches Plus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                repeat(QuickAddCalendars.SLOTS) { slot ->
                    val current = quickIds.getOrNull(slot)
                    QuickSlotPicker(
                        label = "Knopf ${slot + 1}",
                        selected = writable.firstOrNull { it.id == current },
                        options = writable,
                        onPick = { id ->
                            val list = MutableList(QuickAddCalendars.SLOTS) { quickIds.getOrNull(it) }
                            list[slot] = id
                            onQuickIdsChange(list)
                        },
                    )
                }
            }
        }

        item { SectionLabel("Widget „Heute“") }
        item {
            val status = remember(localVersion, version) { WidgetStatus.last(context) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    status?.let { (time, msg) -> "Zuletzt: ${Formats.dateTime(time)} – $msg" }
                        ?: "Das Widget hat sich noch nie gemeldet (nicht platziert oder startet nicht).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { runBusy("Zeichne Widget neu …") { "Widget-Update angestoßen" } },
                    enabled = busy == null,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Widget neu zeichnen") }
            }
        }

        item { SectionLabel("In Chip anzeigen") }
        item {
            Text(
                "Häkchen = in Chip anzeigen und für neue Termine wählbar; Farbpunkt antippen = Farbe ändern. " +
                    "Geteilte Google-Kalender erscheinen hier, sobald das Google-Konto sie synchronisiert. " +
                    "Fehlt einer: in den Kontoeinstellungen des Handys bzw. der Google-Kalender-App die Synchronisierung für ihn einschalten.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val groups = calendars.groupBy { if (it.accountName == SubscriptionSync.ACCOUNT_NAME) "Abos" else it.accountName }
        for ((account, list) in groups) {
            item(key = "a$account") {
                Text(
                    account,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            items(list, key = { "c${it.id}" }) { cal ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onHiddenChange(if (cal.id in hidden) hidden - cal.id else hidden + cal.id)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = cal.id !in hidden,
                        onCheckedChange = { show -> onHiddenChange(if (show) hidden - cal.id else hidden + cal.id) },
                    )
                    // Farbpunkt antippen: eigene Farbe wählen.
                    Box(Modifier.clip(CircleShape).clickable { colorFor = cal }.padding(8.dp)) {
                        ColorDot(cal.color, size = 18)
                    }
                    Text(cal.name, modifier = Modifier.weight(1f))
                    if (!cal.writable) {
                        Text("nur lesen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
        item { Box(Modifier.height(24.dp)) }
    }

    if (showAdd) {
        AddSubscriptionDialog(
            initialUrl = addUrl,
            onDismiss = { showAdd = false },
            onConfirm = { url, name, color ->
                showAdd = false
                runBusy("Lade Abo …") {
                    val (sub, outcome) = withContext(Dispatchers.IO) { SubscriptionSync.add(context, url, name, color) }
                    "„${sub.name}“: " + describe(outcome)
                }
            },
        )
    }

    colorFor?.let { cal ->
        var picked by remember(cal.id) { mutableIntStateOf(cal.color) }
        AlertDialog(
            onDismissRequest = { colorFor = null },
            title = { Text("Farbe für „${cal.name}“") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorPicker(picked, onPick = { picked = it })
                    Text(
                        "Gilt nur in Chip (App, Widget, Plus-Knöpfe). Termine mit eigener Farbe behalten diese.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    CalendarColors.set(context, cal.id, picked)
                    colorFor = null
                    localVersion++
                    onColorsChanged()
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CalendarColors.set(context, cal.id, null)
                    colorFor = null
                    localVersion++
                    onColorsChanged()
                }) { Text("Kontofarbe") }
            },
        )
    }

    confirmDelete?.let { sub ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Abo löschen?") },
            text = { Text("„${sub.name}“ und alle seine Termine werden vom Handy entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    runBusy("Lösche …") {
                        withContext(Dispatchers.IO) { SubscriptionSync.remove(context, sub) }
                        "„${sub.name}“ gelöscht"
                    }
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
}

private fun describe(outcome: SubscriptionSync.Outcome): String = when (outcome) {
    is SubscriptionSync.Outcome.Updated -> "${outcome.count} Termine geladen"
    SubscriptionSync.Outcome.NotModified -> "unverändert"
    is SubscriptionSync.Outcome.Failed -> "Fehler: ${outcome.message}"
}

@Composable
private fun SubscriptionCard(sub: Subscription, enabled: Boolean, onRefresh: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(sub.color, size = 12)
                Spacer(Modifier.width(8.dp))
                Text(sub.name, style = MaterialTheme.typography.titleMedium)
            }
            Text(sub.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            val status = when {
                sub.lastError != null -> "Fehler: ${sub.lastError}"
                sub.lastSync > 0 -> "Aktualisiert ${Formats.dateTime(sub.lastSync)} · ${sub.eventCount} Termine"
                else -> "Noch nicht geladen"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (sub.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRefresh, enabled = enabled) { Text("Aktualisieren") }
                TextButton(onClick = onDelete, enabled = enabled) { Text("Löschen") }
            }
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String, color: Int) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(CALENDAR_COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abo hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Adresse (https:// oder webcal://)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (leer = aus der Datei)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Farbe", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color, onPick = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url.trim(), name.trim(), color) }, enabled = url.isNotBlank()) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CALENDAR_COLORS.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .then(
                                if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier,
                            )
                            .clickable { onPick(c) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSlotPicker(
    label: String,
    selected: CalendarInfo?,
    options: List<CalendarInfo>,
    onPick: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                selected?.let { ColorDot(it.color, size = 12) }
                Spacer(Modifier.width(8.dp))
                Text(selected?.let { "${it.name} · ${it.accountName}" } ?: "– keiner –", modifier = Modifier.weight(1f), maxLines = 1)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("– keiner –") }, onClick = { onPick(null); open = false })
                options.forEach { cal ->
                    DropdownMenuItem(
                        text = { Text("${cal.name} · ${cal.accountName}") },
                        leadingIcon = { ColorDot(cal.color, size = 12) },
                        onClick = { onPick(cal.id); open = false },
                    )
                }
            }
        }
    }
}
