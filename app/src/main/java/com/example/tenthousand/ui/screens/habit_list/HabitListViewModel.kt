package com.example.tenthousand.ui.screens.habit_list

import HabitEntity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.util.CoinManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HabitListUiState(
    val habits: List<HabitEntity> = emptyList(),
    val isLoading: Boolean = true,
    val totalCoins: Int = 0,
    val totalWishes: Int = 0
)

class HabitListViewModel(
    private val dao: HabitDao,
    context: Context
) : ViewModel() {

    private val coinManager = CoinManager(context)
    private val _uiState = MutableStateFlow(HabitListUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeHabits()
        observeCurrencies()
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

    fun createHabit(name: String, color: Int) {
        viewModelScope.launch {
            dao.insert(HabitEntity(name = name, totalSeconds = 0, color = color))
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch { dao.delete(habit) }
    }

    fun updateHabit(habitId: Long, newName: String, newColor: Int) {
        viewModelScope.launch {
            val habit = dao.observeByIdOnce(habitId)
            if (habit != null) {
                dao.update(habit.copy(name = newName, color = newColor))
            }
        }
    }

    // --- Developer Cheats ---
    fun addCheatCoins(amount: Int) {
        coinManager.addCoins(amount)
    }

    fun addCheatWishes(amount: Int) {
        coinManager.addWishes(amount)
    }
}