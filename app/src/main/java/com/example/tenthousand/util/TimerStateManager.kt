package com.example.tenthousand.util

import android.content.Context
import android.content.SharedPreferences

class TimerStateManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)

    // --- TIMER ---
    fun saveActiveTimer(habitId: Long, targetTimeMillis: Long, totalTimerSeconds: Long) {
        prefs.edit()
            .putLong("ACTIVE_HABIT_ID", habitId)
            .putLong("TARGET_TIME", targetTimeMillis)
            .putLong("TOTAL_SECONDS", totalTimerSeconds)
            .apply()
    }

    fun getActiveTimer(habitId: Long): Pair<Long, Long>? {
        val savedHabitId = prefs.getLong("ACTIVE_HABIT_ID", -1L)
        if (savedHabitId == habitId) {
            val targetTime = prefs.getLong("TARGET_TIME", 0L)
            val totalSecs = prefs.getLong("TOTAL_SECONDS", 0L)
            return Pair(targetTime, totalSecs)
        }
        return null
    }

    fun clearActiveTimer() {
        prefs.edit().remove("ACTIVE_HABIT_ID").remove("TARGET_TIME").remove("TOTAL_SECONDS").apply()
    }

    // --- STOPWATCH ---
    fun saveActiveStopwatch(habitId: Long, startTimeMillis: Long) {
        prefs.edit()
            .putLong("SW_HABIT_ID", habitId)
            .putLong("SW_START_TIME", startTimeMillis)
            .apply()
    }

    fun getActiveStopwatch(habitId: Long): Long? {
        val savedHabitId = prefs.getLong("SW_HABIT_ID", -1L)
        if (savedHabitId == habitId) {
            return prefs.getLong("SW_START_TIME", 0L)
        }
        return null
    }

    fun clearActiveStopwatch() {
        prefs.edit().remove("SW_HABIT_ID").remove("SW_START_TIME").apply()
    }
}