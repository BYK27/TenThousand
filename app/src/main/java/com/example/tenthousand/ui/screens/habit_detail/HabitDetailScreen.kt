package com.example.tenthousand.ui.screens.habit_detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.ui.screens.habit_list.formatSeconds

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    habitId: Long,
    dao: HabitDao,
    onBack: () -> Unit
) {
    val viewModel: HabitDetailViewModel = viewModel(
        factory = HabitDetailViewModelFactory(habitId, dao)
    )

    val ui by viewModel.uiState.collectAsState()
    val habit = ui.habit

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopAppBar(
            title = { Text(habit?.name ?: "Habit") },
            navigationIcon = { /* back icon here */ }
        )
        Spacer(Modifier.height(8.dp))

        Text(
            "Total: ${formatSeconds(ui.habit?.totalSeconds ?: 0L)}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(16.dp))

        val pagerState = rememberPagerState(initialPage = 0) { 2 }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> TimerPage(ui, viewModel)
                1 -> StopwatchPage(ui, viewModel)
            }
        }
    }
}

@Composable
fun TimerPage(ui: HabitDetailUiState, viewModel: HabitDetailViewModel) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    // Listen for the finished event
    LaunchedEffect(ui.timerFinishedEvent) {
        if (ui.timerFinishedEvent) {
            playAlarmAndVibrate(context)
            viewModel.consumeTimerFinishedEvent() // Reset it immediately
        }
    }

    // Progress calculation
    val progress = if (ui.timerTotal > 0) {
        ui.timerRemaining.toFloat() / ui.timerTotal.toFloat()
    } else 0f

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Focus Timer", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 12.dp,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = formatSeconds(ui.timerRemaining),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable {
                    // Only allow changing time when timer is paused
                    if (!ui.timerRunning) showDialog = true
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            if (ui.timerRunning) viewModel.pauseTimer()
            else viewModel.startTimer()
        }) {
            Text(if (ui.timerRunning) "Pause" else "Start")
        }
    }

    if (showDialog) {
        var input by remember { mutableStateOf((ui.timerTotal / 60).toString()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Set Timer (minutes)") },
            text = {
                TextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = input.toLongOrNull() ?: 25L
                    viewModel.setTimerTotal(minutes)
                    showDialog = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StopwatchPage(ui: HabitDetailUiState, viewModel: HabitDetailViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Stopwatch", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            CircularProgressIndicator(
                progress = { 1f }, // Full circle for stopwatch
                strokeWidth = 12.dp,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = formatSeconds(ui.stopwatchElapsed),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ui.stopwatchRunning) viewModel.pauseStopwatch()
                else viewModel.startStopwatch()
            }) {
                Text(if (ui.stopwatchRunning) "Pause" else "Start")
            }

            // Only show save button if we have actual time to credit
            if (ui.stopwatchElapsed > 0 && !ui.stopwatchRunning) {
                Button(onClick = { viewModel.stopAndCreditStopwatch() }) {
                    Text("Save & Reset")
                }
            }
        }
    }
}

fun playAlarmAndVibrate(context: Context) {
    // 1. Handle Vibration
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)) // Vibrate for 500ms
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }

    // 2. Play Default Notification Sound
    try {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, alarmUri)
        ringtone.play()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}