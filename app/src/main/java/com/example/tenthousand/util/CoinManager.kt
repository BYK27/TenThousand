package com.example.tenthousand.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CoinManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("coin_prefs", Context.MODE_PRIVATE)

    private val _coins = MutableStateFlow(prefs.getInt("TOTAL_COINS", 0))
    val coins: StateFlow<Int> = _coins.asStateFlow()

    private val _wishes = MutableStateFlow(prefs.getInt("TOTAL_WISHES", 0))
    val wishes: StateFlow<Int> = _wishes.asStateFlow()

    fun addCoins(amount: Int) {
        val newTotal = _coins.value + amount
        prefs.edit().putInt("TOTAL_COINS", newTotal).apply()
        _coins.value = newTotal
    }

    fun removeCoins(amount: Int) {
        val newTotal = (_coins.value - amount).coerceAtLeast(0)
        prefs.edit().putInt("TOTAL_COINS", newTotal).apply()
        _coins.value = newTotal
    }

    fun addWishes(amount: Int) {
        val newTotal = _wishes.value + amount
        prefs.edit().putInt("TOTAL_WISHES", newTotal).apply()
        _wishes.value = newTotal
    }

    companion object {
        @Volatile
        private var INSTANCE: CoinManager? = null

        fun getInstance(context: Context): CoinManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CoinManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}