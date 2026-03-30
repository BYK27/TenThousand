package com.example.tenthousand.ui.screens.habit_detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tenthousand.data.local.dao.HabitDao

class HabitDetailViewModelFactory(
    private val habitId: Long,
    private val dao: HabitDao,
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitDetailViewModel(habitId, dao, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
