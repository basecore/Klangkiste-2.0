package de.basecore.klangkiste2.core

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

internal data class ListeningWindow(val start: LocalTime, val end: LocalTime) {
    init { require(start != end) }
}

internal data class QuietHours(val window: ListeningWindow, val startDays: Set<DayOfWeek>)

internal enum class ListeningBlock { OUTSIDE_WINDOW, QUIET_HOURS, DAILY_LIMIT }

internal data class ParentalSchedule(
    val allowedWindow: ListeningWindow? = null,
    val quietHours: List<QuietHours> = emptyList(),
    val limitsMinutes: Map<DayOfWeek, Int> = emptyMap(),
    val fadeSeconds: Int = 20,
) {
    init { require(fadeSeconds >= 0 && limitsMinutes.values.all { it >= 0 }) }

    fun blockedAt(now: ZonedDateTime, listenedTodayMs: Long): ListeningBlock? {
        val time = now.toLocalTime()
        val day = now.dayOfWeek
        if (quietHours.any { matches(it.window, time, day, it.startDays) }) return ListeningBlock.QUIET_HOURS
        if (allowedWindow?.let { !matches(it, time, day, allDays) } == true) return ListeningBlock.OUTSIDE_WINDOW
        val limit = limitsMinutes[day] ?: 0
        return if (limit > 0 && listenedTodayMs >= limit * 60_000L) ListeningBlock.DAILY_LIMIT else null
    }

    private fun matches(window: ListeningWindow, time: LocalTime, day: DayOfWeek, days: Set<DayOfWeek>): Boolean =
        if (window.start < window.end) day in days && time >= window.start && time < window.end
        else (day in days && time >= window.start) || (day.minus(1) in days && time < window.end)

    private companion object {
        val allDays = DayOfWeek.values().toSet()
    }
}
