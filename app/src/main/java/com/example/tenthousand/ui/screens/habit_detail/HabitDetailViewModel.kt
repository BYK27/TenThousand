package com.example.tenthousand.ui.screens.habit_detail

import HabitEntity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.service.FocusService
import com.example.tenthousand.util.CoinManager
import com.example.tenthousand.util.focus.FocusMode
import com.example.tenthousand.util.focus.FocusSession
import com.example.tenthousand.util.focus.FocusSessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class HabitDetailUiState(
    val habit: HabitEntity? = null,
    /** Postoji li aktivna (makar i pauzirana) TIMER sesija baš za ovu naviku. */
    val timerActive: Boolean = false,
    val timerRunning: Boolean = false,
    val timerTotal: Long = 25 * 60L,
    val timerRemaining: Long = 25 * 60L,
    val timerElapsed: Long = 0L,
    val stopwatchActive: Boolean = false,
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsed: Long = 0L,
    val totalCoins: Int = 0,
    val totalWishes: Int = 0
)

/**
 * ViewModel više ne meri vreme - samo ga prikazuje.
 *
 * Izvor istine je FocusService preko FocusSessionStore-a. Ovde ostaje:
 * - preslikavanje sesije u UI stanje,
 * - slanje komandi servisu,
 * - brainrot coin drop-ovi, koji po dogovoru rade SAMO dok je app u prvom
 *   planu. Zato je petlja umotana u ProcessLifecycleOwner.repeatOnLifecycle -
 *   kad app ode u pozadinu, korutina se otkaže i drop-ovi prestanu; kad se
 *   vrati, ponovo krene. Sat u međuvremenu neometano teče u servisu, jer se
 *   proteklo vreme računa iz zidnog sata, a ne broji otkucajima.
 */
