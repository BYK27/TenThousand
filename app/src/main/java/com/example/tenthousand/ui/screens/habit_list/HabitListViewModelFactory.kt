package com.example.tenthousand.ui.screens.habit_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tenthousand.data.local.dao.HabitDao

class HabitListViewModelFactory(
    private val dao: HabitDao
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitListViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
