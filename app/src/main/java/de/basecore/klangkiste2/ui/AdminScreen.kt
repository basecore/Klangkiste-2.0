package de.basecore.klangkiste2.ui

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.core.ImportFilter
import de.basecore.klangkiste2.data.ImportCandidate
import de.basecore.klangkiste2.data.TrackEntity
import de.basecore.klangkiste2.data.TrackWithAudio

@Composable
fun AdminScreen(vm: KlangKisteViewModel, tracks: List<TrackWithAudio>, onPinApp: () -> Unit) {
    val candidates by vm.candidates.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
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
                    AdminAction("Ordnerstruktur importieren") { smart.launch(null) }
                    AdminAction("Flachen Ordner importieren") { flat.launch(null) }
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
            var menu by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF42494C), contentColor = Color.White)) {
                if (!settings.adminListView) CoverArt(track.coverUri, Modifier.fillMaxWidth().aspectRatio(1f))
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.adminListView) CoverArt(track.coverUri, Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (track.hiddenInLibrary) "Ausgeblendet" else "${item.audioParts.size} Datei(en)", style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) { Text("Menü") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Abspielen") }, enabled = item.audioParts.isNotEmpty(), onClick = { menu = false; vm.playTrack(item) })
                            DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; editing = track })
                            DropdownMenuItem(text = { Text("NFC-Tag zuordnen") }, onClick = { menu = false; vm.beginNfcPairing(track.id) })
                            DropdownMenuItem(text = { Text(if (track.hiddenInLibrary) "Einblenden" else "Ausblenden") }, onClick = { menu = false; vm.toggleHidden(track) })
                            val url = track.remoteAudioUrl
                            if (item.audioParts.isEmpty() && !url.isNullOrBlank()) DropdownMenuItem(text = { Text("Audio herunterladen") }, onClick = {
                                menu = false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { vm.showMessage("Der Downloadlink konnte nicht geöffnet werden.") }
                            })
                            DropdownMenuItem(text = { Text("Löschen") }, onClick = { menu = false; deleting = track })
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
