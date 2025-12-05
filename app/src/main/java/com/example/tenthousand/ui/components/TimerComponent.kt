package com.example.tenthousand.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tenthousand.util.TimerMode
import com.example.tenthousand.util.timerFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun TimerComponent(
    initialSeconds: Long,
    onTimerStart: () -> Unit = {},
    onTimerTick: (remainingSeconds: Long) -> Unit = {},
    onTimerPause: () -> Unit = {},
    onTimerFinish: (elapsedSeconds: Long) -> Unit = {}
) {
    var running by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(initialSeconds) }
    var startedAtOffset by remember { mutableStateOf(0L) } // used when pausing/resuming

    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>>(java.util.concurrent.atomic.AtomicReference(null)) }

    fun startFrom(secondsToRun: Long) {
        running = true
        onTimerStart()
        // use timerFlow countdown mode with start offset equals startedAtOffset
        val flow = timerFlow(TimerMode.Countdown(totalSeconds = secondsToRun), startOffsetSeconds = 0L)
        scope.launch {
            flow.onEach { tick ->
                remaining = tick.seconds
                onTimerTick(remaining)
                if (tick.seconds == 0L) {
                    running = false
                    onTimerFinish(secondsToRun)
                    // cancel downstream - the flow completes automatically on zero
                }
            }.launchIn(this)
        }
    }

    Column {
        Text(text = formatSecondsAsClock(remaining), style = MaterialTheme.typography.displaySmall)
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (!running) {
                    // start fresh
                    startFrom(initialSeconds)
                } else {
                    // pause (simple approach: restart later from 'remaining')
                    running = false
                    onTimerPause()
                }
            }) {
                Text(if (!running) "Start" else "Pause")
            }
            Button(onClick = {
                running = false
                remaining = initialSeconds
                onTimerPause()
            }) {
                Text("Stop")
            }
        }
    }
}

@Composable
fun StopwatchComponent(
    onStart: () -> Unit = {},
    onTick: (elapsedSeconds: Long) -> Unit = {},
    onPause: () -> Unit = {},
    onStop: (totalElapsedSeconds: Long) -> Unit = {}
) {
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    var tickJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    DisposableEffect(running) {
        if (running) {
            onStart()
            tickJob = scope.launch {
                timerFlow(TimerMode.Stopwatch, startOffsetSeconds = elapsed)
                    .onEach { tick ->
                        elapsed = tick.seconds
                        onTick(elapsed)
                    }
                    .launchIn(this)
            }
        } else {
            // paused/stopped
            tickJob?.cancel()
            onPause()
        }
        onDispose { /* nothing */ }
    }

    Column {
        Text(text = formatSecondsAsClock(elapsed), style = MaterialTheme.typography.displaySmall)
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { running = true }) { Text("Start") }
            Button(onClick = { running = false }) { Text("Pause") }
            Button(onClick = {
                running = false
                onStop(elapsed)
                elapsed = 0L
            }) { Text("Stop") }
        }
    }
}

fun formatSecondsAsClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
