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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun PlayerScreen(vm: KlangKisteViewModel, active: TrackWithAudio?, settings: AppSettingsEntity, childMode: Boolean, onOpenLibrary: () -> Unit) {
    val playing by vm.isPlaying.collectAsState()
    val pos by vm.positionMs.collectAsState()
    val duration by vm.durationMs.collectAsState()
    val volume by vm.volume.collectAsState()
    val sleepSeconds by vm.sleepSeconds.collectAsState()
    val title = active?.track?.title ?: "Bereit zum Spielen!"
    val bg = remember(settings.backgroundColor) { runCatching { Color(AndroidColor.parseColor(settings.backgroundColor)) }.getOrDefault(Navy) }
    val coverShape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxSize().background(if (childMode && settings.backgroundMode == "color") bg else Navy)) {
        if (childMode && settings.backgroundMode != "color") {
            val uri = settings.backgroundUri
            if (settings.backgroundMode == "image" && !uri.isNullOrBlank()) FullBackground(uri)
            else Image(painterResource(R.drawable.kids_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (childMode) Spacer(Modifier.height(44.dp))
            Text(title, Modifier.fillMaxWidth().background(Color.White.copy(alpha = .8f), RoundedCornerShape(15.dp)).padding(10.dp),
                color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = if (childMode) 28.sp else 22.sp,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            active?.track?.let { track ->
                val meta = listOfNotNull(track.genre, track.ageRecommendation?.let { "Ab $it J." }, track.runtimeMinutes?.let { "$it min" }).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = Color.White, fontSize = 13.sp)
            }
            Box(Modifier.widthIn(max = 280.dp).fillMaxWidth(.52f).aspectRatio(1f)
                .shadow(8.dp, coverShape).clip(coverShape).background(Gray).border(4.dp, Color.White, coverShape)) {
                CoverArt(active?.track?.coverUri, Modifier.fillMaxSize(), title)
                if (sleepSeconds > 0) {
                    Text("Timer ${sleepSeconds / 60}:${(sleepSeconds % 60).toString().padStart(2, '0')}",
                        Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = Navy)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val playSize = (maxWidth * .35f).coerceIn(64.dp, 110.dp)
                val sideSize = (maxWidth * .22f).coerceIn(44.dp, 75.dp)
                val colorful = childMode && settings.colorfulControls
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundControl(Icons.Default.VolumeDown, "Leiser", sideSize, if (colorful) Color(0xFFF1C40F) else Gray, Navy) { vm.adjustVolume(-.1f) }
                    RoundControl(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Wiedergabe", playSize, Green, Color.White, vm::togglePlay)
                    RoundControl(Icons.Default.VolumeUp, "Lauter", sideSize, if (colorful) Color(0xFFFF4757) else Gray, if (colorful) Color.White else Navy) { vm.adjustVolume(.1f) }
                }
            }
            LinearProgressIndicator(progress = { volume.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(.85f).height(22.dp).clip(CircleShape), color = Green, trackColor = Color.White.copy(alpha = .75f))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val size = (maxWidth * .2f).coerceIn(44.dp, 70.dp)
                val colorful = childMode && settings.colorfulControls
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundControl(Icons.Default.Replay, "Von vorn", size, if (colorful) Color(0xFFE17055) else Gray, Navy, vm::restartPlaylist)
                    RoundControl(Icons.Default.FastRewind, "30 Sekunden zurück", size, if (colorful) Blue else Gray, Navy, vm::skipBack)
                    RoundControl(Icons.Default.FastForward, "30 Sekunden vor", size, if (colorful) Color(0xFF9B59B6) else Gray, Navy, vm::skipForward)
                    RoundControl(Icons.Default.SkipNext, "Nächstes Kapitel oder nächste Datei", size, if (colorful) Color(0xFF00CEC9) else Gray, Navy, vm::nextFileOrCue)
                }
            }
            Column(Modifier.fillMaxWidth(.9f).background(Color.White.copy(alpha = .85f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${formatTime(pos)} / ${formatTime(duration)}", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Slider(value = pos.coerceIn(0L, duration.coerceAtLeast(0L)).toFloat(), onValueChange = { vm.seekTo(it.toLong()) },
                    enabled = duration > 0, valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
            }
            if (!childMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpenLibrary) { Text("Bibliothek") }
                    Button(onClick = { vm.speakTitle(active?.track?.title ?: "KlangKiste") }) { Text("Titel ansagen") }
                }
            }
        }
        if (childMode && settings.libraryEnabled) {
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                RoundControl(Icons.Default.LibraryMusic, "Bibliothek", 52.dp, Navy.copy(alpha = .72f), Color.White, onOpenLibrary)
            }
        }
    }
}

@Composable
private fun RoundControl(image: ImageVector, description: String, size: androidx.compose.ui.unit.Dp, background: Color, foreground: Color, onClick: () -> Unit) {
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
