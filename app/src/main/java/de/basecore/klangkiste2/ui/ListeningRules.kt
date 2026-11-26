package de.basecore.klangkiste2.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.basecore.klangkiste2.core.DailyListeningLedger
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@Composable
internal fun ListeningRules() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("parental_rules", Context.MODE_PRIVATE) }
    val ledger = remember(context) { DailyListeningLedger(context.getSharedPreferences("parental_listening", Context.MODE_PRIVATE)) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var revision by remember { mutableIntStateOf(0) }
    var window by remember { mutableStateOf(prefs.getString("allowed", "").orEmpty()) }
    var quiet by remember { mutableStateOf(prefs.getString("quiet", "").orEmpty()) }
    var fade by remember { mutableStateOf(prefs.getInt("fade_seconds", 20).toString()) }
    val limits = remember { mutableStateListOf<String>().apply { (1..7).forEach { add(prefs.getInt("limit_$it", 0).toString()) } } }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = ZonedDateTime.now() } }
    val used = ledger.listenedMs(now)
    val limit = remember(now.dayOfWeek, revision) { prefs.getInt("limit_${now.dayOfWeek.value}", 0) }
    fun format(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hörregeln", style = MaterialTheme.typography.titleLarge)
        Text("Heute gehört: ${format(used)}")
        Text(if (limit == 0) "Verbleibend: unbegrenzt" else "Verbleibend: ${format((limit * 60000L - used).coerceAtLeast(0L))}")
        OutlinedTextField(window, { window = it }, label = { Text("Erlaubt: HH:mm-HH:mm") }, singleLine = true)
        Text("Leer lassen für ganztägige Freigabe. Zeitfenster dürfen über Mitternacht reichen.")
        OutlinedTextField(quiet, { quiet = it }, label = { Text("Ruhezeiten (eine pro Zeile)") }, minLines = 2)
        Text("Beispiel: 22:00-07:00@5,6. Montag = 1, Sonntag = 7. Es zählt der Starttag.")
        Text("Hörzeitlimit in Minuten · 0 = unbegrenzt")
        listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag").forEachIndexed { i, day ->
            OutlinedTextField(limits[i], { limits[i] = it }, label = { Text(day) }, singleLine = true)
        }
        OutlinedTextField(fade, { fade = it }, label = { Text("Abblenden in Sekunden (0–300)") }, singleLine = true)
        Button(onClick = {
            val w = window.trim()
            val lines = quiet.lines().map(String::trim).filter(String::isNotEmpty)
            val minutes = limits.map { it.trim().toIntOrNull() }
            val seconds = fade.trim().toIntOrNull()
            status = when {
                w.isNotEmpty() && !validRange(w) -> "Ungültiges erlaubtes Zeitfenster."
                lines.any { line ->
                    val p = line.split('@')
                    p.size != 2 || !validRange(p[0]) || p[1].split(',').any { (it.toIntOrNull() ?: 0) !in 1..7 }
                } -> "Ungültige Ruhezeit. Bitte das Beispielformat verwenden."
                minutes.any { it == null || it < 0 } -> "Bitte nichtnegative Minutenzahlen eingeben."
                seconds == null || seconds !in 0..300 -> "Bitte 0 bis 300 Sekunden eingeben."
                else -> {
                    val edit = prefs.edit().putString("quiet", lines.joinToString("\n")).putInt("fade_seconds", seconds)
                    if (w.isEmpty()) edit.remove("allowed") else edit.putString("allowed", w)
                    minutes.forEachIndexed { i, m -> edit.putInt("limit_${i + 1}", requireNotNull(m)) }
                    edit.apply()
                    revision++
                    "Wiedergaberegeln gespeichert."
                }
            }
        }) { Text("Regeln speichern") }
        if (status.isNotEmpty()) Text(status)
    }
}

private fun validRange(value: String): Boolean {
    val parts = value.split('-')
    return parts.size == 2 && parts.all { it.matches(Regex("[0-9]{2}:[0-9]{2}")) } &&
        runCatching { LocalTime.parse(parts[0]) != LocalTime.parse(parts[1]) }.getOrDefault(false)
}
