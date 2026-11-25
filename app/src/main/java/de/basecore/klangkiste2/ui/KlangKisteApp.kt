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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.basecore.klangkiste2.KlangKisteViewModel

private enum class ParentTab { PLAYER, LIBRARY, ADMIN, STATS, SETTINGS }
internal enum class DisplayMode(val label: String) {
 SYSTEM("Android-Timeout"), PLAYBACK("Bei Wiedergabe"), ALWAYS("Immer an")
}

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
 var tab by remember { mutableStateOf(ParentTab.PLAYER) }
 val prefs = remember(context) { context.getSharedPreferences("display_settings", Context.MODE_PRIVATE) }
 var storedMode by remember(prefs) {
  mutableStateOf(DisplayMode.entries.firstOrNull { it.name == prefs.getString("mode", null) })
 }
 val displayMode = storedMode ?: if (settings.keepScreenOn) DisplayMode.PLAYBACK else DisplayMode.SYSTEM
 val window = context.findActivity()?.window
 val keepAwake = displayMode == DisplayMode.ALWAYS || (displayMode == DisplayMode.PLAYBACK && child && playing)
 DisposableEffect(window, keepAwake) {
  if (keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
 }
 LaunchedEffect(message) { if (message.isNotBlank()) { snack.showSnackbar(message); viewModel.clearMessage() } }
 LaunchedEffect(window, child) {
  val activeWindow = window ?: return@LaunchedEffect
  val controller = WindowInsetsControllerCompat(activeWindow, activeWindow.decorView)
  WindowCompat.setDecorFitsSystemWindows(activeWindow, !child)
  if (child) {
   controller.hide(WindowInsetsCompat.Type.systemBars())
   controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  } else controller.show(WindowInsetsCompat.Type.systemBars())
 }
 BackHandler(enabled = child) { /* System gestures remain available unless restricted by Android. */ }
 Box(Modifier.fillMaxSize().background(Color(0xFFF4F7F6))) {
  when {
   sleep -> SleepOverlay(blackout) { viewModel.dismissSleepView() }
   child && childLibrary -> LibraryScreen(viewModel, tracks, settings, true) { viewModel.openChildLibrary(false) }
   child -> PlayerScreen(viewModel, active, settings, true) { viewModel.openChildLibrary(true) }
   else -> Scaffold(snackbarHost = { SnackbarHost(snack) }, bottomBar = {
    NavigationBar {
     NavigationBarItem(tab == ParentTab.PLAYER, { tab = ParentTab.PLAYER }, { Icon(Icons.Default.Home, null) }, label = { Text("Player") })
     NavigationBarItem(tab == ParentTab.LIBRARY, { tab = ParentTab.LIBRARY }, { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Bibliothek") })
     NavigationBarItem(tab == ParentTab.ADMIN, { tab = ParentTab.ADMIN }, { Icon(Icons.Default.Tune, null) }, label = { Text("Verwaltung") })
     NavigationBarItem(tab == ParentTab.STATS, { tab = ParentTab.STATS }, { Icon(Icons.Default.BarChart, null) }, label = { Text("Statistik") })
     NavigationBarItem(tab == ParentTab.SETTINGS, { tab = ParentTab.SETTINGS }, { Icon(Icons.Default.Settings, null) }, label = { Text("Einstellungen") })
    }
   }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
     when (tab) {
      ParentTab.PLAYER -> PlayerScreen(viewModel, active, settings, false) { tab = ParentTab.LIBRARY }
      ParentTab.LIBRARY -> LibraryScreen(viewModel, tracks, settings, false, null)
      ParentTab.ADMIN -> AdminScreen(viewModel, tracks, onPinApp)
      ParentTab.STATS -> StatsScreen(viewModel, tracks)
      ParentTab.SETTINGS -> SettingsScreen(viewModel, settings, onPinApp, displayMode) { mode ->
       storedMode = mode
       prefs.edit().putString("mode", mode.name).apply()
      }
     }
    }
   }
  }
  if (child && faceDown) Box(Modifier.fillMaxSize().background(Color.Black))
  if (child || sleep) {
   Box(Modifier.align(Alignment.TopEnd).size(64.dp).pointerInput(child, sleep) {
    var taps = 0
    var lastTap = 0L
    detectTapGestures {
     val now = SystemClock.elapsedRealtime()
     taps = if (now - lastTap <= 2000L) taps + 1 else 1
     lastTap = now
     if (taps >= 5) {
      taps = 0
      if (sleep) viewModel.dismissSleepView() else viewModel.setChildMode(false)
     }
    }
   })
  }
 }
}

private fun Context.findActivity(): Activity? = when (this) {
 is Activity -> this
 is ContextWrapper -> baseContext.findActivity()
 else -> null
}
