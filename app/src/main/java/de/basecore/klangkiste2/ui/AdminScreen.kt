package de.basecore.klangkiste2.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(vm: KlangKisteViewModel, tracks: List<TrackWithAudio>, onPinApp: () -> Unit) {
    val candidates by vm.candidates.collectAsState()
    val settings by vm.settings.collectAsState()
    val media: OfflineAudioViewModel = viewModel()
    val downloading by media.active.collectAsState()
    val smart = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> vm.scanFolder(uri, true) } }
    val flat = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> vm.scanFolder(uri, false) } }
    val repair = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> vm.relinkFolder(uri, settings.storageMode == "copy") } }
    val loose = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.scanSelectedFiles(it) }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBackup) }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var deleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var areaDialog by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    val selectedIds = selected.intersect(tracks.mapTo(mutableSetOf()) { it.track.id })
    val knownAreas = remember(tracks) { tracks.flatMap { it.track.libraryAreas() }.distinctBy { it.lowercase() }.sorted() }
    val visible = remember(tracks, query) { tracks.filter { row ->
        val t = row.track
        ImportFilter.matches("${t.title} ${t.description} ${t.genre} ${t.tagsCsv} ${if (t.completed) "gehört" else ""}", t.ageRecommendation, query)
    } }
    fun finishSelection() { selecting = false; selected = emptySet() }
    LazyVerticalGrid(GridCells.Fixed(if (settings.adminListView) 1 else 2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    TextButton(onClick = { vm.updateSettings { it.copy(adminListView = !it.adminListView) } }) { Text(if (settings.adminListView) "Raster" else "Liste") }
                }
                if (visible.isEmpty()) Text(if (tracks.isEmpty()) "Noch keine Hörspiele gespeichert." else "Keine passenden Hörspiele gefunden.")
                if (tracks.isNotEmpty() || selecting) TextButton(onClick = { if (selecting) finishSelection() else selecting = true }) {
                    Text(if (selecting) "Auswahl beenden" else "Mehrere markieren")
                }
                if (selecting) {
                    Text("${selectedIds.size} markiert · ${selectedIds.size - visible.count { it.track.id in selectedIds }} außerhalb der Suche")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { selected = selectedIds + visible.map { it.track.id } }, enabled = visible.isNotEmpty()) { Text("Sichtbare markieren") }
                        TextButton(onClick = { selected = emptySet() }, enabled = selectedIds.isNotEmpty()) { Text("Auswahl leeren") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { areaDialog = true }, enabled = selectedIds.isNotEmpty()) { Text("Bereich zuweisen") }
                        OutlinedButton(onClick = { media.hideTracks(selectedIds, vm::showMessage); finishSelection() }, enabled = selectedIds.isNotEmpty()) { Text("Ausblenden") }
                        OutlinedButton(onClick = { deleteIds = selectedIds }, enabled = selectedIds.isNotEmpty() && selectedIds.none { it in downloading }) { Text("Löschen", color = if (selectedIds.isEmpty()) Color.Unspecified else MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        items(visible, key = { it.track.id }) { item ->
            val track = item.track
            val busy = track.id in downloading
            var menu by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF42494C), contentColor = Color.White)) {
                if (!settings.adminListView) CoverArt(track.coverUri, Modifier.fillMaxWidth().aspectRatio(1f))
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.adminListView && !selecting) CoverArt(track.coverUri, Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (track.completed) Text("✓ Gehört", color = Color(0xFF8FF0AF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(if (busy) "Lädt …" else storageSummary(item), style = MaterialTheme.typography.bodySmall)
                        if (track.hiddenInLibrary) Text("Ausgeblendet", style = MaterialTheme.typography.bodySmall)
                        val areas = track.libraryAreas()
                        if (areas.isNotEmpty()) Text(areas.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (selecting) Checkbox(track.id in selectedIds, { checked -> selected = if (checked) selectedIds + track.id else selectedIds - track.id },
                        modifier = Modifier.semantics { contentDescription = track.title })
                    else Box {
                        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) { Text("Menü") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Abspielen") }, enabled = item.audioParts.isNotEmpty(), onClick = { menu = false; vm.playTrack(item) })
                            DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; editId = track.id })
                            DropdownMenuItem(text = { Text("NFC-Tag zuordnen") }, onClick = { menu = false; vm.beginNfcPairing(track.id) })
                            DropdownMenuItem(text = { Text(if (track.hiddenInLibrary) "Einblenden" else "Ausblenden") }, onClick = { menu = false; vm.toggleHidden(track) })
                            if (item.audioParts.isEmpty() && !track.remoteAudioUrl.isNullOrBlank()) DropdownMenuItem(
                                text = { Text(if (busy) "Audio wird heruntergeladen …" else "Audio herunterladen") }, enabled = !busy,
                                onClick = { menu = false; media.download(track.id, vm::showMessage) })
                            DropdownMenuItem(text = { Text("Löschen") }, enabled = !busy, onClick = { menu = false; deleteIds = setOf(track.id) })
                        }
                    }
                }
            }
        }
    }
    if (candidates.isNotEmpty()) ImportPreview(candidates, settings.storageMode == "copy", vm::clearCandidates, vm::toggleCandidate) { items, copy -> vm.importCandidates(items, copy) }
    tracks.firstOrNull { it.track.id == editId }?.let { item ->
        EditTrack(item, knownAreas, { editId = null }) { edited, cover, remove, areas ->
            media.saveTrack(edited, cover, remove, areas, vm::showMessage); editId = null
        }
    }
    if (areaDialog) AreaDialog(knownAreas, selectedIds.size, { areaDialog = false }) { name ->
        media.assignArea(selectedIds, name, vm::showMessage); areaDialog = false; finishSelection()
    }
    deleteIds?.let { ids ->
        val title = if (ids.size == 1) "Hörspiel löschen?" else "${ids.size} Hörspiele löschen?"
        DeleteConfirmation(title, "Die ausgewählten Einträge und ihre NFC-Zuordnungen werden entfernt. Die Audiodateien auf dem Gerät bleiben erhalten.", { deleteIds = null }) {
            media.deleteTracks(ids, vm::showMessage); deleteIds = null; finishSelection()
        }
    }
    if (clear) DeleteConfirmation("Datenbank leeren?", "Tracks, NFC-Zuordnungen und Verlauf werden gelöscht.", { clear = false }) { vm.clearAll(); clear = false }
}

