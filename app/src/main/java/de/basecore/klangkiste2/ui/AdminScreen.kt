package de.basecore.klangkiste2.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.core.ImportFilter
import de.basecore.klangkiste2.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AdminScreen(vm: KlangKisteViewModel, tracks: List<TrackWithAudio>, onPinApp: () -> Unit) {
    val candidates by vm.candidates.collectAsState()
    val settings by vm.settings.collectAsState()
    val downloader: OfflineAudioViewModel = viewModel()
    val downloading by downloader.active.collectAsState()
    val smart = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> vm.scanFolder(uri, true) } }
    val flat = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> vm.scanFolder(uri, false) } }
    val repair = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> vm.relinkFolder(uri, settings.storageMode == "copy") } }
    val loose = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.scanSelectedFiles(it) }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBackup) }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<TrackEntity?>(null) }
    var deleting by remember { mutableStateOf<TrackEntity?>(null) }
    var clear by remember { mutableStateOf(false) }
    val visible = remember(tracks, query) { tracks.filter { row ->
        val t = row.track
        ImportFilter.matches(listOf(t.title, t.description, t.genre, t.tagsCsv).joinToString(" "), t.ageRecommendation, query)
    } }
    LazyVerticalGrid(columns = GridCells.Fixed(if (settings.adminListView) 1 else 2),
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Elternbereich", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF74B9FF))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Hörspiele suchen") })
                AdminSection("Inhalte hinzufügen", section == "import", { section = if (section == "import") "" else "import" }) {
                    AdminAction("Kapitel je Unterordner bündeln") { smart.launch(null) }
                    ImportHint("Grüffelo/01.mp3 und Grüffelo/02.mp3 ergeben zusammen das Hörspiel „Grüffelo“. Dateien direkt im ausgewählten Ordner bleiben einzelne Titel.")
                    AdminAction("Titel nach Dateiname importieren") { flat.launch(null) }
                    ImportHint("Grüffelo.mp3 und Pettersson.mp3 ergeben zwei Titel, auch im selben Ordner. Gleiche Grundnamen im selben Ordner werden gebündelt. Beide Varianten durchsuchen Unterordner.")
                    AdminAction("Dateien auswählen") { loose.launch(arrayOf("audio/*", "image/*", "application/json", "text/plain")) }
                    AdminAction("OHRKA installieren", vm::installOhrkaCatalog)
                    AdminAction("Online-Beispiele laden", vm::loadOnlineExamples)
                }
                AdminSection("Datenbank und Backup", section == "backup", { section = if (section == "backup") "" else "backup" }) {
                    AdminAction("Datenbank laden") { openJson.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    AdminAction("Datenbank exportieren") { saveJson.launch("klangkiste_full_backup.json") }
                    AdminAction("Fehlende Audiodateien verknüpfen") { repair.launch(null) }
                    AdminAction("Datenbank leeren") { clear = true }
                }
                AdminSection("Sichtbarkeit und Kindersicherung", section == "control", { section = if (section == "control") "" else "control" }) {
                    AdminAction("Alle Hörspiele einblenden") { vm.setAllHidden(false) }
                    AdminAction("Alle Hörspiele ausblenden") { vm.setAllHidden(true) }
                    AdminAction("Android-App-Pinning starten", onPinApp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Gespeicherte Inhalte", fontWeight = FontWeight.Bold)
                        Text("${visible.size} von ${tracks.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.updateSettings { it.copy(adminListView = !it.adminListView) } }) {
                        Text(if (settings.adminListView) "Raster" else "Liste")
                    }
                }
                if (visible.isEmpty()) Text(if (tracks.isEmpty()) "Noch keine Hörspiele gespeichert." else "Keine passenden Hörspiele gefunden.")
            }
        }
        items(visible, key = { it.track.id }) { item ->
            val track = item.track
            val busy = track.id in downloading
            var menu by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF42494C), contentColor = Color.White)) {
                if (!settings.adminListView) CoverArt(track.coverUri, Modifier.fillMaxWidth().aspectRatio(1f))
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.adminListView) CoverArt(track.coverUri, Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(when { busy -> "Lädt …"; track.hiddenInLibrary -> "Ausgeblendet"; else -> "${item.audioParts.size} Datei(en)" }, style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) { Text("Menü") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Abspielen") }, enabled = item.audioParts.isNotEmpty(), onClick = { menu = false; vm.playTrack(item) })
                            DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; editing = track })
                            DropdownMenuItem(text = { Text("NFC-Tag zuordnen") }, onClick = { menu = false; vm.beginNfcPairing(track.id) })
                            DropdownMenuItem(text = { Text(if (track.hiddenInLibrary) "Einblenden" else "Ausblenden") }, onClick = { menu = false; vm.toggleHidden(track) })
                            if (item.audioParts.isEmpty() && !track.remoteAudioUrl.isNullOrBlank()) {
                                DropdownMenuItem(text = { Text(if (busy) "Audio wird heruntergeladen …" else "Audio herunterladen") }, enabled = !busy, onClick = {
                                    menu = false
                                    downloader.download(track.id, vm::showMessage)
                                })
                            }
                            DropdownMenuItem(text = { Text("Löschen") }, enabled = !busy, onClick = { menu = false; deleting = track })
                        }
                    }
                }
            }
        }
    }
    if (candidates.isNotEmpty()) ImportPreview(candidates, settings.storageMode == "copy", vm::clearCandidates, vm::toggleCandidate) { items, copy -> vm.importCandidates(items, copy) }
    editing?.let { EditTrack(it, { editing = null }) { edited -> vm.saveEditedTrack(edited); editing = null } }
    deleting?.let { DeleteConfirmation("Hörspiel löschen?", it.title, { deleting = null }) { vm.deleteTrack(it); deleting = null } }
    if (clear) DeleteConfirmation("Datenbank leeren?", "Tracks, NFC-Zuordnungen und Verlauf werden gelöscht.", { clear = false }) { vm.clearAll(); clear = false }
}

