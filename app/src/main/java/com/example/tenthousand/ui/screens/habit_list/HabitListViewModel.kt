package com.example.tenthousand.ui.screens.habit_list

import HabitEntity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.util.CoinManager
import com.example.tenthousand.util.GachaManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HabitListUiState(
    val habits: List<HabitEntity> = emptyList(),
    val isLoading: Boolean = true,
    val totalCoins: Int = 0,
    val totalWishes: Int = 0,
    val unlockedColors: Set<Int> = emptySet(),
    val unlockedBackgrounds: Set<String> = emptySet()
)

class HabitListViewModel(
    private val dao: HabitDao,
    context: Context
) : ViewModel() {

    private val coinManager = CoinManager.getInstance(context)
    private val gachaManager = GachaManager.getInstance(context)

    private val _uiState = MutableStateFlow(HabitListUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeHabits()
        observeCurrencies()
        observeGacha()
    }

    private fun observeHabits() {
        viewModelScope.launch {
            dao.observeAll().collect { habits ->
                _uiState.update { it.copy(habits = habits, isLoading = false) }
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

    private fun observeGacha() {
        viewModelScope.launch {
            gachaManager.unlockedColors.collect { colors ->
                _uiState.update { it.copy(unlockedColors = colors) }
            }
        }
        viewModelScope.launch {
            gachaManager.unlockedBackgrounds.collect { bgs ->
                _uiState.update { it.copy(unlockedBackgrounds = bgs) }
            }
        }
    }

    fun createHabit(name: String, color: Int, background: String?) {
        viewModelScope.launch {
            dao.insert(HabitEntity(name = name, totalSeconds = 0, color = color, background = background))
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch { dao.delete(habit) }
    }

    fun updateHabit(habitId: Long, newName: String, newColor: Int, newBackground: String?) {
        viewModelScope.launch {
            val habit = dao.observeByIdOnce(habitId)
            if (habit != null) {
                dao.update(habit.copy(name = newName, color = newColor, background = newBackground))
            }
        }
    }

    fun addCheatCoins(amount: Int) = coinManager.addCoins(amount)
    fun addCheatWishes(amount: Int) = coinManager.addWishes(amount)
}