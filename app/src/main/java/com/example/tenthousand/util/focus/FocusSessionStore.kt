package com.example.tenthousand.util.focus

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Zamenjuje TimerStateManager.
 *
 * Uloge su namerno razdvojene: SAMO FocusService sme da poziva [set] - on je
 * izvor istine. ViewModel-i čitaju [session] i šalju komande servisu preko
 * Intent-a. Time izbegavam bound service i binder-e: servis i ViewModel su u
 * istom procesu, pa je jedan StateFlow dovoljan kanal nazad ka UI-ju, a
 * SharedPreferences pokriva slučaj kad proces bude ubijen.
 */
class FocusSessionStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_session_prefs", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(load())
    val session: StateFlow<FocusSession?> = _session.asStateFlow()

    fun set(session: FocusSession?) {
        persist(session)
        _session.value = session
    }

    /** Poslednje izabrano trajanje tajmera, da dijalog ne počinje uvek od 25 min. */
    fun lastTimerSeconds(): Long = prefs.getLong(KEY_LAST_TIMER_SECONDS, 25 * 60L)

    fun setLastTimerSeconds(seconds: Long) {
        prefs.edit().putLong(KEY_LAST_TIMER_SECONDS, seconds).apply()
    }

    private fun persist(session: FocusSession?) {
        val editor = prefs.edit()
        if (session == null) {
            editor.remove(KEY_HABIT_ID)
                .remove(KEY_HABIT_NAME)
                .remove(KEY_HABIT_COLOR)
                .remove(KEY_BACKGROUND)
                .remove(KEY_MODE)
                .remove(KEY_TOTAL_SECONDS)
                .remove(KEY_ACCUMULATED)
                .remove(KEY_RUNNING_SINCE)
        } else {
            editor.putLong(KEY_HABIT_ID, session.habitId)
                .putString(KEY_HABIT_NAME, session.habitName)
                .putInt(KEY_HABIT_COLOR, session.habitColor)
                .putString(KEY_BACKGROUND, session.backgroundName)
                .putString(KEY_MODE, session.mode.name)
                .putLong(KEY_TOTAL_SECONDS, session.totalSeconds)
                .putLong(KEY_ACCUMULATED, session.accumulatedMillis)
                .putLong(KEY_RUNNING_SINCE, session.runningSinceMillis ?: -1L)
        }
        // commit(), ne apply(): ovo stanje mora da bude na disku i ako sistem
        // ubije proces odmah posle poziva (npr. korisnik pokrene tajmer pa
        // odmah swipe-uje app). apply() je asinhron i tu bi mogao da izgubi upis.
        editor.commit()
    }

    private fun load(): FocusSession? {
        val habitId = prefs.getLong(KEY_HABIT_ID, -1L)
        if (habitId <= 0L) return null
        val modeName = prefs.getString(KEY_MODE, null) ?: return null
        val mode = runCatching { FocusMode.valueOf(modeName) }.getOrNull() ?: return null
        val runningSince = prefs.getLong(KEY_RUNNING_SINCE, -1L)

        return FocusSession(
            habitId = habitId,
            habitName = prefs.getString(KEY_HABIT_NAME, "") ?: "",
            habitColor = prefs.getInt(KEY_HABIT_COLOR, 0xFF6650a4.toInt()),
            backgroundName = prefs.getString(KEY_BACKGROUND, null),
            mode = mode,
            totalSeconds = prefs.getLong(KEY_TOTAL_SECONDS, 0L),
            accumulatedMillis = prefs.getLong(KEY_ACCUMULATED, 0L),
            runningSinceMillis = if (runningSince > 0L) runningSince else null
        )
    }

    companion object {
        private const val KEY_HABIT_ID = "HABIT_ID"
        private const val KEY_HABIT_NAME = "HABIT_NAME"
        private const val KEY_HABIT_COLOR = "HABIT_COLOR"
        private const val KEY_BACKGROUND = "BACKGROUND"
        private const val KEY_MODE = "MODE"
        private const val KEY_TOTAL_SECONDS = "TOTAL_SECONDS"
        private const val KEY_ACCUMULATED = "ACCUMULATED_MILLIS"
        private const val KEY_RUNNING_SINCE = "RUNNING_SINCE"
        private const val KEY_LAST_TIMER_SECONDS = "LAST_TIMER_SECONDS"

        @Volatile
        private var INSTANCE: FocusSessionStore? = null

        fun getInstance(context: Context): FocusSessionStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FocusSessionStore(context.applicationContext).also { INSTANCE = it }
            }
    }
}