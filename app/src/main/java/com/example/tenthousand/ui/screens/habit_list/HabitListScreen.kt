package com.example.tenthousand.ui.screens.habit_list

import HabitEntity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tenthousand.data.local.dao.HabitDao

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
    var renameInput by remember { mutableStateOf("") }

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
                            renameInput = habit.name
                            showOptionsDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            onAdd = { name ->
                viewModel.createHabit(name)
                showAddDialog = false
            },
            onCancel = { showAddDialog = false }
        )
    }

    if (showOptionsDialog && selectedHabit != null) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Edit Habit") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Habit Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val newName = renameInput.trim()
                    if (newName.isNotEmpty()) {
                        viewModel.renameHabit(selectedHabit!!.id, newName)
                    }
                    showOptionsDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteHabit(selectedHabit!!)
                    showOptionsDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
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
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = habit.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = formatSleekTotal(habit.totalSeconds),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

@Composable
fun AddHabitDialog(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("New Habit") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g. Unreal Engine") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onAdd(name.trim()) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}