private fun storageSummary(item: TrackWithAudio): String = when {
    item.audioParts.isEmpty() -> if (item.track.remoteAudioUrl.isNullOrBlank()) "Keine Audiodatei" else "Nicht heruntergeladen"
    item.audioParts.all { it.managedCopy } -> "In der App gespeichert"
    item.audioParts.none { it.managedCopy } -> "Verknüpft"
    else -> "App-Speicher und Verknüpfungen"
}

private fun audioLocation(raw: String): String {
    val uri = Uri.parse(raw)
    if (uri.scheme == "file") return uri.path ?: raw
    if (uri.authority == "com.android.externalstorage.documents") {
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        if (id != null) {
            val volume = id.substringBefore(':')
            val label = if (volume.equals("primary", true)) "Interner gemeinsamer Speicher" else "Datenträger $volume"
            return "$label / ${id.substringAfter(':', "")}\n$raw"
        }
    }
    return raw
}

private fun TrackEntity.ownAreas() = tagsCsv.split('|').map { it.trim() }
    .filter { it.startsWith(AREA_TAG_PREFIX, true) }.map { it.drop(AREA_TAG_PREFIX.length).trim() }.filter { it.isNotBlank() }

private fun TrackEntity.withOwnAreas(areas: List<String>): TrackEntity {
    val tags = tagsCsv.split('|').filter { it.isNotBlank() && !it.trim().startsWith(AREA_TAG_PREFIX, true) }
    return copy(tagsCsv = (tags + areas.distinctBy { it.lowercase() }.map { AREA_TAG_PREFIX + it }).joinToString("|"))
}

private fun validArea(name: String) = name.isNotBlank() && name.length <= 60 && '|' !in name

@Composable private fun AreaDialog(existing: List<String>, count: Int, close: () -> Unit, save: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Bereich zuweisen") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$count markierte Titel einem vorhandenen oder neuen Bereich zuordnen. Andere Bereiche bleiben erhalten.")
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name des Bereichs") }, isError = name.isNotEmpty() && !validArea(name.trim()))
            LazyColumn(Modifier.heightIn(max = 160.dp)) { listItems(existing) { label -> TextButton(onClick = { name = label }) { Text(label) } } }
        }
    }, confirmButton = { Button(onClick = { save(name.trim()) }, enabled = count > 0 && validArea(name.trim())) { Text("Zuweisen") } }, dismissButton = { TextButton(onClick = close) { Text("Abbrechen") } })
}

