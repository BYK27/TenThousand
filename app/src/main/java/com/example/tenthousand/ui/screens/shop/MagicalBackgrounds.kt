package com.example.tenthousand.ui.screens.shop

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.example.tenthousand.ui.background.MAGIC_SHADER_SRC
import com.example.tenthousand.ui.background.rgbFloats
import com.example.tenthousand.ui.background.specFor

/**
 * Shader izvor i mapa pozadina su preseljeni u
 * com.example.tenthousand.ui.background.MagicalBackgroundShader, jer ih sada
 * koristi i ShaderSnapshotRenderer (offscreen render za notifikaciju).
 * Ovde je ostao samo Compose omotač.
 *
 * @RequiresApi(TIRAMISU) je uklonjen jer je minSdk podignut na 33 - anotacija
 * je ionako bila samo lint-marker i nije sprečavala crash na starijim uređajima.
 */
@Composable
fun MagicalBackground(name: String) {
    val shader = remember { RuntimeShader(MAGIC_SHADER_SRC) }
    val brush = remember(shader) { ShaderBrush(shader) }

    var timeSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            timeSeconds = (frameNanos - startNanos) / 1_000_000_000f
        }
    }

    val spec = specFor(name)
    if (spec == null) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawRect(Color.Black) }
        return
    }

    // Menja se samo kad se `name` promeni (retko), ne po frejmu.
    shader.setIntUniform("mode", spec.mode)
    shader.setFloatUniform("colorA", spec.a.rgbFloats())
    shader.setFloatUniform("colorB", spec.b.rgbFloats())
    shader.setFloatUniform("colorC", spec.c.rgbFloats())

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Ovo je jedino što se menja po frejmu - dve jeftine native
        // pozive, bez alokacije novih objekata.
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", timeSeconds)
        drawRect(brush)
    }
}