package com.example.tenthousand.ui.screens.habit_detail

import HabitEntity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.util.TimerStateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HabitDetailUiState(
    val habit: HabitEntity? = null,
    val timerRunning: Boolean = false,
    val timerTotal: Long = 25 * 60L,
    val timerRemaining: Long = 25 * 60L,
    val timerFinishedEvent: Boolean = false,
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsed: Long = 0L
)

class HabitDetailViewModel(
    private val habitId: Long,
    private val dao: HabitDao,
    context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitDetailUiState())
    val uiState: StateFlow<HabitDetailUiState> = _uiState.asStateFlow()

    private val timerStateManager = TimerStateManager(context)

    private var timerJob: Job? = null
    private var timerTargetTimeMillis: Long = 0L

    private var stopwatchJob: Job? = null
    private var stopwatchStartRealTimeMillis: Long = 0L

    init {
        observeHabit()
        restoreState() // <-- Check SharedPreferences on startup
    }

    private fun observeHabit() {
        viewModelScope.launch {
            dao.observeById(habitId).collect { habit ->
                _uiState.update { it.copy(habit = habit) }
            }
        }
    }

    private fun creditSeconds(seconds: Long) {
        Log.d("HabitDetailViewModel", "Crediting $seconds seconds to habitId=$habitId")
        viewModelScope.launch { dao.addSeconds(habitId, seconds) }
    }

    // --- RESTORE LOGIC ---
    private fun restoreState() {
        val activeTimer = timerStateManager.getActiveTimer(habitId)
        if (activeTimer != null) {
            val (targetTime, totalSec) = activeTimer
            val now = System.currentTimeMillis()
            val diffSeconds = (targetTime - now) / 1000L

            _uiState.update { it.copy(timerTotal = totalSec) }

            if (diffSeconds <= 0) {
                // Timer finished while app was completely closed!
                creditSeconds(totalSec)
                _uiState.update { it.copy(
                    timerRemaining = totalSec,
                    timerRunning = false,
                    timerFinishedEvent = true
                )}
                timerStateManager.clearActiveTimer()
            } else {
                // Timer is still running, catch up UI
                timerTargetTimeMillis = targetTime
                _uiState.update { it.copy(timerRemaining = diffSeconds, timerRunning = true) }
                resumeTimerJob()
            }
        }

        val activeSw = timerStateManager.getActiveStopwatch(habitId)
        if (activeSw != null) {
            stopwatchStartRealTimeMillis = activeSw
            _uiState.update { it.copy(stopwatchRunning = true) }
            resumeStopwatchJob()
        }
    }

    // ----------------------
    // TIMER LOGIC
    // ----------------------

    fun setTimerTotal(minutes: Long) {
        pauseTimer()
        val totalSeconds = minutes * 60L
        _uiState.update { it.copy(timerTotal = totalSeconds, timerRemaining = totalSeconds) }
    }

    fun startTimer() {
        if (_uiState.value.timerRunning) return
        val remaining = _uiState.value.timerRemaining
        if (remaining <= 0) return

        timerTargetTimeMillis = System.currentTimeMillis() + (remaining * 1000L)
        _uiState.update { it.copy(timerRunning = true) }

        // Save persistently
        timerStateManager.saveActiveTimer(habitId, timerTargetTimeMillis, _uiState.value.timerTotal)

        resumeTimerJob()
    }

    private fun resumeTimerJob() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.timerRunning) {
                val now = System.currentTimeMillis()
                val diffSeconds = (timerTargetTimeMillis - now) / 1000L

                if (diffSeconds <= 0) {
                    _uiState.update { it.copy(timerRemaining = 0, timerRunning = false, timerFinishedEvent = true) }
                    creditSeconds(_uiState.value.timerTotal)
                    _uiState.update { it.copy(timerRemaining = it.timerTotal) }
                    timerStateManager.clearActiveTimer() // Clean up storage
                    break
                } else {
                    _uiState.update { it.copy(timerRemaining = diffSeconds) }
                }
                delay(200)
            }
        }
    }

    fun consumeTimerFinishedEvent() {
        _uiState.update { it.copy(timerFinishedEvent = false) }
    }

    fun pauseTimer() {
        _uiState.update { it.copy(timerRunning = false) }
        timerJob?.cancel()
        timerStateManager.clearActiveTimer() // Pausing stops the background check
    }

    // ----------------------
    // STOPWATCH LOGIC
    // ----------------------

    fun startStopwatch() {
        if (_uiState.value.stopwatchRunning) return

        stopwatchStartRealTimeMillis = System.currentTimeMillis() - (_uiState.value.stopwatchElapsed * 1000L)
        _uiState.update { it.copy(stopwatchRunning = true) }

        // Save persistently
        timerStateManager.saveActiveStopwatch(habitId, stopwatchStartRealTimeMillis)

        resumeStopwatchJob()
    }

    private fun resumeStopwatchJob() {
        stopwatchJob?.cancel()
        stopwatchJob = viewModelScope.launch {
            while (isActive && _uiState.value.stopwatchRunning) {
                val now = System.currentTimeMillis()
                val elapsedSeconds = (now - stopwatchStartRealTimeMillis) / 1000L
                _uiState.update { it.copy(stopwatchElapsed = elapsedSeconds) }
                delay(200)
            }
        }
    }

    fun pauseStopwatch() {
        _uiState.update { it.copy(stopwatchRunning = false) }
        stopwatchJob?.cancel()
        timerStateManager.clearActiveStopwatch()
    }

    fun stopAndCreditStopwatch() {
        pauseStopwatch()
        val elapsed = _uiState.value.stopwatchElapsed
        if (elapsed > 0) {
            creditSeconds(elapsed)
            _uiState.update { it.copy(stopwatchElapsed = 0L) }
        }
    }
}