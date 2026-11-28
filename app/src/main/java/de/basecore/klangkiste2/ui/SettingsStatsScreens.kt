package de.basecore.klangkiste2.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.data.AppSettingsEntity
import de.basecore.klangkiste2.data.TrackWithAudio
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private val Panel = Color(0xFF363D40)
private val Outline = Color(0xFF515A5E)
private val BlueAccent = Color(0xFF74B9FF)
private val GreenAccent = Color(0xFF51D58C)
private val OrangeAccent = Color(0xFFFFAB57)

@Composable
private fun ParentPanel(title: String, accent: Color = BlueAccent, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Outline, shape).height(IntrinsicSize.Min)) {
        Box(Modifier.fillMaxHeight().width(3.dp).background(accent))
        Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Help(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, { Text(label) }, shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlueAccent, selectedLabelColor = Color(0xFF213039)))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(vm: KlangKisteViewModel, settings: AppSettingsEntity, onPinApp: () -> Unit, mode: DisplayMode, onMode: (DisplayMode) -> Unit) {
    val context = LocalContext.current
    val tts by vm.ttsAvailable.collectAsState()
    val remaining by vm.sleepSeconds.collectAsState()
    val storyLimit by vm.sleepStoryLimit.collectAsState()
    val storiesRemaining by vm.sleepStoriesRemaining.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    var showRules by rememberSaveable { mutableStateOf(false) }
    var storyDialog by rememberSaveable { mutableStateOf(false) }
    var storyInput by rememberSaveable { mutableStateOf("2") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.updateSettings { it.copy(backgroundMode = "image", backgroundUri = uri.toString()) }
        }
    }
    if (storyDialog) {
        val count = storyInput.toIntOrNull()?.takeIf { it in 1..99 }
        AlertDialog(onDismissRequest = { storyDialog = false }, title = { Text("Nach Hörspielen") },
            text = { OutlinedTextField(storyInput, { storyInput = it.filter { c -> c in '0'..'9' }.take(2) },
                label = { Text("Anzahl (1–99)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(enabled = count != null, onClick = { count?.let(vm::setSleepStoryLimit); storyDialog = false }) { Text("Übernehmen") } },
            dismissButton = { TextButton(onClick = { storyDialog = false }) { Text("Abbrechen") } })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Einstellungen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { ParentPanel("Start & Lautstärke") {
            Toggle("Beim Start direkt in den Kindermodus", settings.startMode == "kids", vm::setStartInKids)
            HorizontalDivider(color = Outline)
            Text("Maximale Lautstärke: ${(settings.maxVolume * 100).toInt()}%")
            Slider(settings.maxVolume, { v -> vm.updateSettings { it.copy(maxVolume = v.coerceIn(0f, 1f)) } })
            OutlinedButton(onClick = vm::playTestTone) { Text("Testton abspielen") }
            Help("Das Limit regelt die App-Lautstärke. Die Medienlautstärke des Handys wirkt zusätzlich.")
        } }
        item { ParentPanel("Schlaf-Timer & Hörregeln", GreenAccent) {
            Text("Schlaf-Timer", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.0 to "Aus", 0.5 to "30 Sek.", 10.0 to "10 Min.", 20.0 to "20 Min.", 30.0 to "30 Min.", 60.0 to "1 Std.").forEach { (minutes, label) ->
                    Choice(label, storyLimit == 0 && settings.sleepTimerMinutes == minutes) { vm.setSleepTimer(minutes) }
                }
                Choice("Nach Hörspielen", storyLimit > 0) {
                    storyInput = if (storyLimit > 0) storyLimit.toString() else "2"
                    storyDialog = true
                }
            }
            Help(when {
                storyLimit > 0 -> when (storiesRemaining) { 0 -> "Schlafenszeit"; 1 -> "Noch 1 Hörspiel"; else -> "Noch $storiesRemaining Hörspiele" }
                settings.sleepTimerMinutes <= 0.0 -> "Timer ausgeschaltet"
                remaining > 0 -> (if (playing) "Verbleibend: %d:%02d" else "Pausiert bei %d:%02d").format(remaining / 60, remaining % 60)
                else -> "Startet mit der Wiedergabe"
            })
            HorizontalDivider(color = Outline)
            Text("Altersfilter: ${if (settings.maxAge >= 99) "Alle" else "bis ${settings.maxAge} Jahre"}")
            Help("Gilt für alle Hörspiele. Titel ohne Altersempfehlung bleiben erlaubt.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(99 to "Alle", 0 to "0 / unbekannt", 4 to "Bis 4", 12 to "Bis 12").forEach { (age, label) ->
                    Choice(label, if (age == 99) settings.maxAge >= 99 else settings.maxAge == age) { vm.updateSettings { it.copy(maxAge = age) } }
                }
            }
            TextButton(onClick = { showRules = !showRules }) { Text(if (showRules) "Hörregeln ausblenden" else "Hörregeln anzeigen") }
            if (showRules) ListeningRules()
        } }
        item { ParentPanel("Energie & Display") {
            Text("Display anlassen", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DisplayMode.entries.forEach { option -> Choice(option.label, mode == option) { onMode(option) } }
            }
            Help("Bei Wiedergabe gilt im Kindermodus. Immer an gilt auch bei Pause und im Elternbereich, solange KlangKiste im Vordergrund ist.")
            HorizontalDivider(color = Outline)
            Toggle("Stromsparen beim Umdrehen", settings.ecoMode) { v -> vm.updateSettings { it.copy(ecoMode = v) } }
            Help("Dunkelt die Ansicht im Kindermodus ab, wenn das Handy mit dem Display nach unten liegt.")
        } }
        item { ParentPanel("Kindermodus & Aussehen") {
            Toggle("Bibliothek im Kindermodus", settings.libraryEnabled) { v -> vm.updateSettings { it.copy(libraryEnabled = v) } }
            Toggle("Bunte Kindersteuerung", settings.colorfulControls) { v -> vm.updateSettings { it.copy(colorfulControls = v) } }
            HorizontalDivider(color = Outline)
            Text("Hintergrund", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Choice("Original", settings.backgroundMode == "default") { vm.updateSettings { it.copy(backgroundMode = "default", backgroundUri = null) } }
                Choice("Farbe", settings.backgroundMode == "color") { vm.updateSettings { it.copy(backgroundMode = "color") } }
                Choice("Bild wählen", settings.backgroundMode == "image") { picker.launch(arrayOf("image/*")) }
            }
            if (settings.backgroundMode == "color") OutlinedTextField(settings.backgroundColor,
                { v -> vm.updateSettings { it.copy(backgroundColor = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Farbcode, z. B. #FFFFFF") }, singleLine = true)
        } }
        item { ParentPanel("Speicher & System", OrangeAccent) {
            Toggle("Medien in App-Speicher kopieren", settings.storageMode == "copy") { v -> vm.updateSettings { it.copy(storageMode = if (v) "copy" else "reference") } }
            Help("Dateireferenzen benötigen Androids Dateifreigabe. Kopien im App-Speicher werden bei Deinstallation entfernt.")
            HorizontalDivider(color = Outline)
            Help(if (tts) "Deutsche Vorlesestimme verfügbar." else "Keine deutsche Vorlesestimme bestätigt.")
            OutlinedButton(onClick = { vm.speakTitle("Guten Abend") }) { Text("Deutsche Stimme testen") }
            OutlinedButton(onClick = onPinApp) { Text("Android-App-Pinning starten") }
            Help("Android fragt vor dem Anheften nach. App-Pinning ersetzt keinen vollständigen Kioskmodus.")
        } }
    }
}

@Composable
fun StatsScreen(vm: KlangKisteViewModel, tracks: List<TrackWithAudio>) {
    val events by vm.events.collectAsState()
    var scope by rememberSaveable { mutableStateOf("day") }
    var showTimeline by rememberSaveable { mutableStateOf(false) }
    val midnight = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val cutoff = when (scope) { "day" -> midnight; "week" -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000; else -> 0L }
    val filtered = events.filter { it.timestamp >= cutoff }
    val sessions = filtered.filter { it.type == "stop" || it.type == "app_close" }
    val duration = sessions.sumOf { it.durationSec }
    val names = tracks.associate { it.track.id to it.track.title }
    val completed = filtered.count { it.type == "completed" }
    val top = sessions.filter { !it.trackId.isNullOrBlank() }.groupBy { it.trackId }
        .mapValues { (_, entries) -> entries.sumOf { it.durationSec } }.toList()
        .filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
    val times = DoubleArray(4)
    val clock = Calendar.getInstance()
    sessions.forEach {
        clock.timeInMillis = it.timestamp
        val hour = clock.get(Calendar.HOUR_OF_DAY)
        val bucket = if (hour < 6) 3 else hour / 6 - 1
        times[bucket] += it.durationSec
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Nutzung & Statistik", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("day" to "Heute", "week" to "7 Tage", "all" to "Gesamt").forEach { (key, label) ->
                    Surface(modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { scope = key },
                        shape = CircleShape, color = if (scope == key) BlueAccent else Panel,
                        contentColor = if (scope == key) Color(0xFF213039) else Color.White) {
                        Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text(label) }
                    }
                }
            }
        }
        item { ParentPanel("Hörübersicht", GreenAccent) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(formatDuration(duration), "Hördauer", Modifier.weight(1f))
                Metric(formatDuration(if (sessions.isEmpty()) 0.0 else duration / sessions.size), "Ø pro Session", Modifier.weight(1f))
            }
            HorizontalDivider(color = Outline)
            Text("Vollständig gehört: $completed")
        } }
        item { ParentPanel("Lieblingshörspiele") {
            if (top.isEmpty()) Help("Für diesen Zeitraum gibt es noch keine erfasste Hördauer.")
            else top.forEach { (id, seconds) ->
                StatBar(id?.let { names[it] } ?: "Unbekanntes Hörspiel", seconds, top.first().second, BlueAccent)
            }
        } }
        item { ParentPanel("Tageszeit", OrangeAccent) {
            listOf("Morgens (6–12)", "Mittags (12–18)", "Abends (18–24)", "Nachts (0–6)").forEachIndexed { i, label ->
                StatBar(label, times[i], times.maxOrNull() ?: 0.0, OrangeAccent)
            }
            Help("Hördauer nach Uhrzeit am Ende der erfassten Session.")
        } }
        item { ParentPanel("Weitere Aktivitäten") {
            Text("NFC-Scans: ${filtered.count { it.type == "scan" }}")
            Text("Pausen / Stopps: ${sessions.size}")
            Help("Umdrehen wird in dieser Version nicht als Ereignis erfasst.")
            TextButton(onClick = { showTimeline = !showTimeline }) {
                Text(if (showTimeline) "Verlauf ausblenden" else "Verlauf anzeigen · max. 50 Ereignisse")
            }
            if (showTimeline && filtered.isEmpty()) Help("Keine Ereignisse im gewählten Zeitraum.")
        } }
        if (showTimeline) items(filtered.sortedByDescending { it.timestamp }.take(50), key = { it.id }) { event ->
            ParentPanel(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.timestamp))) {
                Text(event.details.ifBlank { event.type })
                event.trackId?.let { names[it] }?.let { Help(it) }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = GreenAccent, fontWeight = FontWeight.Bold)
        Help(label)
    }
}

@Composable
private fun StatBar(label: String, seconds: Double, maxSeconds: Double, color: Color) {
    val fraction = if (maxSeconds > 0) (seconds / maxSeconds).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Help(formatDuration(seconds))
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Outline)) {
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    return when {
        total >= 3600 -> "${total / 3600} h ${total % 3600 / 60} min"
        total >= 60 -> "${total / 60} min ${total % 60} s"
        else -> "$total s"
    }
}
