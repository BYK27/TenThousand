package com.example.tenthousand.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import com.example.tenthousand.ui.background.BgSpec
import com.example.tenthousand.ui.background.MAGIC_SHADER_SRC
import com.example.tenthousand.ui.background.rgbFloats
import com.example.tenthousand.ui.background.specFor
import java.io.File
import java.io.FileOutputStream

/**
 * Pravi statičan snapshot AGSL pozadine kao Bitmap, za prikaz u notifikaciji.
 *
 * Zašto ovoliko posla za jednu sliku: RuntimeShader NE radi na softverskom
 * Canvas-u. `Bitmap.createBitmap(...)` pa `Canvas(bitmap)` daje softverski
 * canvas i shader se na njemu jednostavno ne iscrta. Jedini način da se AGSL
 * rasterizuje van ekrana je da se snimi u RenderNode i pusti kroz
 * HardwareRenderer koji crta u Surface jednog ImageReader-a; iz njega se čita
 * HardwareBuffer, omota u hardware Bitmap i kopira u ARGB_8888.
 *
 * Kopija u ARGB_8888 nije opcionalna: RemoteViews ne ume da serijalizuje
 * Bitmap.Config.HARDWARE, pa bi notifikacija ostala bez slike.
 *
 * Render se radi jednom po imenu pozadine i keširа kao PNG u cacheDir. Pozadine
 * su animirane u app-u, ali u notifikaciji je uvek isti frejm (t = SNAPSHOT_TIME),
 * pa je keš uvek validan i renderovanje se ne ponavlja pri svakom update-u.
 */
object ShaderSnapshotRenderer {

    /**
     * RemoteViews ima ograničenje veličine payload-a. 400x200 ARGB_8888 je oko
     * 320 KB i prolazi bez problema; ako ikad vidiš "Couldn't expand RemoteViews"
     * u logcat-u, smanji ove dve konstante.
     */
    private const val WIDTH = 400
    private const val HEIGHT = 200

    /** Fiksni trenutak u animaciji koji se snima - drži keš deterministički. */
    private const val SNAPSHOT_TIME_SECONDS = 6.5f

    private const val CACHE_DIR = "bg_snapshots"

    /**
     * Vraća bitmapu za datu pozadinu, ili null ako ime nije poznato ili render
     * ne uspe. Pozivalac tretira null kao "nema slike" i pada na boju navike.
     *
     * Poziva se sa pozadinske niti (blokira dok GPU ne završi frejm).
     */
    fun snapshot(context: Context, backgroundName: String): Bitmap? {
        val cacheFile = cacheFileFor(context, backgroundName)
        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return it }
        }

        val spec = specFor(backgroundName) ?: return null
        val bitmap = render(spec) ?: return null

        runCatching {
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return bitmap
    }

    private fun cacheFileFor(context: Context, backgroundName: String): File {
        val safeName = backgroundName.replace(Regex("[^A-Za-z0-9]"), "_")
        return File(File(context.cacheDir, CACHE_DIR), "${safeName}_${WIDTH}x$HEIGHT.png")
    }

    private fun render(spec: BgSpec): Bitmap? = runCatching {
        val shader = RuntimeShader(MAGIC_SHADER_SRC).apply {
            setFloatUniform("resolution", WIDTH.toFloat(), HEIGHT.toFloat())
            setFloatUniform("time", SNAPSHOT_TIME_SECONDS)
            setIntUniform("mode", spec.mode)
            setFloatUniform("colorA", spec.a.rgbFloats())
            setFloatUniform("colorB", spec.b.rgbFloats())
            setFloatUniform("colorC", spec.c.rgbFloats())
        }

        val imageReader = ImageReader.newInstance(
            WIDTH,
            HEIGHT,
            PixelFormat.RGBA_8888,
            1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val renderer = HardwareRenderer()
        val node = RenderNode("focus_bg_snapshot")

        try {
            renderer.setSurface(imageReader.surface)
            node.setPosition(0, 0, WIDTH, HEIGHT)

            val canvas = node.beginRecording(WIDTH, HEIGHT)
            canvas.drawRect(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                Paint().apply { this.shader = shader }
            )
            node.endRecording()

            renderer.setContentRoot(node)
            // setWaitForPresent(true) je bitno: bez toga acquireNextImage()
            // može da vrati null jer GPU još nije predao frejm.
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

            val image = imageReader.acquireNextImage() ?: return@runCatching null
            image.use { img ->
                val buffer = img.hardwareBuffer ?: return@runCatching null
                buffer.use { hb ->
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                        hb,
                        ColorSpace.get(ColorSpace.Named.SRGB)
                    ) ?: return@runCatching null
                    // false = immutable kopija; RemoteViews ne treba mutable bitmapu.
                    hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } finally {
            node.discardDisplayList()
            renderer.destroy()
            imageReader.close()
        }
    }.getOrNull()
}