@Composable private fun ImportHint(text: String) {
    Text(text, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
}

@Composable private fun AdminSection(title: String, open: Boolean, toggle: () -> Unit, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF42494C), contentColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            TextButton(onClick = toggle, modifier = Modifier.fillMaxWidth()) { Text((if (open) "▾  " else "▸  ") + title) }
            if (open) content()
        }
    }
}

@Composable private fun AdminAction(title: String, run: () -> Unit) {
    OutlinedButton(onClick = run, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) { Text(title) }
}

@Composable private fun ImportPreview(items: List<ImportCandidate>, defaultCopy: Boolean, close: () -> Unit, toggle: (String) -> Unit, submit: (List<ImportCandidate>, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var min by rememberSaveable { mutableStateOf("") }
    var max by rememberSaveable { mutableStateOf("") }
    var copy by rememberSaveable { mutableStateOf(defaultCopy) }
    val low = min.toIntOrNull()
    val high = max.toIntOrNull()
    val invalid = (min.isNotEmpty() && low == null) || (max.isNotEmpty() && high == null) || (low != null && high != null && low > high)
    val visible = if (invalid) emptyList() else items.filter { ImportFilter.matches("${it.title} ${it.description}", it.ageRecommendation, query, low, high) }
    val selected = items.filter { it.selected }
    val hiddenSelected = selected.size - visible.count { it.selected }
    AlertDialog(onDismissRequest = close, title = { Text("Import-Auswahl") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Suche (−Begriff ausschließen)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(min, { min = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Ab Jahren") }, singleLine = true, isError = invalid)
                OutlinedTextField(max, { max = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Bis Jahren") }, singleLine = true, isError = invalid)
            }
            Text(if (invalid) "Bitte gültigen Altersbereich eingeben." else "${visible.size} sichtbar · ${selected.size} ausgewählt ($hiddenSelected außerhalb des Filters)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { visible.filterNot { it.selected }.forEach { toggle(it.id) } }) { Text("Sichtbare an") }
                TextButton(onClick = { visible.filter { it.selected }.forEach { toggle(it.id) } }) { Text("Sichtbare aus") }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { it.id }) { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(row.selected, { toggle(row.id) })
                        Column {
                            Text(row.title)
                            Text(row.ageRecommendation?.let { "Ab $it J." } ?: "Alter unbekannt", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(copy, { copy = it }); Text(if (copy) "Dateien kopieren" else "Dateien verknüpfen")
            }
        }
    }, confirmButton = { Button(enabled = selected.isNotEmpty() && !invalid, onClick = { submit(selected, copy) }) { Text("${selected.size} importieren") } },
        dismissButton = { TextButton(onClick = close) { Text("Abbrechen") } })
}

@Composable private fun EditTrack(track: TrackEntity, close: () -> Unit, save: (TrackEntity) -> Unit) {
    var title by rememberSaveable(track.id) { mutableStateOf(track.title) }
    var description by rememberSaveable(track.id) { mutableStateOf(track.description) }
    var age by rememberSaveable(track.id) { mutableStateOf(track.ageRecommendation?.toString().orEmpty()) }
    var genre by rememberSaveable(track.id) { mutableStateOf(track.genre) }
    AlertDialog(onDismissRequest = close, title = { Text("Eintrag bearbeiten") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true) }
            item { OutlinedTextField(description, { description = it }, label = { Text("Beschreibung") }) }
            item { OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Alter") }, singleLine = true) }
            item { OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true) }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank() && (age.isBlank() || age.toIntOrNull() != null), onClick = { save(track.copy(title = title.trim(), description = description, ageRecommendation = age.toIntOrNull(), genre = genre.trim())) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = close) { Text("Abbrechen") } })
}

@Composable private fun DeleteConfirmation(title: String, description: String, close: () -> Unit, delete: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = delete) { Text("Löschen") } }, dismissButton = { TextButton(onClick = close) { Text("Abbrechen") } })
}

