package com.example.tenthousand.ui.screens.habit_detail

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tenthousand.data.local.dao.HabitDao
import com.example.tenthousand.ui.screens.habit_list.formatSleekTimer
import com.example.tenthousand.ui.screens.habit_list.formatSleekTotal
import com.example.tenthousand.ui.screens.shop.MagicalBackground
import kotlinx.coroutines.flow.SharedFlow
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * @RequiresApi(TIRAMISU) je uklonjen - minSdk je sada 33.
 *
 * playAlarmAndVibrate() je obrisan: zvuk i vibraciju na kraju tajmera sada
 * pravi notifikacioni kanal "focus_complete", koji radi i kad je app zatvoren.
 * Da je ostalo i jedno i drugo, dobio bi dupli zvuk kad si u app-u.
 */
@Composable
fun HabitDetailScreen(
    habitId: Long,
    dao: HabitDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val viewModel: HabitDetailViewModel = viewModel(
        factory = HabitDetailViewModelFactory(habitId, dao, context)
    )

    val ui by viewModel.uiState.collectAsState()
    val habit = ui.habit
    val activeColor = habit?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    var showWishDialog by remember { mutableStateOf(false) }

    val formattedCoins = NumberFormat.getNumberInstance(Locale.US).format(ui.totalCoins)
    val formattedWishes = NumberFormat.getNumberInstance(Locale.US).format(ui.totalWishes)

    Box(modifier = Modifier.fillMaxSize()) {

        // Inject the Gacha Background
        ui.habit?.background?.let { bgName ->
            MagicalBackground(bgName)
            // Overlay so the white/bright elements don't hide the UI
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }

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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (ui.habit?.background != null) Color.White else LocalContentColor.current
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = habit?.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (ui.habit?.background != null) Color.White else Color.Unspecified
                )
                Spacer(modifier = Modifier.weight(1f))

                // Currencies Container
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Coins",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedCoins,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFD700)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showWishDialog = true }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Wishes",
                            tint = Color(0xFFB388FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedWishes,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFB388FF)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Focus Time: ${formatSleekTotal(ui.habit?.totalSeconds ?: 0L)}",
                style = MaterialTheme.typography.headlineSmall,
                color = if (ui.habit?.background != null) Color.White.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))

            val pagerState = rememberPagerState(initialPage = 0) { 2 }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> TimerPage(ui, viewModel, activeColor)
                    1 -> StopwatchPage(ui, viewModel, activeColor)
                }
            }
        }

        BrainrotCoinOverlay(viewModel.coinEvents)

        if (showWishDialog) {
            WishConversionDialog(
                totalCoins = ui.totalCoins,
                onConfirm = { wishes -> viewModel.convertCoinsToWishes(wishes) },
                onDismiss = { showWishDialog = false }
            )
        }
    }
}

