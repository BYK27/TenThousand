package com.example.tenthousand.ui.screens.habit_detail

import HabitEntity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.util.CoinManager
import com.example.tenthousand.util.TimerStateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class HabitDetailUiState(
    val habit: HabitEntity? = null,
    val timerRunning: Boolean = false,
    val timerTotal: Long = 25 * 60L,
    val timerRemaining: Long = 25 * 60L,
    val timerFinishedEvent: Boolean = false,
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsed: Long = 0L,
    val totalCoins: Int = 0,
    val totalWishes: Int = 0
)

class HabitDetailViewModel(
    private val habitId: Long,
    private val dao: HabitDao,
    context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitDetailUiState())
    val uiState: StateFlow<HabitDetailUiState> = _uiState.asStateFlow()

    private val _coinEvents = MutableSharedFlow<Int>(extraBufferCapacity = 100)
    val coinEvents = _coinEvents.asSharedFlow()

    private val timerStateManager = TimerStateManager(context)

    // Use the Singleton instance here
    private val coinManager = CoinManager.getInstance(context)

    private var timerJob: Job? = null
    private var timerTargetTimeMillis: Long = 0L
    private var stopwatchJob: Job? = null
    private var stopwatchStartRealTimeMillis: Long = 0L
    private var coinAccumulatorMillis: Long = 0L
    private var lastTickTimeMillis: Long = 0L

    init {
        observeHabit()
        observeCurrencies()
        restoreState()
    }

    private fun observeHabit() {
        viewModelScope.launch {
            dao.observeById(habitId).collect { habit ->
                _uiState.update { it.copy(habit = habit) }
            }
        }
    }

    private fun observeCurrencies() {
        viewModelScope.launch {
            coinManager.coins.collect { coins ->
                _uiState.update { it.copy(totalCoins = coins) }
            }
        }
        viewModelScope.launch {
            coinManager.wishes.collect { wishes ->
                _uiState.update { it.copy(totalWishes = wishes) }
            }
        }
    }

    fun convertCoinsToWishes(wishesToBuy: Int) {
        val cost = wishesToBuy * 95000
        if (cost > 0 && _uiState.value.totalCoins >= cost) {
            coinManager.removeCoins(cost)
            coinManager.addWishes(wishesToBuy)
        }
    }

    private fun creditSeconds(seconds: Long) {
        viewModelScope.launch { dao.addSeconds(habitId, seconds) }
    }

    private fun awardCoins(amount: Int) {
        if (amount > 0) coinManager.addCoins(amount)
    }

    private fun restoreState() {
        val activeTimer = timerStateManager.getActiveTimer(habitId)
        if (activeTimer != null) {
            val (targetTime, totalSec) = activeTimer
            val now = System.currentTimeMillis()
            val diffSeconds = (targetTime - now) / 1000L

            _uiState.update { it.copy(timerTotal = totalSec) }

            if (diffSeconds <= 0) {
                creditSeconds(totalSec)
                awardCoins((totalSec * 3).toInt())

                _uiState.update { it.copy(
                    timerRemaining = totalSec,
                    timerRunning = false,
                    timerFinishedEvent = true
                )}
                timerStateManager.clearActiveTimer()
            } else {
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
        timerStateManager.saveActiveTimer(habitId, timerTargetTimeMillis, _uiState.value.timerTotal)
        resumeTimerJob()
    }

    private fun processBrainrotDrops(delta: Long) {
        coinAccumulatorMillis += delta
        if (coinAccumulatorMillis >= 100L) {
            coinAccumulatorMillis -= 100L
            val chance = Random.nextFloat()
            val gain = when {
                chance > 0.98f -> Random.nextInt(100, 1000)
                chance > 0.85f -> Random.nextInt(10, 50)
                chance > 0.40f -> Random.nextInt(2, 10)
                else -> 1
            }
            awardCoins(gain)
            _coinEvents.tryEmit(gain)
        }
    }

    private fun resumeTimerJob() {
        timerJob?.cancel()
        lastTickTimeMillis = System.currentTimeMillis()
        coinAccumulatorMillis = 0L

        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.timerRunning) {
                val now = System.currentTimeMillis()
                val delta = now - lastTickTimeMillis
                lastTickTimeMillis = now

                processBrainrotDrops(delta)

                val diffSeconds = (timerTargetTimeMillis - now) / 1000L

                if (diffSeconds <= 0) {
                    _uiState.update { it.copy(timerRemaining = 0, timerRunning = false, timerFinishedEvent = true) }
                    creditSeconds(_uiState.value.timerTotal)
                    _uiState.update { it.copy(timerRemaining = it.timerTotal) }
                    timerStateManager.clearActiveTimer()
                    break
                } else {
                    _uiState.update { it.copy(timerRemaining = diffSeconds) }
                }
                delay(16)
            }
        }
    }

    fun consumeTimerFinishedEvent() {
        _uiState.update { it.copy(timerFinishedEvent = false) }
    }

    fun pauseTimer() {
        _uiState.update { it.copy(timerRunning = false) }
        timerJob?.cancel()
        timerStateManager.clearActiveTimer()
    }

    fun startStopwatch() {
        if (_uiState.value.stopwatchRunning) return
        stopwatchStartRealTimeMillis = System.currentTimeMillis() - (_uiState.value.stopwatchElapsed * 1000L)
        _uiState.update { it.copy(stopwatchRunning = true) }
        timerStateManager.saveActiveStopwatch(habitId, stopwatchStartRealTimeMillis)
        resumeStopwatchJob()
    }

    private fun resumeStopwatchJob() {
        stopwatchJob?.cancel()
        lastTickTimeMillis = System.currentTimeMillis()
        coinAccumulatorMillis = 0L

        stopwatchJob = viewModelScope.launch {
            while (isActive && _uiState.value.stopwatchRunning) {
                val now = System.currentTimeMillis()
                val delta = now - lastTickTimeMillis
                lastTickTimeMillis = now

                processBrainrotDrops(delta)

                val elapsedSeconds = (now - stopwatchStartRealTimeMillis) / 1000L
                _uiState.update { it.copy(stopwatchElapsed = elapsedSeconds) }
                delay(16)
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