class HabitDetailViewModel(
    private val habitId: Long,
    private val dao: HabitDao,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(HabitDetailUiState())
    val uiState: StateFlow<HabitDetailUiState> = _uiState.asStateFlow()

    private val _coinEvents = MutableSharedFlow<Int>(extraBufferCapacity = 100)
    val coinEvents = _coinEvents.asSharedFlow()

    private val coinManager = CoinManager.getInstance(appContext)
    private val store = FocusSessionStore.getInstance(appContext)

    private var coinAccumulatorMillis: Long = 0L

    init {
        _uiState.update {
            val last = store.lastTimerSeconds()
            it.copy(timerTotal = last, timerRemaining = last)
        }
        observeHabit()
        observeCurrencies()
        observeSession()
        runForegroundTicker()
    }

    // ---------------------------------------------------------------------
    // Observers
    // ---------------------------------------------------------------------

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

    private fun observeSession() {
        viewModelScope.launch {
            store.session.collect { session ->
                render(session, System.currentTimeMillis())
            }
        }
    }

    /**
     * Jedina periodična petlja u app-u. Radi na 100 ms - isti raster koji je
     * stari kod koristio za coin drop-ove (delay(16) sa akumulatorom od 100 ms
     * je davao isti efekat uz 6x više budjenja). Prikaz se osvežava u istoj
     * petlji; MutableStateFlow ne emituje kad je nova vrednost jednaka staroj,
     * pa rekompozicija ide tek kad se sekunda promeni.
     */
    private fun runForegroundTicker() {
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastTick = System.currentTimeMillis()
                coinAccumulatorMillis = 0L

                while (isActive) {
                    val now = System.currentTimeMillis()
                    val delta = now - lastTick
                    lastTick = now

                    val session = mySession()
                    if (session != null && session.isRunning) {
                        processBrainrotDrops(delta)
                    } else {
                        coinAccumulatorMillis = 0L
                    }

                    render(session, now)
                    delay(TICK_MILLIS)
                }
            }
        }
    }

    private fun mySession(): FocusSession? =
        store.session.value?.takeIf { it.habitId == habitId }

    private fun render(session: FocusSession?, now: Long) {
        val mine = session?.takeIf { it.habitId == habitId }

        _uiState.update { state ->
            when {
                mine == null -> state.copy(
                    timerActive = false,
                    timerRunning = false,
                    timerRemaining = state.timerTotal,
                    timerElapsed = 0L,
                    stopwatchActive = false,
                    stopwatchRunning = false,
                    stopwatchElapsed = 0L
                )

                mine.mode == FocusMode.TIMER -> state.copy(
                    timerActive = true,
                    timerRunning = mine.isRunning,
                    timerTotal = mine.totalSeconds,
                    timerRemaining = mine.remainingSecondsAt(now),
                    timerElapsed = mine.elapsedSecondsAt(now),
                    stopwatchActive = false,
                    stopwatchRunning = false,
                    stopwatchElapsed = 0L
                )

                else -> state.copy(
                    timerActive = false,
                    timerRunning = false,
                    timerRemaining = state.timerTotal,
                    timerElapsed = 0L,
                    stopwatchActive = true,
                    stopwatchRunning = mine.isRunning,
                    stopwatchElapsed = mine.elapsedSecondsAt(now)
                )
            }
        }
    }

    private fun processBrainrotDrops(delta: Long) {
        coinAccumulatorMillis += delta
        while (coinAccumulatorMillis >= 100L) {
            coinAccumulatorMillis -= 100L
            val chance = Random.nextFloat()
            val gain = when {
                chance > 0.98f -> Random.nextInt(100, 1000)
                chance > 0.85f -> Random.nextInt(10, 50)
                chance > 0.40f -> Random.nextInt(2, 10)
                else -> 1
            }
            coinManager.addCoins(gain)
            _coinEvents.tryEmit(gain)
        }
    }

    // ---------------------------------------------------------------------
    // Komande ka servisu
    // ---------------------------------------------------------------------

    fun setTimerTotal(minutes: Long) {
        val seconds = (minutes * 60L).coerceAtLeast(60L)
        // Ako sesija za ovu naviku već postoji, promena trajanja je zatvara i
        // upisuje ono što je do sada odrađeno - inače bi novo trajanje bilo
        // primenjeno na već potrošeno vreme.
        if (_uiState.value.timerActive) {
            FocusService.stopAndSave(appContext)
        }
        store.setLastTimerSeconds(seconds)
        _uiState.update { it.copy(timerTotal = seconds, timerRemaining = seconds, timerElapsed = 0L) }
    }

    /** START / PAUSE / RESUME za tajmer, u zavisnosti od stanja sesije. */
    fun toggleTimer() {
        val mine = mySession()
        when {
            mine != null && mine.mode == FocusMode.TIMER && mine.isRunning ->
                FocusService.pause(appContext)

            mine != null && mine.mode == FocusMode.TIMER ->
                FocusService.resume(appContext)

            else ->
                FocusService.startTimer(appContext, habitId, _uiState.value.timerTotal)
        }
    }

    fun toggleStopwatch() {
        val mine = mySession()
        when {
            mine != null && mine.mode == FocusMode.STOPWATCH && mine.isRunning ->
                FocusService.pause(appContext)

            mine != null && mine.mode == FocusMode.STOPWATCH ->
                FocusService.resume(appContext)

            else ->
                FocusService.startStopwatch(appContext, habitId)
        }
    }

    /** Upisuje protekle sekunde u bazu i gasi sesiju - i za tajmer i za štopericu. */
    fun stopAndSave() {
        FocusService.stopAndSave(appContext)
    }

    fun convertCoinsToWishes(wishesToBuy: Int) {
        val cost = wishesToBuy * 95000
        if (cost > 0 && _uiState.value.totalCoins >= cost) {
            coinManager.removeCoins(cost)
            coinManager.addWishes(wishesToBuy)
        }
    }

    companion object {
        private const val TICK_MILLIS = 100L
    }
}