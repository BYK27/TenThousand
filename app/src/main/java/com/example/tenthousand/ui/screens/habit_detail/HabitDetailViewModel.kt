package com.example.tenthousand.ui.screens.habit_detail

import HabitEntity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Debug

data class HabitDetailUiState(
    val habit: HabitEntity? = null,
    val timerRunning: Boolean = false,
    val timerRemaining: Long = 0L,

    val stopwatchRunning: Boolean = false,
    val stopwatchElapsed: Long = 0L
)

class HabitDetailViewModel(
    private val habitId: Long,
    private val dao: HabitDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitDetailUiState())
    val uiState: StateFlow<HabitDetailUiState> = _uiState.asStateFlow()

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

    fun creditSeconds(seconds: Long) {
        Log.d("HabitDetailViewModel", "Crediting $seconds seconds to habitId=$habitId")
        viewModelScope.launch {
            dao.addSeconds(habitId, seconds)
        }
    }

    // ----------------------
    // TIMER STATE
    // ----------------------
    fun setTimerRunning(running: Boolean) {
        _uiState.update { it.copy(timerRunning = running) }
    }

    fun setTimerRemaining(remaining: Long) {
        _uiState.update { it.copy(timerRemaining = remaining) }
    }

    // ----------------------
    // STOPWATCH STATE
    // ----------------------
    fun setStopwatchRunning(running: Boolean) {
        _uiState.update { it.copy(stopwatchRunning = running) }
    }

    fun setStopwatchElapsed(elapsed: Long) {
        _uiState.update { it.copy(stopwatchElapsed = elapsed) }
    }
}
