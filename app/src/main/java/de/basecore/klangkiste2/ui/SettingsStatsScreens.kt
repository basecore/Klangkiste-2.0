package de.basecore.klangkiste2.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private val Sky = Color(0xFF74B9FF)
private val Mint = Color(0xFF55D993)
private val Amber = Color(0xFFFFAD5A)
private val PanelShape = RoundedCornerShape(14.dp)

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
    var showStoryDialog by rememberSaveable { mutableStateOf(false) }
    var storyInput by rememberSaveable { mutableStateOf("2") }
    if (showStoryDialog) {
        val count = storyInput.toIntOrNull()?.takeIf { it in 1..99 }
        AlertDialog(onDismissRequest = { showStoryDialog = false }, title = { Text("Nach Hörspielen") },
            text = { OutlinedTextField(storyInput, { input -> storyInput = input.filter { it in '0'..'9' }.take(2) }, label = { Text("Anzahl (1–99)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(enabled = count != null, onClick = { count?.let(vm::setSleepStoryLimit); showStoryDialog = false }) { Text("Übernehmen") } },
            dismissButton = { TextButton(onClick = { showStoryDialog = false }) { Text("Abbrechen") } })
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.updateSettings { it.copy(backgroundMode = "image", backgroundUri = uri.toString()) }
        }
    }
    val timers = listOf(0.0 to "Aus", 0.5 to "30 Sek.", 10.0 to "10 Min.", 20.0 to "20 Min.", 30.0 to "30 Min.", 60.0 to "1 Std.")
    val timerStatus = when {
        storyLimit > 0 -> when (storiesRemaining) { 0 -> "Schlafenszeit"; 1 -> "Noch 1 Hörspiel"; else -> "Noch $storiesRemaining Hörspiele" }
        settings.sleepTimerMinutes <= 0 -> "Timer ausgeschaltet"
        remaining > 0 -> (if (playing) "Verbleibend: %d:%02d" else "Pausiert bei %d:%02d").format(remaining / 60, remaining % 60)
        else -> "Startet mit der Wiedergabe"
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("Einstellungen", "Wiedergabe, Kindermodus und Gerät") }
        item {
            DashboardPanel("Start & Wiedergabe", Mint) {
                Text("Startmodus", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Selection("Elternmodus", settings.startMode != "kids") { vm.setStartInKids(false) }
                    Selection("Kindermodus", settings.startMode == "kids") { vm.setStartInKids(true) }
                }
                HorizontalDivider()
                Text("Maximale Lautstärke: ${(settings.maxVolume * 100).toInt()} %", style = MaterialTheme.typography.titleSmall)
                Slider(settings.maxVolume, { value -> vm.updateSettings { it.copy(maxVolume = value.coerceIn(0f, 1f)) } })
                OutlinedButton(onClick = vm::playTestTone) { Text("Testton abspielen") }
                Note("Prüfe die Begrenzung bei der Medienlautstärke, die das Gerät im Kindermodus tatsächlich nutzt.")
            }
        }
        item {
            DashboardPanel("Schlaf-Timer", Amber) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timers.forEach { (minutes, label) -> Selection(label, storyLimit == 0 && settings.sleepTimerMinutes == minutes) { vm.setSleepTimer(minutes) } }
                    Selection("Nach Hörspielen", storyLimit > 0) {
                        storyInput = if (storyLimit > 0) storyLimit.toString() else "2"
                        showStoryDialog = true
                    }
                }
                Note(timerStatus)
            }
        }
        item {
            DashboardPanel("Energie & Display") {
                Text("Display anlassen", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayMode.entries.forEach { option -> Selection(option.label, mode == option) { onMode(option) } }
                }
                Note("Bei Wiedergabe gilt im Kindermodus. Immer an gilt auch bei Pause und im Elternbereich, solange KlangKiste im Vordergrund ist.")
                HorizontalDivider()
                SettingSwitch("Stromsparen beim Umdrehen", "Zeigt eine schwarze Ansicht, wenn das Gerät im Kindermodus mit dem Display nach unten liegt.", settings.ecoMode) { v -> vm.updateSettings { it.copy(ecoMode = v) } }
            }
        }
        item {
            DashboardPanel("Kindermodus & Bibliothek") {
                SettingSwitch("Bibliothek anzeigen", null, settings.libraryEnabled) { v -> vm.updateSettings { it.copy(libraryEnabled = v) } }
                SettingSwitch("Bunte Player-Tasten", null, settings.colorfulControls) { v -> vm.updateSettings { it.copy(colorfulControls = v) } }
                HorizontalDivider()
                Text("Hintergrund", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Selection("KlangKiste-Original", settings.backgroundMode == "default") { vm.updateSettings { it.copy(backgroundMode = "default", backgroundUri = null) } }
                    Selection("Farbe", settings.backgroundMode == "color") { vm.updateSettings { it.copy(backgroundMode = "color") } }
                    Selection("Bild wählen", settings.backgroundMode == "image") { pickBackground.launch(arrayOf("image/*")) }
                }
                if (settings.backgroundMode == "color") OutlinedTextField(settings.backgroundColor, { v -> vm.updateSettings { it.copy(backgroundColor = v) } }, modifier = Modifier.fillMaxWidth(), label = { Text("Hintergrundfarbe, z. B. #FFFFFF") }, singleLine = true)
                HorizontalDivider()
                Text("Altersfilter: ${if (settings.maxAge >= 99) "Alle" else "bis ${settings.maxAge} Jahre"}", style = MaterialTheme.typography.titleSmall)
                Note("Gilt für alle Hörspiele. Titel ohne Altersempfehlung bleiben erlaubt.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(99 to "Alle", 0 to "0 / unbekannt", 4 to "Bis 4", 12 to "Bis 12").forEach { (age, label) ->
                        Selection(label, if (age == 99) settings.maxAge >= 99 else settings.maxAge == age) { vm.updateSettings { it.copy(maxAge = age) } }
                    }
                }
                TextButton(onClick = { showRules = !showRules }) { Text(if (showRules) "Hörregeln ausblenden" else "Hörregeln öffnen") }
            }
        }
        if (showRules) item(key = "listening_rules") { DashboardPanel("Hörregeln") { ListeningRules() } }
        item {
            DashboardPanel("Dateien & System") {
                SettingSwitch("Medien in App-Speicher kopieren", "Dateireferenzen benötigen eine Android-Zugriffsfreigabe. App-interne Kopien werden bei Deinstallation entfernt.", settings.storageMode == "copy") { v -> vm.updateSettings { it.copy(storageMode = if (v) "copy" else "reference") } }
                HorizontalDivider()
                Note(if (tts) "Deutsche Vorlesestimme verfügbar." else "Keine deutsche Vorlesestimme bestätigt.")
                OutlinedButton(onClick = { vm.speakTitle("Guten Abend") }) { Text("Vorlesestimme testen") }
                OutlinedButton(onClick = onPinApp) { Text("Android-App-Pinning starten") }
                Note("Android muss das Anpinnen bestätigen. Ein vollständiger Kioskmodus kann eine Device-Owner-Konfiguration erfordern.")
            }
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Sky, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Note(subtitle)
    }
}

