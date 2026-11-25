package de.basecore.klangkiste2.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var remainingMs = 0L
    private var lastTick = 0L
    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            val now = SystemClock.elapsedRealtime()
            if (p.isPlaying && lastTick > 0L) remainingMs = (remainingMs - (now - lastTick)).coerceAtLeast(0L)
            lastTick = now
            if (remainingMs <= 0L) { p.pause(); handler.removeCallbacks(this); lastTick = 0L }
            else handler.postDelayed(this, 1000L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_OFF
        }
        session = MediaSession.Builder(this, player!!).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_SLEEP -> setSleepTimer(intent.getLongExtra(EXTRA_SECONDS, 0L))
            ACTION_CANCEL_SLEEP -> clearSleepTimer()
        }
        return super.onStartCommand(intent, flags, startId)
    }
    private fun setSleepTimer(seconds: Long) {
        clearSleepTimer()
        if (seconds <= 0L) return
        remainingMs = seconds * 1000L
        lastTick = SystemClock.elapsedRealtime()
        handler.post(tick)
    }
    private fun clearSleepTimer() { handler.removeCallbacks(tick); remainingMs = 0L; lastTick = 0L }
    override fun onDestroy() {
        clearSleepTimer()
        session?.release(); session = null
        player?.release(); player = null
        super.onDestroy()
    }
    companion object {
        const val ACTION_SET_SLEEP = "de.basecore.klangkiste2.action.SET_SLEEP"
        const val ACTION_CANCEL_SLEEP = "de.basecore.klangkiste2.action.CANCEL_SLEEP"
        const val EXTRA_SECONDS = "seconds"
    }
}