internal class OfflineAudioViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = KlangKisteDatabase.get(app)
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active = _active.asStateFlow()

    fun download(id: String, message: (String) -> Unit) {
        viewModelScope.launch {
            if (id in _active.value) return@launch
            _active.value = _active.value + id
            var target: File? = null
            var linked = false
            try {
                val track = db.trackDao().get(id) ?: error("Eintrag nicht gefunden")
                if (db.audioPartDao().getForTrack(id).isNotEmpty()) return@launch
                val url = track.remoteAudioUrl?.takeIf { it.startsWith("https://") } ?: error("Kein HTTPS-Audiolink vorhanden")
                val expected = runCatching { JSONArray(track.expectedAudioNamesJson).optString(0) }.getOrDefault("")
                val name = expected.ifBlank { Uri.parse(url).lastPathSegment.orEmpty().ifBlank { "audio.mp3" } }
                val suffix = "_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(90)
                withContext(Dispatchers.IO) {
                    val dir = File(app.filesDir, "klangkiste/audio").apply { mkdirs() }
                    val file = File.createTempFile("download_", suffix, dir)
                    target = file
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    try {
                        val status = conn.responseCode
                        if (status !in 200..299 || conn.url.protocol != "https") error("Unerwartete HTTP-Antwort: $status")
                        val type = conn.contentType.orEmpty().substringBefore(';').trim().lowercase()
                        if (type.startsWith("text/") || type == "application/json" || type == "application/xhtml+xml") error("Der Link liefert keine Audiodatei")
                        conn.inputStream.use { input -> file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val bytes = input.read(buffer)
                                if (bytes < 0) break
                                output.write(buffer, 0, bytes)
                            }
                        } }
                        val length = conn.getHeaderField("Content-Length")?.toLongOrNull()
                        if (file.length() == 0L || (length != null && file.length() != length)) error("Download unvollständig")
                    } finally { conn.disconnect() }
                }
                val part = AudioPartEntity(trackId = id, position = 0, uriString = Uri.fromFile(checkNotNull(target)).toString(), displayName = name, managedCopy = true)
                ensureActive()
                withContext(NonCancellable) {
                    linked = db.withTransaction {
                        if (db.trackDao().get(id)?.remoteAudioUrl != url || db.audioPartDao().getForTrack(id).isNotEmpty()) false
                        else { db.audioPartDao().upsertAll(listOf(part)); true }
                    }
                }
                message(if (linked) "„${track.title}“ ist jetzt offline verfügbar." else "Eintrag wurde geändert; Audio nicht hinzugefügt.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("Audio-Download fehlgeschlagen: ${e.localizedMessage ?: "Unbekannter Fehler"}") }
            finally {
                if (!linked) withContext(NonCancellable + Dispatchers.IO) { target?.delete() }
                _active.value = _active.value - id
            }
        }
    }
}
