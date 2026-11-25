package de.basecore.klangkiste2.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.basecore.klangkiste2.KlangKisteViewModel

private enum class ParentPage { ADMIN, SETTINGS, STATS, PREVIEW, LIBRARY }
internal enum class DisplayMode(val label: String) {
    SYSTEM("Android-Timeout"), PLAYBACK("Bei Wiedergabe"), ALWAYS("Immer an")
}
private val ParentBackground = Color(0xFF2D3436)
private val ParentGreen = Color(0xFF2ECC71)
private val ParentBlue = Color(0xFF74B9FF)
private val ParentColors = darkColorScheme(primary = ParentBlue, onPrimary = ParentBackground,
    background = ParentBackground, onBackground = Color.White, surface = ParentBackground,
    onSurface = Color.White, surfaceVariant = Color(0xFF42494C), onSurfaceVariant = Color(0xFFDFE6E9))

@Composable
fun KlangKisteApp(viewModel: KlangKisteViewModel, onPinApp: () -> Unit) {
    val context = LocalContext.current
    val tracks by viewModel.tracks.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val child by viewModel.isChildMode.collectAsState()
    val childLibrary by viewModel.childLibraryOpen.collectAsState()
    val playing by viewModel.isPlaying.collectAsState()
    val sleep by viewModel.sleeping.collectAsState()
    val blackout by viewModel.blackout.collectAsState()
    val faceDown by viewModel.faceDown.collectAsState()
    val message by viewModel.message.collectAsState()
    val currentId by viewModel.currentTrackId.collectAsState()
    val active = tracks.firstOrNull { it.track.id == currentId }
    val snack = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(ParentPage.ADMIN) }
    val immersive = child || sleep
    val prefs = remember(context) { context.getSharedPreferences("display_settings", Context.MODE_PRIVATE) }
    var storedMode by remember(prefs) {
        mutableStateOf(DisplayMode.entries.firstOrNull { it.name == prefs.getString("mode", null) })
    }
    val mode = storedMode ?: if (settings.keepScreenOn) DisplayMode.PLAYBACK else DisplayMode.SYSTEM
    val window = context.findActivity()?.window
    val keepAwake = mode == DisplayMode.ALWAYS || (mode == DisplayMode.PLAYBACK && child && playing)
    DisposableEffect(window, keepAwake) {
        if (keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(message) {
        if (message.isNotBlank()) { snack.showSnackbar(message); viewModel.clearMessage() }
    }
    LaunchedEffect(window, immersive) {
        val activeWindow = window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(activeWindow, activeWindow.decorView)
        WindowCompat.setDecorFitsSystemWindows(activeWindow, !immersive)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    BackHandler(enabled = immersive || page != ParentPage.ADMIN) {
        if (!immersive) page = ParentPage.ADMIN
    }
    Box(Modifier.fillMaxSize().background(ParentBackground)) {
        when {
            sleep -> SleepOverlay(blackout) { viewModel.dismissSleepView() }
            child && childLibrary -> LibraryScreen(viewModel, tracks, settings, true) { viewModel.openChildLibrary(false) }
            child -> PlayerScreen(viewModel, active, settings, true) { viewModel.openChildLibrary(true) }
            else -> MaterialTheme(colorScheme = ParentColors) {
                Scaffold(containerColor = ParentBackground, snackbarHost = { SnackbarHost(snack) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (page == ParentPage.ADMIN) {
                            Button(onClick = { viewModel.setChildMode(true) },
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp).heightIn(min = 56.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ParentGreen, contentColor = ParentBackground)) {
                                Text("Zum Kindermodus", fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                                TextButton(onClick = { page = ParentPage.SETTINGS }) { Text("Einstellungen") }
                                TextButton(onClick = { page = ParentPage.STATS }) { Text("Statistik") }
                                TextButton(onClick = { page = ParentPage.PREVIEW }) { Text("Player-Vorschau") }
                            }
                        } else TextButton(onClick = { page = ParentPage.ADMIN }, modifier = Modifier.padding(start = 8.dp)) { Text("Zurück zum Elternbereich") }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (page) {
                                ParentPage.ADMIN -> AdminScreen(viewModel, tracks, onPinApp)
                                ParentPage.SETTINGS -> SettingsScreen(viewModel, settings, onPinApp, mode) {
                                    storedMode = it
                                    prefs.edit().putString("mode", it.name).apply()
                                }
                                ParentPage.STATS -> StatsScreen(viewModel, tracks)
                                ParentPage.PREVIEW -> PlayerScreen(viewModel, active, settings, false) { page = ParentPage.LIBRARY }
                                ParentPage.LIBRARY -> LibraryScreen(viewModel, tracks, settings, false, null)
                            }
                        }
                    }
                }
            }
        }
        if (child && faceDown) Box(Modifier.fillMaxSize().background(Color.Black))
        if (immersive) {
            Box(Modifier.align(Alignment.TopEnd).size(64.dp).pointerInput(child, sleep) {
                var taps = 0
                var lastTap = 0L
                detectTapGestures {
                    val now = SystemClock.elapsedRealtime()
                    taps = if (now - lastTap <= 2000L) taps + 1 else 1
                    lastTap = now
                    if (taps >= 5) {
                        taps = 0
                        page = ParentPage.ADMIN
                        if (sleep) viewModel.dismissSleepView() else viewModel.setChildMode(false)
                    }
                }
            })
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}
