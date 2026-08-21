package com.example.tenthousand.ui.screens.shop

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tenthousand.BuildConfig

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShopScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = remember { ShopViewModel(context) }
    val ui by viewModel.uiState.collectAsState()

    var showRewardDialog by remember { mutableStateOf(false) }
    var showCheatDialog by remember { mutableStateOf(false) }
    var currentRewards by remember { mutableStateOf<List<PullResult>>(emptyList()) }

    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Jedan sekvencijalni collect - nema vise scope.launch po eventu,
        // pa nema race-a izmedju vise pull-ova koji se preklapaju.
        viewModel.pullEvents.collect { results ->
            flashAlpha.animateTo(1f, tween(100))
            currentRewards = results
            showRewardDialog = true
            flashAlpha.animateTo(0f, tween(500))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (ui.dailyBackground.isNotEmpty()) {
            MagicalBackground(ui.dailyBackground)
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Wishes", tint = Color(0xFFB388FF), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${ui.totalWishes}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFFB388FF))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Long click na ovo otvara cheat meni za promenu pozadine.
                // Dostupno SAMO u debug build-u (BuildConfig.DEBUG) - u
                // release APK-u long click ovde ne radi nista.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { if (BuildConfig.DEBUG) showCheatDialog = true }
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = "DAILY FEATURED",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = ui.dailyBackground.uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                if (ui.isDailyOwned) {
                    Text("✓ OWNED", color = Color.Green, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    progress = { ui.pityCounter / 90f },
                    modifier = Modifier.fillMaxWidth(0.8f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFB388FF),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text(
                    text = "Pity: ${ui.pityCounter} / 90",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = { viewModel.pullWish(1) },
                        enabled = ui.totalWishes >= 1 && !ui.isDailyOwned,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.2f)),
                        modifier = Modifier.height(56.dp).weight(1f).padding(end = 8.dp)
                    ) {
                        Text("Pull 1x", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.pullWish(10) },
                        enabled = ui.totalWishes >= 10 && !ui.isDailyOwned,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB388FF)),
                        modifier = Modifier.height(56.dp).weight(1f).padding(start = 8.dp)
                    ) {
                        Text("Pull 10x", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showCheatDialog && BuildConfig.DEBUG) {
            AlertDialog(
                onDismissRequest = { showCheatDialog = false },
                containerColor = Color(0xFF1A1A2E),
                title = { Text("Change Daily Background", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        ui.availableBackgrounds.forEach { bgName ->
                            TextButton(
                                onClick = {
                                    viewModel.setCustomDailyBackground(bgName)
                                    showCheatDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(bgName, color = if (bgName == ui.dailyBackground) Color(0xFFB388FF) else Color.White, fontSize = 18.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCheatDialog = false }) { Text("Close") }
                }
            )
        }

        if (flashAlpha.value > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha.value)))
        }

        if (showRewardDialog && currentRewards.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showRewardDialog = false },
                containerColor = Color(0xFF1A1A2E),
                title = {
                    Text(
                        if (currentRewards.size > 1) "${currentRewards.size} Rewards Received!" else "Reward Received!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 240.dp).fillMaxWidth()
                    ) {
                        items(currentRewards) { r ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                when (r) {
                                    is PullResult.Background -> {
                                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFFD700), modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            r.name,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2
                                        )
                                    }
                                    is PullResult.ColorReward -> {
                                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(r.colorInt)))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showRewardDialog = false }) { Text("Awesome") }
                }
            )
        }
    }
}