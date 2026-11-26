package de.basecore.klangkiste2.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import de.basecore.klangkiste2.core.*
import de.basecore.klangkiste2.data.KlangKisteDatabase
import kotlinx.coroutines.*
import java.time.*

@UnstableApi
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rules by lazy { getSharedPreferences("parental_rules", MODE_PRIVATE) }
    private val ledger by lazy { DailyListeningLedger(getSharedPreferences("parental_listening", MODE_PRIVATE)) }
    private var remainingMs = 0L
    private var playingSince: Long? = null
    private var schedule = ParentalSchedule()
    private var ages: Map<String, Int?>? = null
    private var maxAge: Int? = null
    private var baseVolume: Float? = null
    private var writingVolume = false

    private fun readSchedule() {
        fun window(value: String): ListeningWindow? = runCatching {
            val parts = value.split('-')
            ListeningWindow(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]))
        }.getOrNull()
        schedule = ParentalSchedule(
            allowedWindow = rules.getString("allowed", null)?.let(::window),
            quietHours = rules.getString("quiet", "").orEmpty().lineSequence().mapNotNull { line ->
                val parts = line.split('@')
                val times = window(parts[0]) ?: return@mapNotNull null
                val days = parts.getOrNull(1).orEmpty().split(',').mapNotNull { d ->
                    d.toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of)
                }.toSet()
                QuietHours(times, days)
            }.toList(),
            limitsMinutes = DayOfWeek.values().associateWith { rules.getInt("limit_${it.value}", 0).coerceAtLeast(0) },
            fadeSeconds = rules.getInt("fade_seconds", 20).coerceIn(0, 300)
        )
    }

    private val ruleListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        readSchedule()
        enforce()
    }

    private fun ageAllowed(id: String?): Boolean {
        if (id == null) return true
        val limit = maxAge ?: return false
        val catalog = ages ?: return false
        return catalog[id]?.let { it <= limit } ?: true
    }

    private fun account(now: ZonedDateTime) {
        val start = playingSince ?: return
        val mark = SystemClock.elapsedRealtime()
        val elapsed = (mark - start).coerceAtLeast(0L)
        playingSince = mark
        ledger.addPlaybackEndingAt(now, elapsed)
        if (remainingMs > 0L && elapsed >= remainingMs) {
            remainingMs = 0L
            player.pause()
        } else if (remainingMs > 0L) remainingMs -= elapsed
    }

    private fun volume(value: Float) {
        writingVolume = true
        try { player.volume = value } finally { writingVolume = false }
    }

    private fun restoreVolume() {
        val saved = baseVolume ?: return
        baseVolume = null
        volume(saved)
    }

    private fun enforce() {
        val now = ZonedDateTime.now()
        account(now)
        if (!player.playWhenReady) { restoreVolume(); return }
        val used = ledger.listenedMs(now)
        if (!ageAllowed(player.currentMediaItem?.mediaId) || schedule.blockedAt(now, used) != null) {
            player.pause()
            restoreVolume()
            return
        }
        if (!player.isPlaying || schedule.fadeSeconds == 0) { restoreVolume(); return }
        val fade = schedule.fadeSeconds * 1000L
        val windows = listOfNotNull(schedule.allowedWindow) + schedule.quietHours.map { it.window }
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        var next = windows.flatMap { window ->
            (0L..1L).flatMap { offset ->
                val date = now.toLocalDate().plusDays(offset)
                listOf(window.start, window.end).flatMap { time ->
                    val local = date.atTime(time)
                    val offsets = now.zone.rules.getValidOffsets(local)
                    if (offsets.isEmpty()) listOf(local.atZone(now.zone))
                    else offsets.map { ZonedDateTime.ofLocal(local, now.zone, it) }
                }
            }
        }.plus(listOfNotNull(now.zone.rules.nextTransition(now.toInstant())?.instant?.atZone(now.zone)))
            .asSequence().filter { it.isAfter(now) }
            .filter { schedule.blockedAt(it, if (it.toLocalDate() == now.toLocalDate()) used else 0L) != null }
            .map { Duration.between(now, it).toMillis() }.minOrNull() ?: Long.MAX_VALUE
        val daily = schedule.limitsMinutes[now.dayOfWeek] ?: 0
        if (daily > 0) {
            val left = daily * 60_000L - used
            if (left < Duration.between(now, nextMidnight).toMillis()) next = minOf(next, left)
        }
        if (next < fade) {
            if (baseVolume == null) baseVolume = player.volume
            volume(baseVolume!! * (next.toFloat() / fade).coerceIn(0f, 1f))
        } else restoreVolume()
    }

    private val tick = object : Runnable {
        override fun run() {
            enforce()
            handler.postDelayed(this, if (baseVolume != null) 100L else 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        readSchedule()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) playingSince = SystemClock.elapsedRealtime()
                    else { account(ZonedDateTime.now()); playingSince = null }
                    enforce()
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { enforce() }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { enforce() }
                override fun onVolumeChanged(volume: Float) { if (!writingVolume && baseVolume != null) baseVolume = volume }
            })
        }
        val db = KlangKisteDatabase.get(this)
        scope.launch { db.settingsDao().observe().collect { maxAge = it?.maxAge ?: 99; enforce() } }
        scope.launch { db.trackDao().observeAll().collect { list -> ages = list.associate { it.track.id to it.track.ageRecommendation }; enforce() } }
        rules.registerOnSharedPreferenceChangeListener(ruleListener)
        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
                if (playerCommand == Player.COMMAND_PLAY_PAUSE && !player.playWhenReady) {
                    val now = ZonedDateTime.now()
                    if (!ageAllowed(player.currentMediaItem?.mediaId) || schedule.blockedAt(now, ledger.listenedMs(now)) != null)
                        return androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED
                }
                return androidx.media3.session.SessionResult.RESULT_SUCCESS
            }
        }).build()
        handler.post(tick)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        account(ZonedDateTime.now())
        when (intent?.action) {
            ACTION_SET_SLEEP -> remainingMs = intent.getLongExtra(EXTRA_SECONDS, 0L).coerceAtLeast(0L) * 1000L
            ACTION_CANCEL_SLEEP -> remainingMs = 0L
        }
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onDestroy() {
        account(ZonedDateTime.now())
        handler.removeCallbacks(tick)
        rules.unregisterOnSharedPreferenceChangeListener(ruleListener)
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }
    companion object {
        const val ACTION_SET_SLEEP = "de.basecore.klangkiste2.action.SET_SLEEP"
        const val ACTION_CANCEL_SLEEP = "de.basecore.klangkiste2.action.CANCEL_SLEEP"
        const val EXTRA_SECONDS = "seconds"
    }
}
