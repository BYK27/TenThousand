package com.example.tenthousand.ui.screens.habit_detail

import HabitEntity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HabitDetailUiState(
    val habit: HabitEntity? = null,

    // Timer State
    val timerRunning: Boolean = false,
    val timerTotal: Long = 25 * 60L, // Default 25 minutes
    val timerRemaining: Long = 25 * 60L,
    val timerFinishedEvent: Boolean = false,

    // Stopwatch State
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsed: Long = 0L
)

class HabitDetailViewModel(
    private val habitId: Long,
    private val dao: HabitDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitDetailUiState())
    val uiState: StateFlow<HabitDetailUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var timerTargetTimeMillis: Long = 0L

    private var stopwatchJob: Job? = null
    private var stopwatchStartRealTimeMillis: Long = 0L

    init {
        observeHabit()
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
        viewModelScope.launch {
            dao.addSeconds(habitId, seconds)
        }
    }

    // ----------------------
    // TIMER LOGIC
    // ----------------------

    fun setTimerTotal(minutes: Long) {
        pauseTimer()
        val totalSeconds = minutes * 60L
        _uiState.update {
            it.copy(timerTotal = totalSeconds, timerRemaining = totalSeconds)
        }
    }

    fun startTimer() {
        if (_uiState.value.timerRunning) return
        val remaining = _uiState.value.timerRemaining
        if (remaining <= 0) return

        // Calculate exactly when the timer should finish in the real world
        timerTargetTimeMillis = System.currentTimeMillis() + (remaining * 1000L)
        _uiState.update { it.copy(timerRunning = true) }

        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.timerRunning) {
                val now = System.currentTimeMillis()
                val diffSeconds = (timerTargetTimeMillis - now) / 1000L

                if (diffSeconds <= 0) {
                    // Timer reached 0!
                    _uiState.update {
                        it.copy(
                            timerRemaining = 0,
                            timerRunning = false,
                            timerFinishedEvent = true
                        )
                    }
                    creditSeconds(_uiState.value.timerTotal)

                    // Reset timer to original duration for the next session
                    _uiState.update { it.copy(timerRemaining = it.timerTotal) }
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
    }

    // ----------------------
    // STOPWATCH LOGIC
    // ----------------------

    fun startStopwatch() {
        if (_uiState.value.stopwatchRunning) return

        // Offset the start time by however much time has already elapsed
        stopwatchStartRealTimeMillis = System.currentTimeMillis() - (_uiState.value.stopwatchElapsed * 1000L)
        _uiState.update { it.copy(stopwatchRunning = true) }

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