@Composable private fun ImportHint(text: String) { Text(text, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall) }
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
    var query by rememberSaveable { mutableStateOf("") }; var min by rememberSaveable { mutableStateOf("") }; var max by rememberSaveable { mutableStateOf("") }
    var copy by rememberSaveable { mutableStateOf(defaultCopy) }
    val low = min.toIntOrNull(); val high = max.toIntOrNull()
    val invalid = (min.isNotEmpty() && low == null) || (max.isNotEmpty() && high == null) || (low != null && high != null && low > high)
    val visible = if (invalid) emptyList() else items.filter { ImportFilter.matches("${it.title} ${it.description}", it.ageRecommendation, query, low, high) }
    val selected = items.filter { it.selected }
    AlertDialog(onDismissRequest = close, title = { Text("Import-Auswahl") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Suche (−Begriff ausschließen)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(min, { min = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Ab Jahren") }, singleLine = true, isError = invalid)
                OutlinedTextField(max, { max = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Bis Jahren") }, singleLine = true, isError = invalid)
            }
            Text(if (invalid) "Bitte gültigen Altersbereich eingeben." else "${visible.size} sichtbar · ${selected.size} ausgewählt (${selected.size - visible.count { it.selected }} außerhalb des Filters)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { visible.filterNot { it.selected }.forEach { toggle(it.id) } }) { Text("Sichtbare an") }
                TextButton(onClick = { visible.filter { it.selected }.forEach { toggle(it.id) } }) { Text("Sichtbare aus") }
            }
            LazyColumn(Modifier.weight(1f)) {
                listItems(visible, key = { it.id }) { row -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(row.selected, { toggle(row.id) }); Column { Text(row.title); Text(row.ageRecommendation?.let { "Ab $it J." } ?: "Alter unbekannt", style = MaterialTheme.typography.bodySmall) }
                } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(copy, { copy = it }); Text(if (copy) "Dateien kopieren" else "Dateien verknüpfen")
            }
        }
    }, confirmButton = { Button(enabled = selected.isNotEmpty() && !invalid, onClick = { submit(selected, copy) }) { Text("${selected.size} importieren") } }, dismissButton = { TextButton(onClick = close) { Text("Abbrechen") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun EditTrack(item: TrackWithAudio, knownAreas: List<String>, close: () -> Unit, save: (TrackEntity, Uri?, Boolean, List<String>) -> Unit) {
    val track = item.track
    var title by rememberSaveable(track.id) { mutableStateOf(track.title) }
    var description by rememberSaveable(track.id) { mutableStateOf(track.description) }
    var age by rememberSaveable(track.id) { mutableStateOf(track.ageRecommendation?.toString().orEmpty()) }
    var genre by rememberSaveable(track.id) { mutableStateOf(track.genre) }
    var cover by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var remove by rememberSaveable(track.id) { mutableStateOf(false) }
    var areas by rememberSaveable(track.id) { mutableStateOf(ArrayList(track.ownAreas())) }
    var addArea by rememberSaveable(track.id) { mutableStateOf(false) }
    val automatic = track.withOwnAreas(emptyList()).libraryAreas()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { cover = uri.toString(); remove = false } }
    val preview = if (remove) null else cover ?: track.coverUri
    if (addArea) {
        AreaDialog(knownAreas, 1, { addArea = false }) { name ->
            if ((areas + automatic).none { it.equals(name, true) }) areas = ArrayList(areas + name)
            addArea = false
        }
        return
    }
    AlertDialog(onDismissRequest = close, title = { Text("Eintrag bearbeiten") }, text = {
        LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(preview, Modifier.size(64.dp), "Cover-Vorschau")
                    TextButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text(if (preview == null) "Cover hinzufügen" else "Cover ändern") }
                }
                if (preview != null) TextButton(onClick = { cover = null; remove = true }) { Text("Cover entfernen") }
            }
            item { OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true) }
            item { OutlinedTextField(description, { description = it }, label = { Text("Beschreibung") }) }
            item { OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Alter") }, singleLine = true) }
            item { OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true) }
            item {
                Text("Hörbuchbereiche", fontWeight = FontWeight.Bold)
                if (automatic.isNotEmpty()) Text("Automatisch: ${automatic.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    areas.forEach { name -> TextButton(onClick = { areas = ArrayList(areas.filterNot { it.equals(name, true) }) },
                        modifier = Modifier.semantics { contentDescription = "Bereich $name entfernen" }) { Text("$name ×") } }
                }
                OutlinedButton(onClick = { addArea = true }) { Text("Bereich zuweisen") }
                if (track.completed) Text("✓ Vollständig gehört", fontWeight = FontWeight.Bold)
            }
            item { Text("Audio-Speicherort", fontWeight = FontWeight.Bold); Text(storageSummary(item)) }
            if (item.audioParts.isEmpty() && !track.remoteAudioUrl.isNullOrBlank()) item {
                SelectionContainer { Text(track.remoteAudioUrl.orEmpty(), style = MaterialTheme.typography.bodySmall) }
            }
            listItems(item.audioParts.sortedBy { it.position }) { part ->
                Column {
                    Text(part.displayName, fontWeight = FontWeight.Bold)
                    Text(if (part.managedCopy) "Im App-Speicher" else "Verknüpfung", style = MaterialTheme.typography.bodySmall)
                    SelectionContainer { Text(audioLocation(part.uriString), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank() && (age.isBlank() || age.toIntOrNull() != null), onClick = {
        save(track.copy(title = title.trim(), description = description, ageRecommendation = age.toIntOrNull(), genre = genre.trim()), cover?.let(Uri::parse), remove, areas.toList())
    }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = close) { Text("Abbrechen") } })
}

@Composable private fun DeleteConfirmation(title: String, description: String, close: () -> Unit, delete: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = delete) { Text("Löschen") } }, dismissButton = { TextButton(onClick = close) { Text("Abbrechen") } })
}

