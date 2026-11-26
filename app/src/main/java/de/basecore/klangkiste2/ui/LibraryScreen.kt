package de.basecore.klangkiste2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.core.ImportFilter
import de.basecore.klangkiste2.data.*

private val LibraryNavy = Color(0xFF2C3E50)
private val LibraryGreen = Color(0xFF2ECC71)
private val LibraryRed = Color(0xFFFF4757)
internal const val AREA_TAG_PREFIX = "Bereich:"

internal fun TrackEntity.libraryAreas(): List<String> = buildList {
    val tags = tagsCsv.split('|').map { it.trim() }
    if (title.contains("Bibi Blocksberg", true) || tags.any { it.equals("Bibi Blocksberg", true) }) add("Bibi Blocksberg")
    if (tags.any { it.equals("OHRKA", true) } || id.startsWith("ohrka-", true)) add("OHRKA")
    tags.filter { it.startsWith(AREA_TAG_PREFIX, true) }.map { it.drop(AREA_TAG_PREFIX.length).trim() }
        .filter { it.isNotBlank() }.forEach { add(it) }
}.distinctBy { it.lowercase() }

@Composable
fun LibraryScreen(vm: KlangKisteViewModel, tracks: List<TrackWithAudio>, settings: AppSettingsEntity, childMode: Boolean, onBack: (() -> Unit)?) {
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf<String?>(null) }
    var area by rememberSaveable { mutableStateOf<String?>(null) }
    var childMaxAge by remember { mutableStateOf(99) }
    var ageMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<TrackEntity?>(null) }
    val listMode = settings.adminListView && !childMode
    val ageLimit = if (childMode) minOf(settings.maxAge, childMaxAge) else settings.maxAge
    val available = remember(tracks, childMode, settings.maxAge) {
        tracks.filter { val t = it.track; (!childMode || !t.hiddenInLibrary) && (t.ageRecommendation == null || t.ageRecommendation <= settings.maxAge) }
    }
    val areasById = remember(available) { available.associate { it.track.id to it.track.libraryAreas() } }
    val areas = remember(areasById) { areasById.values.flatten().distinctBy { it.lowercase() }.sortedBy { it.lowercase() } }
    val selectedArea = areas.firstOrNull { it.equals(area, true) }
    val genres = remember(available) { available.map { it.track.genre }.filter { it.isNotBlank() }.distinct().sorted() }
    val selectedGenre = genre?.takeIf { it in genres }
    val visible = remember(available, query, selectedGenre, ageLimit, selectedArea, areasById) {
        available.filter { item ->
            val t = item.track
            (selectedArea == null || areasById[t.id].orEmpty().any { it.equals(selectedArea, true) }) &&
                (selectedGenre == null || selectedGenre == t.genre) && (t.ageRecommendation == null || t.ageRecommendation <= ageLimit) &&
                ImportFilter.matches("${t.title} ${t.description} ${t.genre} ${t.tagsCsv}", t.ageRecommendation, query)
        }.sortedBy { it.track.title.lowercase() }
    }
    val recent = remember(visible, childMode, selectedArea) {
        if (childMode && selectedArea == null) visible.filter { it.track.lastPlayedAt > 0 }.sortedByDescending { it.track.lastPlayedAt }.take(3) else emptyList()
    }
    fun play(item: TrackWithAudio) { vm.playTrack(item); if (childMode) onBack?.invoke() }
    Box(Modifier.fillMaxSize().background(LibraryNavy)) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = if (childMode) 44.dp else 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bibliothek", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (!childMode) TextButton(onClick = { vm.updateSettings { it.copy(adminListView = !it.adminListView) } }) {
                    Text(if (listMode) "Raster" else "Liste", color = Color.White)
                }
            }
            if (!childMode) TextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Titel, Beschreibung, Genre oder Tag") })
            if (areas.isNotEmpty()) {
                SectionLabel("Hörbuchbereiche")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { LibraryChip("Alle Bereiche", selectedArea == null) { area = null } }
                    items(areas) { label -> LibraryChip(label, selectedArea == label) { area = label } }
                }
            }
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { LibraryChip("Alle Genres", selectedGenre == null) { genre = null } }
                item {
                    Box {
                        LibraryChip(if (ageLimit >= 99) "Alle Alter" else "Bis $ageLimit J.", ageLimit < 99) { ageMenu = true }
                        DropdownMenu(ageMenu, { ageMenu = false }) {
                            (listOf(99) + (0..12)).filter { !childMode || it == 99 || it <= settings.maxAge }.forEach { age ->
                                DropdownMenuItem(text = { Text(when (age) { 99 -> if (childMode) "Kein zusätzlicher Altersfilter" else "Alle Alter"; 0 -> "0 J. / ohne Angabe"; else -> "Bis $age Jahre" }) }, onClick = {
                                    if (childMode) childMaxAge = age else vm.updateSettings { it.copy(maxAge = age) }
                                    ageMenu = false
                                })
                            }
                        }
                    }
                }
                items(genres) { g -> LibraryChip(g, selectedGenre == g) { genre = g } }
            }
            if (visible.isEmpty()) Text("Keine passenden Inhalte gefunden.", color = Color.White, modifier = Modifier.padding(vertical = 16.dp))
            else if (listMode) LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.track.id }) { item ->
                    Card(onClick = { play(item) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoverArt(item.track.coverUri, Modifier.size(52.dp), item.track.title)
                            Column(Modifier.weight(1f)) {
                                Text(item.track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(trackMeta(item.track), fontSize = 12.sp)
                            }
                            TextButton(onClick = { info = item.track }) { Text("Info") }
                        }
                    }
                }
            } else LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = if (childMode && onBack != null) 80.dp else 16.dp)) {
                if (recent.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("Zuletzt gehört")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recent, key = { it.track.id }) { item -> LibraryTile(item.track, Modifier.width(120.dp), { play(item) }, { info = item.track }) }
                        }
                    }
                }
                if (childMode || selectedArea != null) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(selectedArea ?: "Alle Hörspiele") }
                items(visible, key = { it.track.id }) { item -> LibraryTile(item.track, Modifier.fillMaxWidth(), { play(item) }, { info = item.track }) }
            }
        }
        if (childMode && onBack != null) Button(onClick = onBack,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp).heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LibraryRed, contentColor = Color.White)) { Text("Schließen", fontWeight = FontWeight.Bold) }
    }
    info?.let { track ->
        AlertDialog(onDismissRequest = { info = null }, containerColor = Color.White, titleContentColor = LibraryNavy, textContentColor = LibraryNavy,
            title = { Text(track.title, fontWeight = FontWeight.Bold) }, text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoverArt(track.coverUri, Modifier.size(120.dp), track.title)
                    val meta = trackMeta(track)
                    if (meta.isNotBlank()) Text(meta, fontSize = 13.sp)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 170.dp).background(Color(0xFFF7F9FA), RoundedCornerShape(10.dp))) {
                        item { Text(track.description.ifBlank { "Keine Beschreibung vorhanden." }, Modifier.padding(12.dp)) }
                    }
                }
            }, confirmButton = { Button(onClick = { info = null }, colors = ButtonDefaults.buttonColors(containerColor = LibraryGreen, contentColor = LibraryNavy)) { Text("Schließen") } })
    }
}

