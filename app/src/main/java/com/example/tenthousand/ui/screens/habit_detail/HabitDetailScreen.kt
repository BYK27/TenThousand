package com.example.tenthousand.ui.screens.habit_detail

import android.util.Log
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
import com.example.tenthousand.ui.components.StopwatchComponent
import com.example.tenthousand.ui.components.TimerComponent
import com.example.tenthousand.ui.screens.habit_list.formatSeconds
import com.example.tenthousand.ui.screens.habit_list.formatTotal
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        // Horizontal pager for Timer and Stopwatch
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
    val totalSeconds = remember { mutableStateOf(25 * 60L) } // default 25 min
    var showDialog by remember { mutableStateOf(false) }
    val remaining = ui.timerRemaining.takeIf { it > 0 } ?: totalSeconds.value
    val progress = remaining.toFloat() / totalSeconds.value

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Timer", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 12.dp,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                formatSeconds(remaining),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clickable { showDialog = true } // click to change time
            )
        }
        Spacer(Modifier.height(16.dp))

        val running = ui.timerRunning
        Button(onClick = {
            if (running) viewModel.setTimerRunning(false)
            else viewModel.setTimerRunning(true)
        }) {
            Text(if (running) "Pause" else "Start")
        }

        // Timer ticking
        LaunchedEffect(running) {
            if (running) {
                var sec = remaining
                while (sec > 0 && ui.timerRunning) {
                    kotlinx.coroutines.delay(1000)
                    sec -= 1
                    viewModel.setTimerRemaining(sec)
                }
                if (sec <= 0) {
                    viewModel.creditSeconds(totalSeconds.value)
                    viewModel.setTimerRunning(false)
                    viewModel.setTimerRemaining(totalSeconds.value)
                }
            }
        }
    }

    // Dialog to set timer
    if (showDialog) {
        var input by remember { mutableStateOf((totalSeconds.value / 60).toString()) } // minutes
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
                    val minutes = input.toLongOrNull() ?: 25
                    totalSeconds.value = minutes * 60
                    viewModel.setTimerRemaining(totalSeconds.value)
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
    val elapsed = ui.stopwatchElapsed

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Stopwatch", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            CircularProgressIndicator(
                progress = 0f, // you can calculate progress if you want
                strokeWidth = 12.dp,
                modifier = Modifier.fillMaxSize()
            )
            Text(formatSeconds(elapsed), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        val running = ui.stopwatchRunning
        Button(onClick = {
            if (running) viewModel.setStopwatchRunning(false)
            else viewModel.setStopwatchRunning(true)
        }) {
            Text(if (running) "Pause" else "Start")
        }

        LaunchedEffect(running) {
            if (running) {
                var sec = elapsed
                while (ui.stopwatchRunning) {
                    kotlinx.coroutines.delay(1000)
                    sec += 1
                    viewModel.setStopwatchElapsed(sec)
                    viewModel.creditSeconds(1)
                }
            }
        }
    }
}