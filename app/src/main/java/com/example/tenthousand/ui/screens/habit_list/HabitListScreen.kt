package com.example.tenthousand.ui.screens.habit_list

import HabitEntity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tenthousand.data.local.dao.HabitDao

// Predefined modern color palette
val habitColors = listOf(
    Color(0xFF6650a4), // Purple
    Color(0xFFE91E63), // Pink
    Color(0xFFF44336), // Red
    Color(0xFFFF9800), // Orange
    Color(0xFF4CAF50), // Green
    Color(0xFF009688), // Teal
    Color(0xFF2196F3), // Blue
    Color(0xFF3F51B5)  // Indigo
)

@Composable
fun HabitListScreen(
    dao: HabitDao,
    onOpenHabit: (Long) -> Unit
) {
    val viewModel: HabitListViewModel = viewModel(
        factory = HabitListViewModelFactory(dao)
    )

    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var selectedHabit by remember { mutableStateOf<HabitEntity?>(null) }
    var showOptionsDialog by remember { mutableStateOf(false) }

    // Calculate total time across all habits
    val totalSecondsAllHabits = state.habits.sumOf { it.totalSeconds }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Habit")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "My Habits",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Total Focus Time: ${formatSleekTotal(totalSecondsAllHabits)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.habits) { habit ->
                    HabitRow(
                        habit = habit,
                        onClick = { onOpenHabit(habit.id) },
                        onLongClick = {
                            selectedHabit = habit
                            showOptionsDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        HabitEditorDialog(
            initialName = "",
            initialColor = habitColors[0].toArgb(),
            title = "New Habit",
            onConfirm = { name, color ->
                viewModel.createHabit(name, color)
                showAddDialog = false
            },
            onCancel = { showAddDialog = false }
        )
    }

    if (showOptionsDialog && selectedHabit != null) {
        HabitEditorDialog(
            initialName = selectedHabit!!.name,
            initialColor = selectedHabit!!.color,
            title = "Edit Habit",
            onConfirm = { name, color ->
                viewModel.updateHabit(selectedHabit!!.id, name, color)
                showOptionsDialog = false
            },
            onCancel = { showOptionsDialog = false },
            onDelete = {
                viewModel.deleteHabit(selectedHabit!!)
                showOptionsDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRow(
    habit: HabitEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(end = 16.dp), // Removed start padding to attach color strip to edge
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Sleek color indicator strip
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(64.dp)
                    .background(Color(habit.color))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = habit.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = formatSleekTotal(habit.totalSeconds),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HabitEditorDialog(
    initialName: String,
    initialColor: Int,
    title: String,
    onConfirm: (String, Int) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableIntStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Reading") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Theme Color", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal Color Picker
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(habitColors) { color ->
                        val isSelected = color.toArgb() == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color.toArgb() }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedColor) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}

fun formatSleekTotal(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h : ${m}m" else "${m}m"
}

fun formatSleekTimer(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}