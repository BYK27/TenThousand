package com.example.tenthousand.ui.screens.habit_list

import HabitEntity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HabitListViewModel(private val dao: HabitDao) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitListUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeHabits()
    }

    private fun observeHabits() {
        viewModelScope.launch {
            dao.observeAll().collect { habits ->
                _uiState.update { it.copy(habits = habits, isLoading = false) }
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
}

data class HabitListUiState(
    val habits: List<HabitEntity> = emptyList(),
    val isLoading: Boolean = true
)