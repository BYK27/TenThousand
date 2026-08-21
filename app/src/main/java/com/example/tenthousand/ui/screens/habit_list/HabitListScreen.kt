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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.tenthousand.BuildConfig
import com.example.tenthousand.data.local.dao.HabitDao
import java.text.NumberFormat
import java.util.Locale

val defaultHabitColors = listOf(
    Color(0xFF6650a4), Color(0xFFE91E63), Color(0xFFF44336),
    Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF009688),
    Color(0xFF2196F3), Color(0xFF3F51B5)
)

// Redosled kategorija kojim se prikazuju u editoru (prazne kategorije se preskacu).
private val colorCategoryOrder = listOf("Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Neutral")

private fun categoryForColor(colorInt: Int): String {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colorInt, hsv)
    val hue = hsv[0]
    val saturation = hsv[1]
    if (saturation < 0.15f) return "Neutral"
    return when {
        hue < 15f || hue >= 345f -> "Red"
        hue < 45f -> "Orange"
        hue < 70f -> "Yellow"
        hue < 170f -> "Green"
        hue < 200f -> "Cyan"
        hue < 260f -> "Blue"
        hue < 290f -> "Purple"
        else -> "Pink"
    }
}

private fun categorizeColors(colors: List<Int>): List<Pair<String, List<Int>>> {
    val grouped = colors.groupBy { categoryForColor(it) }
    return colorCategoryOrder.mapNotNull { category ->
        grouped[category]?.let { category to it }
    }
}

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

    val allColors = remember(state.unlockedColors) {
        defaultHabitColors.map { it.toArgb() } + state.unlockedColors.toList()
    }

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

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {},
                                // Cheat meni je dostupan SAMO u debug build-u.
                                // U release APK-u (BuildConfig.DEBUG == false)
                                // dugi pritisak ovde ne radi nista.
                                onLongClick = { if (BuildConfig.DEBUG) showCheatDialog = true }
                            )
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(NumberFormat.getNumberInstance(Locale.US).format(state.totalCoins), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFFFFD700))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFB388FF), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(NumberFormat.getNumberInstance(Locale.US).format(state.totalWishes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color(0xFFB388FF))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Total Focus Time: ${formatSleekTotal(totalSecondsAllHabits)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
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
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 16.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Shop")
            }
        }
    }

    if (showCheatDialog && BuildConfig.DEBUG) {
        DeveloperCheatDialog(
            onAddCoins = { viewModel.addCheatCoins(it) },
            onAddWishes = { viewModel.addCheatWishes(it) },
            onDismiss = { showCheatDialog = false }
        )
    }

    if (showAddDialog) {
        HabitEditorDialog(
            initialName = "",
            initialColor = allColors.firstOrNull() ?: defaultHabitColors[0].toArgb(),
            initialBackground = null,
            availableColors = allColors,
            unlockedBackgrounds = state.unlockedBackgrounds,
            title = "New Habit",
            onConfirm = { name, color, bg ->
                viewModel.createHabit(name, color, bg)
                showAddDialog = false
            },
            onCancel = { showAddDialog = false }
        )
    }

    if (showOptionsDialog && selectedHabit != null) {
        HabitEditorDialog(
            initialName = selectedHabit!!.name,
            initialColor = selectedHabit!!.color,
            initialBackground = selectedHabit!!.background,
            availableColors = allColors,
            unlockedBackgrounds = state.unlockedBackgrounds,
            title = "Edit Habit",
            onConfirm = { name, color, bg ->
                viewModel.updateHabit(selectedHabit!!.id, name, color, bg)
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
fun DeveloperCheatDialog(onAddCoins: (Int) -> Unit, onAddWishes: (Int) -> Unit, onDismiss: () -> Unit) {
    var coinsInput by remember { mutableStateOf("1000000") }
    var wishesInput by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Developer Cheats", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
        text = {
            Column {
                OutlinedTextField(value = coinsInput, onValueChange = { coinsInput = it.filter { c -> c.isDigit() } }, label = { Text("Add Coins") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = wishesInput, onValueChange = { wishesInput = it.filter { c -> c.isDigit() } }, label = { Text("Add Wishes") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = coinsInput.toIntOrNull() ?: 0
                val w = wishesInput.toIntOrNull() ?: 0
                if (c > 0) onAddCoins(c)
                if (w > 0) onAddWishes(w)
                onDismiss()
            }) { Text("HACK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRow(habit: HabitEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(8.dp).height(64.dp).background(Color(habit.color)))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = habit.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        }
        Text(text = formatSleekTotal(habit.totalSeconds), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HabitEditorDialog(
    initialName: String,
    initialColor: Int,
    initialBackground: String?,
    availableColors: List<Int>,
    unlockedBackgrounds: Set<String>,
    title: String,
    onConfirm: (String, Int, String?) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableIntStateOf(initialColor) }
    var selectedBackground by remember { mutableStateOf(initialBackground) }

    val colorCategories = remember(availableColors) { categorizeColors(availableColors) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("e.g. Reading") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(16.dp))
                Text("Theme Color (${availableColors.size} Unlocked)", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                // Boje grupisane po nijansi (Red, Orange, Yellow, ...) umesto
                // jedne ravne mreže - lakše je pronaći boju kad ih ima puno
                // (nakon gacha pull-ova).
                Column(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    colorCategories.forEach { (category, colors) ->
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(colors) { colorInt ->
                                val color = Color(colorInt)
                                val isSelected = colorInt == selectedColor
                                Box(
                                    modifier = Modifier
                                        .size(40.dp).clip(CircleShape).background(color)
                                        .border(width = if (isSelected) 3.dp else 0.dp, color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent, shape = CircleShape)
                                        .clickable { selectedColor = colorInt }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (unlockedBackgrounds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Magical Background", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    val bgOptions = listOf(null) + unlockedBackgrounds.toList()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bgOptions) { bg ->
                            val isSelected = bg == selectedBackground
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedBackground = bg }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = bg ?: "None",
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedColor, selectedBackground) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }; Spacer(modifier = Modifier.width(8.dp)) }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}

fun formatSleekTotal(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h : ${m}m" else "${m}m"
}

fun formatSleekTimer(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}