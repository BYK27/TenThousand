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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tenthousand.data.local.dao.HabitDao
import java.text.NumberFormat
import java.util.Locale

val habitColors = listOf(
    Color(0xFF6650a4), Color(0xFFE91E63), Color(0xFFF44336),
    Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF009688),
    Color(0xFF2196F3), Color(0xFF3F51B5)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitListScreen(
    dao: HabitDao,
    onOpenHabit: (Long) -> Unit,
    onOpenShop: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val viewModel: HabitListViewModel = viewModel(
        factory = HabitListViewModelFactory(dao, context)
    )

    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var selectedHabit by remember { mutableStateOf<HabitEntity?>(null) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showCheatDialog by remember { mutableStateOf(false) }

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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Habits",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Long click for Developer Cheats
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showCheatDialog = true }
                            )
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Coins",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = NumberFormat.getNumberInstance(Locale.US).format(state.totalCoins),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD700)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Wishes",
                                tint = Color(0xFFB388FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = NumberFormat.getNumberInstance(Locale.US).format(state.totalWishes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFB388FF)
                            )
                        }
                    }
                }

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

            FloatingActionButton(
                onClick = onOpenShop,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 16.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Shop")
            }
        }
    }

    if (showCheatDialog) {
        DeveloperCheatDialog(
            onAddCoins = { viewModel.addCheatCoins(it) },
            onAddWishes = { viewModel.addCheatWishes(it) },
            onDismiss = { showCheatDialog = false }
        )
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

@Composable
fun DeveloperCheatDialog(
    onAddCoins: (Int) -> Unit,
    onAddWishes: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var coinsInput by remember { mutableStateOf("1000000") }
    var wishesInput by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Developer Cheats", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
        text = {
            Column {
                OutlinedTextField(
                    value = coinsInput,
                    onValueChange = { coinsInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Add Coins") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = wishesInput,
                    onValueChange = { wishesInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Add Wishes") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val c = coinsInput.toIntOrNull() ?: 0
                    val w = wishesInput.toIntOrNull() ?: 0
                    if (c > 0) onAddCoins(c)
                    if (w > 0) onAddWishes(w)
                    onDismiss()
                }
            ) { Text("HACK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRow(habit: HabitEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    initialName: String, initialColor: Int, title: String,
    onConfirm: (String, Int) -> Unit, onCancel: () -> Unit, onDelete: (() -> Unit)? = null
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