@Composable private fun LibraryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(containerColor = Color.White.copy(alpha = .20f), labelColor = Color.White,
            selectedContainerColor = LibraryGreen, selectedLabelColor = LibraryNavy))
}

@Composable private fun SectionLabel(label: String) { Text(label, color = Color(0xFFDFE6E9), fontSize = 18.sp, fontWeight = FontWeight.Bold) }

@Composable private fun LibraryTile(track: TrackEntity, modifier: Modifier, onPlay: () -> Unit, onInfo: () -> Unit) {
    Card(onClick = onPlay, modifier = modifier, shape = RoundedCornerShape(15.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = LibraryNavy)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            CoverArt(track.coverUri, Modifier.fillMaxSize(), track.title)
            IconButton(onClick = onInfo, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp).background(Color.White.copy(alpha = .94f), CircleShape)) {
                Text("ⓘ", color = Color(0xFF21618C), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            if (track.completed) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).background(LibraryGreen, CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = LibraryNavy, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = trackMeta(track)
            if (meta.isNotBlank()) Text(meta, color = Color(0xFF58636A), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun trackMeta(track: TrackEntity): String = listOfNotNull(
    track.ageRecommendation?.let { "Ab $it J." }, track.genre.takeIf { it.isNotBlank() }, track.runtimeMinutes?.let { "$it min" }
).joinToString(" · ")
