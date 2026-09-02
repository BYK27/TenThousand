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
import com.example.tenthousand.util.focus.FocusCoinEngine
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
    val totalWishes: Int = 0,
    /** Trenutni množilac zarade, za prikaz iznad dugmeta. 1.0 kad nema sesije. */
    val coinMultiplier: Float = 1f
)

/**
 * ViewModel ne meri vreme i, od ove izmene, VIŠE NE DELI COIN-OVE.
 *
 * Ranije su drop-ovi nastajali ovde, u petlji vezanoj za ProcessLifecycleOwner,
 * pa su prestajali čim app ode u pozadinu - upravo taj bug se ispravlja. Sada
 * ih isplaćuje FocusService iz proteklog vremena sesije, a ViewModel samo
 * preslikava događaje iz FocusSessionStore.coinDrops u animaciju.
 *
 * Petlja koja je ostala služi isključivo osvežavanju prikaza i zato i dalje sme
 * da bude vezana za prvi plan - kad se ekran ne vidi, nema šta da se crta.
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

    init {
        _uiState.update {
            val last = store.lastTimerSeconds()
            it.copy(timerTotal = last, timerRemaining = last)
        }
        observeHabit()
        observeCurrencies()
        observeSession()
        observeCoinDrops()
        runDisplayTicker()
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
     * Filter po habitId postoji jer je sesija globalna: ako tajmer teče za
     * naviku A, a korisnik gleda ekran navike B, popup-i ne treba da lete po
     * pogrešnom ekranu. Novac je svejedno pripisan - on je globalan.
     */
    private fun observeCoinDrops() {
        viewModelScope.launch {
            store.coinDrops.collect { drop ->
                if (drop.habitId == habitId) {
                    _coinEvents.tryEmit(drop.amount)
                }
            }
        }
    }

    /**
     * Petlja samo za prikaz. Vezana je za prvi plan jer u pozadini nema šta da
     * osvežava; sat i coin-ovi u međuvremenu rade u servisu.
     *
     * MutableStateFlow ne emituje kad je nova vrednost jednaka staroj, pa se
     * rekompozicija dešava tek kad se promeni sekunda, a ne deset puta u sekundi.
     */
    private fun runDisplayTicker() {
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    render(store.session.value, System.currentTimeMillis())
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
                    stopwatchElapsed = 0L,
                    coinMultiplier = 1f
                )

                mine.mode == FocusMode.TIMER -> state.copy(
                    timerActive = true,
                    timerRunning = mine.isRunning,
                    timerTotal = mine.totalSeconds,
                    timerRemaining = mine.remainingSecondsAt(now),
                    timerElapsed = mine.elapsedSecondsAt(now),
                    stopwatchActive = false,
                    stopwatchRunning = false,
                    stopwatchElapsed = 0L,
                    coinMultiplier = FocusCoinEngine.multiplierAt(mine.elapsedMillisAt(now))
                )

                else -> state.copy(
                    timerActive = false,
                    timerRunning = false,
                    timerRemaining = state.timerTotal,
                    timerElapsed = 0L,
                    stopwatchActive = true,
                    stopwatchRunning = mine.isRunning,
                    stopwatchElapsed = mine.elapsedSecondsAt(now),
                    coinMultiplier = FocusCoinEngine.multiplierAt(mine.elapsedMillisAt(now))
                )
            }
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