package com.example.tenthousand.ui.screens.shop

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
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

// ============================================================
// AGSL shader (API 33+). Jedan shader program, mode-uniform bira
// koji se od 10 pattern-a crta. Sve u float/float2/float3/float4 -
// namerno bez mešanja sa "half" tipovima, da izbegnemo rizik od
// type-mismatch grešaka pri kompajliranju shadera na uređaju.
// Bez petlji po pikselu (nema fBM/octave noise-a) - svaki pattern
// je jeftin analitički izraz, da GPU ne radi više nego što mora.
// ============================================================
private const val MAGIC_SHADER_SRC = """
uniform float2 resolution;
uniform float time;
uniform int mode;
uniform float3 colorA;
uniform float3 colorB;
uniform float3 colorC;

float hash21(float2 p) {
    p = fract(p * float2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float4 nebula(float2 uv, float t) {
    float2 p = (uv - 0.5) * 2.0;
    float2 c1 = float2(sin(t * 0.15) * 0.4, cos(t * 0.12) * 0.3);
    float2 c2 = float2(cos(t * 0.1) * 0.35, sin(t * 0.18) * 0.4);
    float d1 = length(p - c1);
    float d2 = length(p - c2);
    float glow = clamp(0.22 / (d1 + 0.15) + 0.18 / (d2 + 0.2), 0.0, 1.4);
    float3 col = mix(colorC, mix(colorA, colorB, clamp(d2, 0.0, 1.0)), clamp(glow, 0.0, 1.0));
    col *= 0.3 + glow * 0.7;
    return float4(col, 1.0);
}

float4 aurora(float2 uv, float t) {
    float wave = sin(uv.x * 6.0 + t * 0.6) * 0.08 + sin(uv.x * 11.0 - t * 0.4) * 0.04;
    float band = smoothstep(0.18, 0.0, abs(uv.y - 0.5 - wave));
    float3 base = mix(colorC, colorA, uv.x * 0.5 + 0.5 * sin(t * 0.2));
    float3 col = mix(base, colorB, band);
    col *= 0.35 + band * 0.85;
    return float4(col, 1.0);
}

float4 cyberGrid(float2 uv, float t) {
    float2 p = uv * float2(resolution.x / max(resolution.y, 1.0), 1.0) * 14.0;
    p.y += t * 0.5;
    float2 g = abs(fract(p) - 0.5);
    float line = smoothstep(0.46, 0.5, max(g.x, g.y));
    float pulse = 0.5 + 0.5 * sin(t * 0.8 - uv.y * 4.0);
    float3 col = mix(colorC, colorA, line);
    col = mix(col, colorB, line * pulse * 0.6);
    return float4(col, 1.0);
}

float4 quantumPulse(float2 uv, float t) {
    float2 p = uv - 0.5;
    float r = length(p) * 2.0;
    float pulse = 0.5 + 0.5 * sin(t * 1.2);
    float ring = smoothstep(0.9, 0.0, abs(r - pulse * 0.8) * 3.0);
    float3 col = mix(colorC, colorA, clamp(1.0 - r, 0.0, 1.0));
    col = mix(col, colorB, ring);
    return float4(col, 1.0);
}

float4 starfall(float2 uv, float t) {
    float2 p = uv * float2(resolution.x / max(resolution.y, 1.0), 1.0) * 18.0;
    p.y += t * 1.5;
    float2 cell = floor(p);
    float2 f = fract(p) - 0.5;
    float star = hash21(cell);
    float sz = 0.05 + star * 0.15;
    float d = length(f);
    float glow = smoothstep(sz, 0.0, d) * step(0.55, star);
    float3 col = mix(colorC, colorA, uv.y);
    col = mix(col, colorB, glow);
    return float4(col, 1.0);
}

float4 crystalPrism(float2 uv, float t) {
    float2 p = uv - 0.5;
    float angle = atan(p.y, p.x) + t * 0.15;
    float ring = fract(angle / 6.28318 * 6.0);
    float3 col = mix(colorA, colorB, ring);
    col = mix(col, colorC, 0.4 + 0.4 * sin(length(p) * 8.0 - t * 0.5));
    return float4(col, 1.0);
}

float4 solarFlare(float2 uv, float t) {
    float2 p = uv - 0.5;
    float angle = atan(p.y, p.x);
    float r = length(p) * 2.0;
    float rays = 0.5 + 0.5 * cos(angle * 10.0 + sin(t * 0.3) * 2.0);
    float core = clamp(1.0 - r, 0.0, 1.0);
    float glow = rays * core * core;
    float3 col = mix(colorC, colorA, clamp(1.0 - r * 0.8, 0.0, 1.0));
    col = mix(col, colorB, glow);
    return float4(col, 1.0);
}

float4 inkDrift(float2 uv, float t) {
    float w1 = sin(uv.x * 3.0 + uv.y * 2.0 + t * 0.25);
    float w2 = sin(uv.x * 2.0 - uv.y * 3.5 - t * 0.18);
    float blend = 0.5 + 0.5 * sin((w1 + w2) * 1.5 + t * 0.1);
    float3 col = mix(colorA, colorB, blend);
    col = mix(col, colorC, 0.5 + 0.5 * sin(uv.y * 4.0 - t * 0.2));
    return float4(col, 1.0);
}

float4 voidRipple(float2 uv, float t) {
    float2 p = uv - 0.5;
    float r = length(p);
    float wave = sin(r * 22.0 - t * 1.4);
    float ring = smoothstep(0.15, 1.0, 0.5 + 0.5 * wave) * (1.0 - clamp(r * 1.4, 0.0, 1.0));
    float3 col = mix(colorC, colorA, clamp(r * 1.4, 0.0, 1.0));
    col = mix(col, colorB, ring);
    return float4(col, 1.0);
}

float4 emberDrift(float2 uv, float t) {
    float2 p = uv * float2(resolution.x / max(resolution.y, 1.0), 1.0) * 16.0;
    p.y -= t * 1.0;
    float2 cell = floor(p);
    float2 f = fract(p) - 0.5;
    float ember = hash21(cell);
    float flicker = 0.6 + 0.4 * sin(t * 3.0 + ember * 20.0);
    float sz = (0.04 + ember * 0.1) * flicker;
    float d = length(f);
    float glow = smoothstep(sz, 0.0, d) * step(0.5, ember);
    float3 col = mix(colorC, colorA, uv.y);
    col = mix(col, colorB, glow);
    return float4(col, 1.0);
}

float4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float t = time;
    if (mode == 0) return nebula(uv, t);
    if (mode == 1) return aurora(uv, t);
    if (mode == 2) return cyberGrid(uv, t);
    if (mode == 3) return quantumPulse(uv, t);
    if (mode == 4) return starfall(uv, t);
    if (mode == 5) return crystalPrism(uv, t);
    if (mode == 6) return solarFlare(uv, t);
    if (mode == 7) return inkDrift(uv, t);
    if (mode == 8) return voidRipple(uv, t);
    return emberDrift(uv, t);
}
"""

