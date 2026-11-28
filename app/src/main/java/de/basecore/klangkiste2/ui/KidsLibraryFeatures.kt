package de.basecore.klangkiste2.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Folder
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
import de.basecore.klangkiste2.data.TrackWithAudio
import java.util.Locale

internal class KidsLibraryPreferences(private val preferences: SharedPreferences) {
    private val randomEnabledState = mutableStateOf(preferences.getBoolean("random_enabled", false))
    val randomEnabled: Boolean get() = randomEnabledState.value
    private val covers = mutableStateMapOf<String, String>().apply {
        preferences.all.forEach { (key, value) ->
            if (key.startsWith("cover:") && value is String) put(key.removePrefix("cover:"), value)
        }
    }

    fun setRandomEnabled(value: Boolean) {
        randomEnabledState.value = value
        preferences.edit().putBoolean("random_enabled", value).apply()
    }

    fun cover(key: String): String? = covers[key]

    fun setCover(key: String, uri: String?) {
        val editor = preferences.edit()
        if (uri == null) {
            covers.remove(key)
            editor.remove("cover:$key")
        } else {
            covers[key] = uri
            editor.putString("cover:$key", uri)
        }
        editor.apply()
    }
}

@Composable
internal fun rememberKidsLibraryPreferences(): KidsLibraryPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        KidsLibraryPreferences(context.getSharedPreferences("kids_library", Context.MODE_PRIVATE))
    }
}

internal fun TrackWithAudio.isKidsRandomCandidate(maxAge: Int): Boolean =
    !track.hiddenInLibrary && audioParts.isNotEmpty() && (track.ageRecommendation?.let { it <= maxAge } ?: true)

internal data class KidsArea(val key: String, val label: String, val count: Int, val previewCovers: List<String>) {
    val fallbackCover: String? get() = previewCovers.firstOrNull()
}

internal fun kidsAreas(tracks: List<TrackWithAudio>): List<KidsArea> {
    val entries = tracks.sortedBy { it.track.title.lowercase(Locale.ROOT) }
        .flatMap { item -> item.track.libraryAreas().map { label -> label to item.track.coverUri } }
    return entries.groupBy { (label, _) -> label.lowercase(Locale.ROOT) }
        .map { (key, rows) ->
            val covers = rows.asSequence().mapNotNull { it.second?.takeIf(String::isNotBlank) }.distinct().take(4).toList()
            KidsArea(key, rows.first().first, rows.size, covers)
        }
        .sortedBy { it.key }
}

@Composable
internal fun RandomStoryButton(enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFF1C40F), contentColor = Color(0xFF2C3E50))) {
        Icon(Icons.Default.Casino, contentDescription = "Zufälliges Hörspiel starten", modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun AreaPreviewCell(uri: String?, modifier: Modifier, ink: Color) {
    if (uri != null) CoverArt(uri, modifier, "Folgen-Cover")
    else Box(modifier.background(Color(0xFFE8F7F9), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Folder, null, Modifier.size(26.dp), tint = ink.copy(alpha = .65f))
    }
}

@Composable
private fun AreaCoverPreview(area: KidsArea, customCover: String?, ink: Color) {
    when {
        customCover != null -> CoverArt(customCover, Modifier.fillMaxSize(), "Cover für ${area.label}")
        area.count <= 1 -> AreaPreviewCell(area.fallbackCover, Modifier.fillMaxSize(), ink)
        area.count == 2 -> Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) { index ->
                AreaPreviewCell(area.previewCovers.getOrNull(index), Modifier.weight(1f).fillMaxHeight(), ink)
            }
        }
        else -> Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) { column ->
                        AreaPreviewCell(area.previewCovers.getOrNull(row * 2 + column), Modifier.weight(1f).fillMaxHeight(), ink)
                    }
                }
            }
        }
    }
}

@Composable
internal fun KidsAreaTile(area: KidsArea, customCover: String?, onOpen: () -> Unit) {
    val ink = Color(0xFF2C3E50)
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = ink)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFCEDDE6))) {
            AreaCoverPreview(area, customCover, ink)
            Surface(Modifier.align(Alignment.BottomEnd).padding(7.dp), shape = RoundedCornerShape(12.dp), color = ink, contentColor = Color.White) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Folder, null, Modifier.size(17.dp))
                    Text(area.count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(area.label, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (area.count == 1) "1 Hörspiel" else "Sammlung · ${area.count} Hörspiele", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AreaCoverSettings(tracks: List<TrackWithAudio>, preferences: KidsLibraryPreferences) {
    val context = LocalContext.current
    val areas = remember(tracks) { kidsAreas(tracks) }
    var expanded by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf(false) }
    val area = areas.firstOrNull { it.key == selected } ?: areas.firstOrNull()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = picking
        picking = null
        if (key != null && uri != null) {
            error = runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isFailure
            if (!error) preferences.setCover(key, uri.toString())
        }
    }
    Text("Cover für Hörbuchbereiche", style = MaterialTheme.typography.titleSmall)
    if (area == null) {
        Text("Noch keine Hörbuchbereiche vorhanden. Weise einem Titel zuerst im Elternbereich einen Bereich zu.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(area.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(expanded, { expanded = false }) {
            areas.forEach { candidate -> DropdownMenuItem(text = { Text(candidate.label) }, onClick = {
                selected = candidate.key
                expanded = false
                error = false
            }) }
        }
    }
    val custom = preferences.cover(area.key)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(56.dp)) { AreaCoverPreview(area, custom, Color(0xFF2C3E50)) }
        OutlinedButton(onClick = { error = false; picking = area.key; picker.launch(arrayOf("image/*")) }) { Text("Übercover wählen") }
    }
    if (custom != null) TextButton(onClick = { preferences.setCover(area.key, null); error = false }) { Text("Eigenes Cover entfernen") }
    if (error) Text("Die Bilddatei konnte nicht dauerhaft verknüpft werden. Wähle sie erneut aus dem Gerätespeicher.", color = MaterialTheme.colorScheme.error)
}
