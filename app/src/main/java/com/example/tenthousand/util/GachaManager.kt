package com.example.tenthousand.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class GachaManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gacha_prefs", Context.MODE_PRIVATE)

    val availableBackgrounds = listOf("Galactic Nebula", "Cyber Rain", "Mystic Aura")

    private val _pityCounter = MutableStateFlow(prefs.getInt("PITY", 0))
    val pityCounter: StateFlow<Int> = _pityCounter.asStateFlow()

    private val _unlockedBackgrounds = MutableStateFlow(
        prefs.getStringSet("UNLOCKED_BGS", emptySet()) ?: emptySet()
    )
    val unlockedBackgrounds: StateFlow<Set<String>> = _unlockedBackgrounds.asStateFlow()

    private val _unlockedColors = MutableStateFlow(
        prefs.getStringSet("UNLOCKED_COLORS", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    )
    val unlockedColors: StateFlow<Set<Int>> = _unlockedColors.asStateFlow()

    private val _dailyBackground = MutableStateFlow(calculateDailyBackground())
    val dailyBackground: StateFlow<String> = _dailyBackground.asStateFlow()

    private fun calculateDailyBackground(): String {
        val currentDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        val rng = Random(currentDay)
        return availableBackgrounds[rng.nextInt(availableBackgrounds.size)]
    }

    fun incrementPity() {
        val newPity = _pityCounter.value + 1
        prefs.edit().putInt("PITY", newPity).apply()
        _pityCounter.value = newPity
    }

    fun resetPity() {
        prefs.edit().putInt("PITY", 0).apply()
        _pityCounter.value = 0
    }

    fun unlockBackground(bgId: String) {
        val newSet = _unlockedBackgrounds.value + bgId
        prefs.edit().putStringSet("UNLOCKED_BGS", newSet).apply()
        _unlockedBackgrounds.value = newSet
    }

    fun unlockColor(colorInt: Int) {
        val newSet = _unlockedColors.value + colorInt
        prefs.edit().putStringSet("UNLOCKED_COLORS", newSet.map { it.toString() }.toSet()).apply()
        _unlockedColors.value = newSet
    }

    companion object {
        @Volatile
        private var INSTANCE: GachaManager? = null

        fun getInstance(context: Context): GachaManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GachaManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}