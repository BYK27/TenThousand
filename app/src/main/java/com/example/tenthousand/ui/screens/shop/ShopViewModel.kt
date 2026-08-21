package com.example.tenthousand.ui.screens.shop

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tenthousand.util.CoinManager
import com.example.tenthousand.util.GachaManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ShopUiState(
    val totalWishes: Int = 0,
    val pityCounter: Int = 0,
    val dailyBackground: String = "",
    val isDailyOwned: Boolean = false,
    val availableBackgrounds: List<String> = emptyList()
)

sealed class PullResult {
    data class Background(val name: String) : PullResult()
    data class ColorReward(val colorInt: Int) : PullResult()
}

class ShopViewModel(context: Context) : ViewModel() {
    private val coinManager = CoinManager.getInstance(context)
    private val gachaManager = GachaManager.getInstance(context)

    private val _uiState = MutableStateFlow(ShopUiState(availableBackgrounds = gachaManager.availableBackgrounds))
    val uiState = _uiState.asStateFlow()

    private val _pullEvents = MutableSharedFlow<PullResult>()
    val pullEvents = _pullEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                coinManager.wishes,
                gachaManager.pityCounter,
                gachaManager.dailyBackground,
                gachaManager.unlockedBackgrounds
            ) { wishes, pity, daily, unlocked ->
                ShopUiState(
                    totalWishes = wishes,
                    pityCounter = pity,
                    dailyBackground = daily,
                    isDailyOwned = unlocked.contains(daily),
                    availableBackgrounds = gachaManager.availableBackgrounds
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setCustomDailyBackground(bgName: String) {
        gachaManager.setDailyBackground(bgName)
    }

    fun pullWish(times: Int = 1) {
        viewModelScope.launch {
            if (_uiState.value.totalWishes < times) return@launch

            for (i in 0 until times) {
                coinManager.addWishes(-1)

                gachaManager.incrementPity()
                val currentPity = gachaManager.pityCounter.value
                val isGuaranteed = currentPity >= 90
                val isRngWin = Random.nextFloat() < 0.006f // 0.6%

                if ((isGuaranteed || isRngWin) && !_uiState.value.isDailyOwned) {
                    val bg = _uiState.value.dailyBackground
                    gachaManager.unlockBackground(bg)
                    gachaManager.resetPity()
                    _pullEvents.emit(PullResult.Background(bg))
                    break
                } else {
                    val r = Random.nextInt(100, 256)
                    val g = Random.nextInt(100, 256)
                    val b = Random.nextInt(100, 256)
                    val newColor = Color(r, g, b).toArgb()

                    gachaManager.unlockColor(newColor)
                    _pullEvents.emit(PullResult.ColorReward(newColor))
                }
            }
        }
    }
}