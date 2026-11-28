package de.basecore.klangkiste2.ui

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.basecore.klangkiste2.KlangKisteViewModel
import de.basecore.klangkiste2.R
import de.basecore.klangkiste2.data.AppSettingsEntity
import de.basecore.klangkiste2.data.TrackWithAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF2C3E50)
private val Green = Color(0xFF2ECC71)
private val Blue = Color(0xFF3498DB)
private val Gray = Color(0xFFDFE4EA)
private val Red = Color(0xFFFF4757)
private val Yellow = Color(0xFFF1C40F)

@Composable
fun PlayerScreen(vm: KlangKisteViewModel, active: TrackWithAudio?, settings: AppSettingsEntity, childMode: Boolean, onOpenLibrary: () -> Unit) {
    val playing by vm.isPlaying.collectAsState()
    val pos by vm.positionMs.collectAsState()
    val duration by vm.durationMs.collectAsState()
    val volume by vm.volume.collectAsState()
    val sleepSeconds by vm.sleepSeconds.collectAsState()
    val title = active?.track?.title ?: "Bereit zum Spielen!"
    val background = remember(settings.backgroundColor) { runCatching { Color(AndroidColor.parseColor(settings.backgroundColor)) }.getOrDefault(Navy) }
    val shape = RoundedCornerShape(18.dp)
    val level = volume.coerceIn(0f, 1f)
    val levelColor = when { level >= .849f -> Red; level >= .549f -> Yellow; else -> Green }
    val colorful = childMode && settings.colorfulControls
    Box(Modifier.fillMaxSize().background(if (childMode && settings.backgroundMode == "color") background else Navy)) {
        if (childMode && settings.backgroundMode != "color") {
            val uri = settings.backgroundUri
            if (settings.backgroundMode == "image" && !uri.isNullOrBlank()) FullBackground(uri)
            else Image(painterResource(R.drawable.kids_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        BoxWithConstraints(Modifier.fillMaxSize().then(if (childMode) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)) {
            val usableWidth = (maxWidth - 28.dp).coerceAtLeast(1.dp)
            val cover = minOf(usableWidth * .53f, maxHeight * .27f, 270.dp)
            val primary = (usableWidth * .29f).coerceIn(64.dp, 104.dp)
            val secondary = (usableWidth * .19f).coerceIn(48.dp, 66.dp)
            val transport = minOf(secondary, ((usableWidth - 24.dp) / 4).coerceAtLeast(1.dp))
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = if (childMode) maxHeight else 0.dp)
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.fillMaxWidth().widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if ((childMode && settings.libraryEnabled) || sleepSeconds > 0) {
                        Row(Modifier.fillMaxWidth().padding(end = if (childMode) 60.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (childMode && settings.libraryEnabled) RoundControl(Icons.Default.LibraryMusic, "Bibliothek", 48.dp, Color.White.copy(alpha = .88f), Navy, onOpenLibrary)
                            Spacer(Modifier.weight(1f))
                            if (sleepSeconds > 0) SleepTimerBadge(sleepSeconds)
                        }
                    } else if (childMode) Spacer(Modifier.height(36.dp))
                    Text(title, Modifier.fillMaxWidth().background(Color.White.copy(alpha = .85f), RoundedCornerShape(14.dp)).padding(8.dp),
                        color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = if (childMode) 26.sp else 22.sp,
                        textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    active?.track?.let { track ->
                        val meta = listOfNotNull(track.genre, track.ageRecommendation?.let { "Ab $it J." }, track.runtimeMinutes?.let { "$it min" }).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, Modifier.background(Color.White.copy(alpha = .88f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
                            color = Navy, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    Box(Modifier.size(cover).shadow(7.dp, shape).clip(shape).background(Gray).border(3.dp, Color.White, shape)) {
                        CoverArt(active?.track?.coverUri, Modifier.fillMaxSize(), title)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundControl(Icons.Default.Remove, "Leiser", secondary, if (colorful) Yellow else Gray, Navy) { vm.adjustVolume(-.1f) }
                        RoundControl(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Wiedergabe",
                            primary, if (childMode) Red else Green, Color.White, vm::togglePlay)
                        RoundControl(Icons.Default.Add, "Lauter", secondary, if (colorful) Red else Gray, if (colorful) Color.White else Navy) { vm.adjustVolume(.1f) }
                    }
                    LinearProgressIndicator(progress = { level }, modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(.85f).height(16.dp).clip(CircleShape)
                        .semantics { contentDescription = "Lautstärke" }, color = levelColor, trackColor = Color.White.copy(alpha = .78f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundControl(Icons.Default.SkipPrevious, "Von vorn", transport, if (colorful) Blue else Gray, if (colorful) Color.White else Navy, vm::restartPlaylist)
                        RoundControl(Icons.Default.FastRewind, "30 Sekunden zurück", transport, if (colorful) Color(0xFFE17055) else Gray, if (colorful) Color.White else Navy, vm::skipBack)
                        RoundControl(Icons.Default.FastForward, "30 Sekunden vor", transport, if (colorful) Color(0xFF9B59B6) else Gray, if (colorful) Color.White else Navy, vm::skipForward)
                        RoundControl(Icons.Default.SkipNext, "Nächstes Kapitel oder nächste Datei", transport, if (colorful) Color(0xFF00CEC9) else Gray, Navy, vm::nextFileOrCue)
                    }
                }
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(.92f).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val clamped = pos.coerceIn(0L, duration.coerceAtLeast(0L))
                    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .9f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (childMode && duration > 0) "−${formatTime(duration - clamped)}" else "${formatTime(clamped)} / ${formatTime(duration)}",
                            color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Slider(value = clamped.toFloat(), onValueChange = { vm.seekTo(it.toLong()) }, enabled = duration > 0,
                            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                            modifier = Modifier.semantics { contentDescription = "Wiedergabeposition" },
                            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
                    }
                    if (!childMode) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onOpenLibrary) { Text("Bibliothek") }
                        Button(onClick = { vm.speakTitle(active?.track?.title ?: "KlangKiste") }) { Text("Titel ansagen") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerBadge(seconds: Int) {
    Row(Modifier.background(Navy.copy(alpha = .92f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(Icons.Default.Bedtime, contentDescription = null, tint = Yellow, modifier = Modifier.size(24.dp))
        Column {
            Text("Schlaf-Timer", color = Gray, fontSize = 11.sp)
            Text(formatTime(seconds * 1000L), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun RoundControl(image: ImageVector, description: String, size: Dp, background: Color, foreground: Color, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.size(size).shadow(4.dp, CircleShape), shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = background, contentColor = foreground)) {
        Icon(image, contentDescription = description, modifier = Modifier.size(size * .52f))
    }
}

@Composable
private fun FullBackground(uri: String) {
    val context = LocalContext.current
    val image by produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, key1 = uri) {
        value = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() }
    }
    val bitmap = image
    if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Image(painterResource(R.drawable.kids_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

@Composable
fun SleepOverlay(blackout: Boolean, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (!blackout) {
            val transition = rememberInfiniteTransition(label = "sleep")
            val scale by transition.animateFloat(1f, 1.04f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "pulse")
            Image(painterResource(R.drawable.background_sleep), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.note_sleeping_small), "Schlafende Note", Modifier.size(150.dp).graphicsLayer(scaleX = scale, scaleY = scale), contentScale = ContentScale.Fit)
                Spacer(Modifier.height(16.dp))
                Text("Schlafenszeit …", color = Color.White.copy(alpha = .75f), fontSize = 20.sp)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