@Composable
private fun DashboardPanel(title: String, accent: Color = Sky, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = PanelShape, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), border = CardDefaults.outlinedCardBorder().copy(width = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Selection(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sky, selectedLabelColor = Color(0xFF17252D)))
}

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Note(subtitle)
        }
        Switch(checked, onChanged)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: KlangKisteViewModel, tracks: List<TrackWithAudio>) {
    val events by vm.events.collectAsState()
    var scope by rememberSaveable { mutableStateOf("day") }
    var showTimeline by rememberSaveable { mutableStateOf(false) }
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val now = System.currentTimeMillis()
    val cutoff = when (scope) { "day" -> today; "week" -> now - 7L * 24 * 60 * 60 * 1000; else -> 0L }
    val filtered = remember(events, cutoff.takeIf { scope != "week" }, scope) { events.filter { it.timestamp >= cutoff } }
    val names = remember(tracks) { tracks.associate { it.track.id to it.track.title } }
    val sessions = remember(filtered) { filtered.filter { it.type in setOf("stop", "app_close", "pause") && it.durationSec.isFinite() && it.durationSec > 0 } }
    val duration = sessions.sumOf { it.durationSec }
    val favorites = remember(sessions) { sessions.filter { !it.trackId.isNullOrBlank() }.groupBy { it.trackId }.mapValues { (_, rows) -> rows.sumOf { it.durationSec } }.toList().sortedByDescending { it.second }.take(5) }
    val dayParts = remember(sessions) {
        val calendar = Calendar.getInstance()
        val totals = DoubleArray(4)
        sessions.forEach { event -> calendar.timeInMillis = event.timestamp; val index = (calendar.get(Calendar.HOUR_OF_DAY) / 6 + 3) % 4; totals[index] += event.durationSec }
        totals
    }
    val complete = filtered.count { it.type == "completed" }
    val scans = filtered.count { it.type == "scan" }
    val stops = filtered.count { it.type == "stop" || it.type == "pause" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("Nutzung & Statistik", "Hörzeiten und Aktivitäten im Überblick") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("day" to "Heute", "week" to "7 Tage", "all" to "Gesamt").forEach { (key, label) -> Selection(label, scope == key) { scope = key } }
            }
        }
        item {
            DashboardPanel("Hörübersicht", Mint) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Hördauer", formatDuration(duration))
                    Metric("Ø pro Sitzung", formatDuration(if (sessions.isEmpty()) 0.0 else duration / sessions.size))
                }
                HorizontalDivider()
                ActivityLine("Vollständig gehört", complete)
            }
        }
        item {
            DashboardPanel("Lieblingshörspiele") {
                val max = favorites.firstOrNull()?.second ?: 0.0
                if (favorites.isEmpty()) Note("Noch keine Hörzeit für einzelne Titel erfasst.")
                favorites.forEach { (id, seconds) -> Meter(names[id] ?: "Unbekannter Titel", seconds, max, Sky) }
            }
        }
        item {
            DashboardPanel("Tageszeit", Amber) {
                val max = dayParts.maxOrNull() ?: 0.0
                listOf("Morgens (6–12 Uhr)", "Mittags (12–18 Uhr)", "Abends (18–24 Uhr)", "Nachts (0–6 Uhr)").forEachIndexed { i, label -> Meter(label, dayParts[i], max, Amber) }
                Note("Die Tageszeit richtet sich nach dem Ende der erfassten Sitzung.")
            }
        }
        item {
            DashboardPanel("Aktivitäten") {
                ActivityLine("NFC-Scans", scans)
                ActivityLine("Pausen / Stopps", stops)
                TextButton(onClick = { showTimeline = !showTimeline }) { Text(if (showTimeline) "Verlauf ausblenden" else "Verlauf anzeigen") }
            }
        }
        if (showTimeline) {
            item { Note(if (filtered.isEmpty()) "Keine Ereignisse im gewählten Zeitraum." else "Verlauf · bis zu 50 Ereignisse") }
            items(filtered.take(50), key = { it.id }) { event ->
                val label = when (event.type) { "play" -> "Wiedergabe"; "stop", "pause" -> "Pause / Stopp"; "completed" -> "Vollständig gehört"; "scan" -> "NFC-Scan"; "app_close" -> "App geschlossen"; else -> event.type }
                DashboardPanel(label) {
                    Note(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.timestamp)))
                    names[event.trackId]?.let { Text(it) }
                    if (event.details.isNotBlank()) Note(event.details)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, color = Mint, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Note(label)
    }
}

@Composable
private fun ActivityLine(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), color = Mint, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Meter(label: String, seconds: Double, maximum: Double, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Note(formatDuration(seconds))
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .13f))) {
            if (seconds > 0 && maximum > 0) Box(Modifier.fillMaxWidth((seconds / maximum).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    return when { total >= 3600 -> "${total / 3600} h ${total / 60 % 60} min"; total >= 60 -> "${total / 60} min"; else -> "$total s" }
}