private data class BgSpec(val mode: Int, val a: Color, val b: Color, val c: Color)

// Mora da prati imena iz GachaManager.availableBackgrounds.
// Stara imena (npr. "Cyber Rain") koja eventualno ostanu sačuvana
// u bazi iz starih podataka namerno padaju u `else -> null` ispod
// i crtaju se kao obična crna pozadina (bez crash-a).
private fun specFor(name: String): BgSpec? = when (name) {
    "Galactic Nebula" -> BgSpec(0, Color(0xFF7C4DFF), Color(0xFF00E5FF), Color(0xFF0A0015))
    "Aurora Flow" -> BgSpec(1, Color(0xFF00FFC6), Color(0xFFFF2D95), Color(0xFF05060F))
    "Cyber Grid" -> BgSpec(2, Color(0xFF00FFAA), Color(0xFF00FFFF), Color(0xFF001414))
    "Quantum Pulse" -> BgSpec(3, Color(0xFFFF0055), Color(0xFFFFFFFF), Color(0xFF0A0010))
    "Starfall" -> BgSpec(4, Color(0xFFFFE082), Color(0xFF82B1FF), Color(0xFF02020A))
    "Crystal Prism" -> BgSpec(5, Color(0xFFE0FFFF), Color(0xFF66CCFF), Color(0xFFFFFFFF))
    "Solar Flare" -> BgSpec(6, Color(0xFFFFD54F), Color(0xFFFF6D00), Color(0xFF1A0800))
    "Ink Drift" -> BgSpec(7, Color(0xFF4A148C), Color(0xFF00838F), Color(0xFF120024))
    "Void Ripple" -> BgSpec(8, Color(0xFF1A237E), Color(0xFF64FFDA), Color(0xFF00050F))
    "Ember Drift" -> BgSpec(9, Color(0xFFFFAB40), Color(0xFFFF3D00), Color(0xFF0A0400))
    else -> null
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
    shader.setFloatUniform("colorA", spec.a.red, spec.a.green, spec.a.blue)
    shader.setFloatUniform("colorB", spec.b.red, spec.b.green, spec.b.blue)
    shader.setFloatUniform("colorC", spec.c.red, spec.c.green, spec.c.blue)

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Ovo je jedino što se menja po frejmu - dve jeftine native
        // pozive, bez alokacije novih objekata.
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", timeSeconds)
        drawRect(brush)
    }
}