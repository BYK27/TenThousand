package com.example.tenthousand.ui.screens.habit_list

import HabitEntity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.data.repository.HabitRepository
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

    fun createHabit(name: String) {
        viewModelScope.launch {
            dao.insert(HabitEntity(name = name, totalSeconds = 0))
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch { dao.delete(habit) }
    }

    fun renameHabit(habitId: Long, newName: String) {
        viewModelScope.launch {
            val habit = dao.observeByIdOnce(habitId) // We'll add this helper below
            if (habit != null) {
                dao.update(habit.copy(name = newName))
            }
        }
    }
}

data class HabitListUiState(
    val habits: List<HabitEntity> = emptyList(),
    val isLoading: Boolean = true
)