@Composable
fun WishConversionDialog(
    totalCoins: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val conversionRate = 95_000
    val maxAffordable = totalCoins / conversionRate
    var sliderValue by remember { mutableStateOf(0f) }

    val selectedWishes = sliderValue.roundToInt()
    val cost = selectedWishes * conversionRate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFB388FF))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Convert Wishes", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Exchange Rate: 95,000",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp).padding(horizontal = 4.dp)
                    )
                    Text(
                        text = "= 1",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFB388FF),
                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$selectedWishes",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFB388FF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFB388FF),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Cost: ${NumberFormat.getNumberInstance(Locale.US).format(cost)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (cost > 0) Color.Red else MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = if (cost > 0) Color.Red else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..(maxAffordable.toFloat().coerceAtLeast(1f)),
                    enabled = maxAffordable > 0
                )

                if (maxAffordable == 0) {
                    Text(
                        text = "Not enough coins to convert.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedWishes)
                    onDismiss()
                },
                enabled = selectedWishes > 0
            ) {
                Text("Convert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    var size: Float, val color: Color
)

@Composable
fun FocusVFX(isRunning: Boolean, themeColor: Color) {
    if (!isRunning) return

    val particles = remember { mutableStateListOf<Particle>() }

    LaunchedEffect(Unit) {
        var lastFrame = System.nanoTime()
        while (true) {
            withFrameNanos { frameTime ->
                val dt = (frameTime - lastFrame) / 1_000_000_000f
                lastFrame = frameTime

                if (Random.nextFloat() < 0.8f) {
                    val angle = Random.nextFloat() * 2 * Math.PI
                    val distance = 140f
                    val speed = Random.nextFloat() * 200f + 50f
                    val isCrazy = Random.nextFloat() > 0.9f

                    particles.add(
                        Particle(
                            x = (cos(angle) * distance).toFloat(),
                            y = (sin(angle) * distance).toFloat(),
                            vx = (cos(angle) * speed).toFloat(),
                            vy = (sin(angle) * speed).toFloat(),
                            life = 0f,
                            maxLife = Random.nextFloat() * 1.0f + 0.2f,
                            size = if (isCrazy) Random.nextFloat() * 20f + 10f else Random.nextFloat() * 8f + 2f,
                            color = if (isCrazy) Color(Random.nextLong(0xFFFFFFFF)) else themeColor
                        )
                    )
                }

                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.life += dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    if (p.life >= p.maxLife) iterator.remove()
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "aura")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)

        drawCircle(
            color = themeColor.copy(alpha = 0.2f),
            radius = (140.dp.toPx()) * pulseScale,
            center = center
        )

        particles.forEach { p ->
            val progress = p.life / p.maxLife
            val currentAlpha = 1f - progress
            drawCircle(
                color = p.color.copy(alpha = currentAlpha),
                radius = p.size * (1f - progress),
                center = Offset(center.x + p.x, center.y + p.y)
            )
        }
    }
}

// BrainrotText polja su sad `val` (nepromenljiva) - pozicija/rotacija/alpha
// se racunaju kinematicki iz `progress` (Animatable), umesto da se rucno
// mutiraju svaki frejm. Stari kod je menjao plain `var` polja na objektu u
// SnapshotStateList-i; te mutacije Compose UOPSTE ne vidi (nisu State), pa se
// offset/rotate/alpha modifier ponovo racunao samo kad bi NEKA druga,
// nepovezana rekompozicija (npr. otkucaj tajmera svake sekunde) slucajno
// prodrmala stablo - vizuelno je to izgledalo kao da brojevi "skacu" umesto
// da glatko lete. Animatable ispravno okida Compose-ov animacioni clock
// svaki frejm dok animacija traje.
private data class BrainrotText(
    val id: Long,
    val text: String,
    val isJackpot: Boolean,
    val x0: Float = 0f,
    val y0: Float = 0f,
    val vx: Float = Random.nextFloat() * 800f - 400f,
    val vy: Float = Random.nextFloat() * -600f - 200f,
    val rotation0: Float = Random.nextFloat() * 60f - 30f,
    val rotVelocity: Float = Random.nextFloat() * 200f - 100f,
    val lifeSeconds: Float = 1.2f
)

@Composable
fun BrainrotCoinOverlay(coinEvents: SharedFlow<Int>) {
    val popups = remember { mutableStateListOf<BrainrotText>() }

    LaunchedEffect(Unit) {
        coinEvents.collect { amount ->
            val isJackpot = amount >= 100
            val prefix = if (isJackpot) listOf("JACKPOT ", "MEGA ", "CRAZY ", "INSANE ").random() else ""

            popups.add(
                BrainrotText(
                    id = System.nanoTime(),
                    text = "$prefix+$amount",
                    isJackpot = isJackpot
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        popups.forEach { p ->
            // key() osigurava da svaki popup zadrzi SVOJ Animatable cak i kad
            // se lista menja (dodaju/uklanjaju stavke) - bez ovoga Compose bi
            // mogao da pomesa animaciono stanje izmedju popup-a na istoj poziciji.
            key(p.id) {
                BrainrotPopup(popup = p, onFinished = { popups.remove(p) })
            }
        }
    }
}

@Composable
private fun BrainrotPopup(popup: BrainrotText, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(popup.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = (popup.lifeSeconds * 1000).toInt(), easing = LinearEasing)
        )
        onFinished()
    }

    Text(
        text = popup.text,
        color = if (popup.isJackpot) Color.Red else Color(0xFFFFD700),
        fontSize = if (popup.isJackpot) 64.sp else 32.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.graphicsLayer {
            // graphicsLayer{} lambda se cita u draw/layer fazi, ne u
            // kompoziciji - update pozicije/rotacije/alfa svakog frejma NE
            // pokrece rekompoziciju, samo jeftin update kompozitorskog sloja.
            val t = progress.value * popup.lifeSeconds
            val gravity = 1200f
            translationX = popup.x0 + popup.vx * t
            translationY = popup.y0 + popup.vy * t + 0.5f * gravity * t * t
            rotationZ = popup.rotation0 + popup.rotVelocity * t
            alpha = (1f - progress.value).coerceIn(0f, 1f)
        }
    )
}

@Composable
fun TimerPage(ui: HabitDetailUiState, viewModel: HabitDetailViewModel, themeColor: Color) {
    var showDialog by remember { mutableStateOf(false) }

    val textColor = if (ui.habit?.background != null) Color.White else MaterialTheme.colorScheme.onBackground

    val progress = if (ui.timerTotal > 0) {
        ui.timerRemaining.toFloat() / ui.timerTotal.toFloat()
    } else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            FocusVFX(isRunning = ui.timerRunning, themeColor = themeColor)

            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 24.dp,
                strokeCap = StrokeCap.Round,
                color = themeColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (ui.habit?.background != null) 0.5f else 1f
                ),
                modifier = Modifier
                    .size(280.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (!ui.timerRunning) showDialog = true }
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatSleekTimer(ui.timerRemaining),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                    fontWeight = FontWeight.Light,
                    color = textColor
                )
                CoinMultiplierChip(multiplier = ui.coinMultiplier, visible = ui.timerActive)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { viewModel.toggleTimer() },
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ui.timerRunning) MaterialTheme.colorScheme.error else themeColor
                ),
                modifier = Modifier.fillMaxWidth(0.7f).height(64.dp)
            ) {
                Text(
                    text = when {
                        ui.timerRunning -> "PAUSE"
                        ui.timerActive -> "RESUME"
                        else -> "START"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Novo: prekid tajmera na pola sada kreditira ono što je odrađeno.
            // Ranije je pauza brisala stanje i tih 8 od 25 minuta se gubilo.
            if (ui.timerActive && !ui.timerRunning && ui.timerElapsed > 0) {
                OutlinedButton(
                    onClick = { viewModel.stopAndSave() },
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)
                ) {
                    Text("SAVE & RESET", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }
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
                TextButton(onClick = { showDialog = false }) { Text("Cancel", color = themeColor) }
            }
        )
    }
}

