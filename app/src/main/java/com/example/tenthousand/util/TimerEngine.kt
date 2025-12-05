package com.example.tenthousand.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.system.*

/**
 * Emits ticks every second with the remaining seconds for a countdown, or elapsed seconds for stopwatch.
 * This is simple and uses a monotonic clock for accuracy.
 */

sealed class TimerMode {
    data class Countdown(val totalSeconds: Long): TimerMode()
    object Stopwatch : TimerMode()
}

data class TimerTick(val seconds: Long)

/**
 * Emits TimerTick each second until cancelled. For Countdown tick.seconds is remaining seconds (>=0).
 * For Stopwatch tick.seconds is elapsed seconds (>=0).
 */
fun timerFlow(mode: TimerMode, startOffsetSeconds: Long = 0L): Flow<TimerTick> = flow {
    val start = System.nanoTime()
    var lastEmitted = -1L
    while (true) {
        val elapsedNanos = System.nanoTime() - start
        val elapsedSeconds = (elapsedNanos / 1_000_000_000L)
        val value = when (mode) {
            is TimerMode.Countdown -> {
                val rem = mode.totalSeconds - (startOffsetSeconds + elapsedSeconds)
                if (rem < 0L) 0L else rem
            }
            TimerMode.Stopwatch -> startOffsetSeconds + elapsedSeconds
        }
        if (value != lastEmitted) {
            emit(TimerTick(value))
            lastEmitted = value
        }
        // Sleep until next second boundary (approx)
        val nanosToNext = ((lastEmitted + 1) * 1_000_000_000L) - (System.nanoTime() - start)
        val ms = (nanosToNext / 1_000_000L).coerceAtLeast(200L) // don't busy loop
        delay(ms)
        // If countdown reached zero and mode is Countdown: stop flow after emitting zero
        if (mode is TimerMode.Countdown && lastEmitted == 0L) break
    }
}
