package com.example.tenthousand.ui.screens.habit_list

import HabitEntity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    // State for rename/delete dialog
    var selectedHabit by remember { mutableStateOf<HabitEntity?>(null) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
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

    // Add Habit Dialog
    if (showAddDialog) {
        AddHabitDialog(
            onAdd = { name ->
                viewModel.createHabit(name)
                showAddDialog = false
            },
            onCancel = { showAddDialog = false }
        )
    }

    // Rename/Delete Dialog
    if (showOptionsDialog && selectedHabit != null) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Edit Habit") },
            text = {
                TextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Habit Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameInput.trim()
                    if (newName.isNotEmpty()) {
                        viewModel.renameHabit(selectedHabit!!.id, newName)
                    }
                    showOptionsDialog = false
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteHabit(selectedHabit!!)
                        showOptionsDialog = false
                    }) {
                        Text("Delete")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showOptionsDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun HabitRow(
    habit: HabitEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatTotal(habit.totalSeconds),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


@Composable
fun AddHabitDialog(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim()) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
        title = { Text("New Habit") },
        text = {
            TextField(value = name, onValueChange = { name = it }, placeholder = { Text("Habit name") })
        }
    )
}

fun formatTotal(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return String.format("%dh %02dm", h, m)
}

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