@Composable
fun StopwatchPage(ui: HabitDetailUiState, viewModel: HabitDetailViewModel, themeColor: Color) {
    val textColor = if (ui.habit?.background != null) Color.White else MaterialTheme.colorScheme.onBackground

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Stopwatch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            FocusVFX(isRunning = ui.stopwatchRunning, themeColor = themeColor)

            CircularProgressIndicator(
                progress = { 1f },
                strokeWidth = 24.dp,
                color = themeColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (ui.habit?.background != null) 0.5f else 1f
                ),
                modifier = Modifier.size(280.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatSleekTimer(ui.stopwatchElapsed),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                    fontWeight = FontWeight.Light,
                    color = textColor
                )
                CoinMultiplierChip(multiplier = ui.coinMultiplier, visible = ui.stopwatchActive)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { viewModel.toggleStopwatch() },
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ui.stopwatchRunning) MaterialTheme.colorScheme.error else themeColor
                ),
                modifier = Modifier.fillMaxWidth(0.7f).height(64.dp)
            ) {
                Text(
                    text = when {
                        ui.stopwatchRunning -> "PAUSE"
                        ui.stopwatchActive -> "RESUME"
                        else -> "START"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (ui.stopwatchActive && !ui.stopwatchRunning && ui.stopwatchElapsed > 0) {
                OutlinedButton(
                    onClick = { viewModel.stopAndSave() },
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)
                ) {
                    Text("SAVE & RESET", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }
        }
    }
}

/**
 * Prikazuje trenutni mnozilac zarade. Bez ovoga bi eskalacija bila nevidljiva -
 * korisnik bi dobijao vise coin-ova, ali ne bi imao nacin da vidi zasto.
 * Ispod 1.05x se ne crta, da ne bi stajalo "1.0x" prvih pet minuta sesije.
 */
@Composable
private fun CoinMultiplierChip(multiplier: Float, visible: Boolean) {
    if (!visible || multiplier < 1.05f) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(
                color = Color(0xFFFFD700).copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFFFFD700),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = String.format(Locale.US, "%.2fx", multiplier),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700)
        )
    }
}