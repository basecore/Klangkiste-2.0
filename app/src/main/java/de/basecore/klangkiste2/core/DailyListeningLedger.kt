package de.basecore.klangkiste2.core

import android.content.SharedPreferences
import java.time.ZonedDateTime

internal class DailyListeningLedger(private val prefs: SharedPreferences) {
    fun listenedMs(at: ZonedDateTime): Long = prefs.getLong(key(at), 0L)

    fun addPlaybackEndingAt(end: ZonedDateTime, elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        var cursor = end.minusNanos(elapsedMs * 1_000_000L)
        val edit = prefs.edit()
        while (cursor.isBefore(end)) {
            val midnight = cursor.toLocalDate().plusDays(1).atStartOfDay(cursor.zone)
            val stop = if (midnight.isBefore(end)) midnight else end
            val duration = java.time.Duration.between(cursor, stop).toMillis()
            val dateKey = key(cursor)
            edit.putLong(dateKey, prefs.getLong(dateKey, 0L) + duration)
            cursor = stop
        }
        edit.apply()
    }

    private fun key(at: ZonedDateTime): String = "listening_ms_${at.toLocalDate()}"
}
