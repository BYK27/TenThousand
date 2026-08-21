package com.example.tenthousand.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CoinManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("coin_prefs", Context.MODE_PRIVATE)
    private val _coins = MutableStateFlow(prefs.getInt("TOTAL_COINS", 0))
    val coins: StateFlow<Int> = _coins.asStateFlow()

    fun addCoins(amount: Int) {
        val newTotal = _coins.value + amount
        prefs.edit().putInt("TOTAL_COINS", newTotal).apply()
        _coins.value = newTotal
    }
}