internal class OfflineAudioViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = KlangKisteDatabase.get(app)
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active = _active.asStateFlow()

    private fun batch(ids: Set<String>, message: (String) -> Unit, success: (Int) -> String, change: suspend (TrackEntity) -> Boolean) = viewModelScope.launch {
        try {
            val changed = db.withTransaction {
                var count = 0
                for (id in ids) { val entry = db.trackDao().get(id) ?: continue; if (change(entry)) count++ }
                count
            }
            message(if (changed == 0) "Keine Änderung nötig." else success(changed))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message("Aktion fehlgeschlagen: ${e.localizedMessage}") }
    }

    fun hideTracks(ids: Set<String>, message: (String) -> Unit) = batch(ids, message, { "$it Titel ausgeblendet." }) { track ->
        if (track.hiddenInLibrary) false else { db.trackDao().upsert(track.copy(hiddenInLibrary = true)); true }
    }

    fun deleteTracks(ids: Set<String>, message: (String) -> Unit) = batch(ids, message, { "$it Einträge gelöscht." }) { track ->
        if (track.id in _active.value) false else { db.trackDao().delete(track.id); true }
    }

    fun assignArea(ids: Set<String>, name: String, message: (String) -> Unit) {
        val label = name.trim()
        if (!validArea(label)) return
        batch(ids, message, { "$it Titel dem Bereich „$label“ zugeordnet." }) { track ->
            if (track.libraryAreas().any { it.equals(label, true) }) false
            else { db.trackDao().upsert(track.withOwnAreas(track.ownAreas() + label)); true }
        }
    }

    fun saveTrack(edited: TrackEntity, cover: Uri?, remove: Boolean, areas: List<String>, message: (String) -> Unit) = viewModelScope.launch {
        var newFile: File? = null
        var committed = false
        try {
            val name = if (cover == null) null else withContext(Dispatchers.IO) {
                val resolver = app.contentResolver
                val filename = runCatching { resolver.query(cover, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull() ?: "cover"
                val safe = filename.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
                val file = File.createTempFile("edited_", "_$safe", File(app.filesDir, "klangkiste/covers").apply { mkdirs() })
                newFile = file
                val source = resolver.openInputStream(cover) ?: error("Bilddatei nicht zugänglich")
                source.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        ensureActive(); val count = input.read(buffer); if (count < 0) break
                        total += count
                        if (total > 16L * 1024 * 1024) error("Das Cover darf höchstens 16 MB groß sein")
                        output.write(buffer, 0, count)
                    }
                } }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Das Bildformat wird nicht unterstützt")
                filename
            }
            val newUri = newFile?.let { Uri.fromFile(it).toString() }
            ensureActive()
            withContext(NonCancellable) {
                db.withTransaction {
                    val latest = db.trackDao().get(edited.id) ?: error("Eintrag nicht gefunden")
                    val updated = if (areas == edited.ownAreas()) latest else latest.withOwnAreas(areas)
                    db.trackDao().upsert(updated.copy(title = edited.title, description = edited.description, ageRecommendation = edited.ageRecommendation, genre = edited.genre,
                        coverUri = if (remove) null else newUri ?: latest.coverUri, coverFileName = if (remove) null else name ?: latest.coverFileName))
                }
                committed = true
            }
            message("Änderungen gespeichert.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message("Speichern fehlgeschlagen: ${e.localizedMessage}") }
        finally { if (!committed) withContext(NonCancellable + Dispatchers.IO) { newFile?.delete() } }
    }

    fun download(id: String, message: (String) -> Unit) = viewModelScope.launch {
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
                val file = File.createTempFile("download_", suffix, File(app.filesDir, "klangkiste/audio").apply { mkdirs() })
                target = file
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                try {
                    val status = conn.responseCode
                    if (status !in 200..299 || conn.url.protocol != "https") error("Unerwartete HTTP-Antwort: $status")
                    val type = conn.contentType.orEmpty().substringBefore(';').trim().lowercase()
                    if (type.startsWith("text/") || type == "application/json" || type == "application/xhtml+xml") error("Der Link liefert keine Audiodatei")
                    conn.inputStream.use { input -> file.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) { ensureActive(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
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
