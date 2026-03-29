package com.example.tenthousand.ui.screens.habit_detail

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.ui.screens.habit_list.formatSleekTimer
import com.example.tenthousand.ui.screens.habit_list.formatSleekTotal

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

    // Safely extract the chosen color or fallback to primary theme color
    val activeColor = habit?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = habit?.name ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Focus Time: ${formatSleekTotal(ui.habit?.totalSeconds ?: 0L)}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(8.dp))

        val pagerState = rememberPagerState(initialPage = 0) { 2 }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                // We pass the activeColor downwards into the pages
                0 -> TimerPage(ui, viewModel, activeColor)
                1 -> StopwatchPage(ui, viewModel, activeColor)
            }
        }
    }
}

@Composable
fun TimerPage(ui: HabitDetailUiState, viewModel: HabitDetailViewModel, themeColor: Color) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ui.timerFinishedEvent) {
        if (ui.timerFinishedEvent) {
            playAlarmAndVibrate(context)
            viewModel.consumeTimerFinishedEvent()
        }
    }

    val progress = if (ui.timerTotal > 0) {
        ui.timerRemaining.toFloat() / ui.timerTotal.toFloat()
    } else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = "Timer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 24.dp,
                strokeCap = StrokeCap.Round,
                color = themeColor, // <--- Apply dynamically chosen color
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(280.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!ui.timerRunning) showDialog = true
                    }
            )

            Text(
                text = formatSleekTimer(ui.timerRemaining),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                fontWeight = FontWeight.Light
            )
        }

        Button(
            onClick = {
                if (ui.timerRunning) viewModel.pauseTimer()
                else viewModel.startTimer()
            },
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                // Use error (red) when running/pausable, otherwise use theme color
                containerColor = if (ui.timerRunning) MaterialTheme.colorScheme.error else themeColor
            ),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(64.dp)
        ) {
            Text(
                text = if (ui.timerRunning) "STOP" else "START",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }

    if (showDialog) {
        var input by remember { mutableStateOf((ui.timerTotal / 60).toString()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Set Timer") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    label = { Text("Minutes") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        focusedLabelColor = themeColor,
                        cursorColor = themeColor
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minutes = input.toLongOrNull() ?: 25L
                        viewModel.setTimerTotal(minutes)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) { Text("Cancel", color = themeColor) }
            }
        )
    }
}

@Composable
fun StopwatchPage(ui: HabitDetailUiState, viewModel: HabitDetailViewModel, themeColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.height(32.dp))

        Text("Stopwatch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                strokeWidth = 24.dp,
                color = themeColor, // <--- Apply dynamically chosen color
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(280.dp)
            )
            Text(
                text = formatSleekTimer(ui.stopwatchElapsed),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                fontWeight = FontWeight.Light
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (ui.stopwatchRunning) viewModel.pauseStopwatch()
                    else viewModel.startStopwatch()
                },
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ui.stopwatchRunning) MaterialTheme.colorScheme.error else themeColor
                ),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(64.dp)
            ) {
                Text(
                    text = if (ui.stopwatchRunning) "PAUSE" else "START",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (ui.stopwatchElapsed > 0 && !ui.stopwatchRunning) {
                OutlinedButton(
                    onClick = { viewModel.stopAndCreditStopwatch() },
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(56.dp)
                ) {
                    Text("SAVE & RESET", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }
        }
    }
}

fun playAlarmAndVibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }

    try {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, alarmUri)
        ringtone.play()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}