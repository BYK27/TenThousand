package com.example.tenthousand.ui.screens.shop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun MagicalBackground(name: String) {
    when (name) {
        "Galactic Nebula" -> GalacticNebula()
        "Cyber Rain" -> CyberRain()
        "Mystic Aura" -> MysticAura()
        else -> GalacticNebula()
    }
}

@Composable
fun GalacticNebula() {
    data class Star(var x: Float, var y: Float, var z: Float, val size: Float)
    val stars = remember { List(150) {
        Star(Random.nextFloat() * 2000f - 1000f, Random.nextFloat() * 2000f - 1000f, Random.nextFloat() * 1000f + 1f, Random.nextFloat() * 4f + 1f)
    }}

    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while(true) {
            withFrameNanos { t ->
                val dt = (t - last) / 1E9f
                last = t
                stars.forEach { s ->
                    s.z -= 150f * dt
                    if (s.z <= 0) s.z = 1000f
                }
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        drawRect(Brush.radialGradient(listOf(Color(0xFF2A004D), Color.Black), center = Offset(cx, cy), radius = size.width))

        stars.forEach { s ->
            val px = (s.x / s.z) * 500f + cx
            val py = (s.y / s.z) * 500f + cy
            val alpha = (1f - (s.z / 1000f)).coerceIn(0.1f, 1f)
            if (px in 0f..size.width && py in 0f..size.height) {
                drawCircle(Color.White.copy(alpha = alpha), radius = s.size * alpha, center = Offset(px, py))
            }
        }
    }
}

@Composable
fun CyberRain() {
    data class Drop(var x: Float, var y: Float, var speed: Float, val length: Float)
    val drops = remember { List(100) { Drop(0f, Random.nextFloat() * 2000f, Random.nextFloat() * 800f + 400f, Random.nextFloat() * 100f + 50f) } }

    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while(true) {
            withFrameNanos { t ->
                val dt = (t - last) / 1E9f
                last = t
                drops.forEach { it.y += it.speed * dt }
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color(0xFF050510))
        if (drops.first().x == 0f) {
            drops.forEach { it.x = Random.nextFloat() * size.width } // Init X once size is known
        }
        drops.forEach { d ->
            if (d.y > size.height + d.length) d.y = -d.length
            drawLine(
                color = Color(0xFF00FFCC).copy(alpha = 0.6f),
                start = Offset(d.x, d.y),
                end = Offset(d.x, d.y + d.length),
                strokeWidth = 4f
            )
        }
    }
}

@Composable
fun MysticAura() {
    data class Orb(var angle: Float, var dist: Float, val speed: Float, val size: Float, val color: Color)
    val orbs = remember { List(30) {
        Orb(Random.nextFloat() * 6f, Random.nextFloat() * 400f, Random.nextFloat() * 1f + 0.5f, Random.nextFloat() * 40f + 20f, listOf(Color(0xFFFF007F), Color(0xFF7F00FF), Color(0xFF00F0FF)).random())
    }}

    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while(true) {
            withFrameNanos { t ->
                val dt = (t - last) / 1E9f
                last = t
                orbs.forEach { it.angle += it.speed * dt }
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color(0xFF0D001A))
        val cx = size.width / 2
        val cy = size.height / 2
        orbs.forEach { o ->
            val px = cx + cos(o.angle) * o.dist
            val py = cy + sin(o.angle) * o.dist
            drawCircle(
                brush = Brush.radialGradient(listOf(o.color.copy(alpha = 0.8f), Color.Transparent), center = Offset(px, py), radius = o.size),
                radius = o.size,
                center = Offset(px, py)
            )
        }
    }
}