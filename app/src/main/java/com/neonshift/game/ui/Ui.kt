package com.neonshift.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Colour palette: deep indigo night with neon accents. */
object Neon {
    val BG = 0xFF0A0A2A.toInt()
    val LIGHT = 0xFF22E4FF.toInt()
    val SHADOW = 0xFFB15CFF.toInt()
    val ENERGY = 0xFFFFC93C.toInt()
    val TEXT = 0xFFF5F7FF.toInt()
    val PANEL = 0xFF1C1652.toInt()
    val EMPTY = 0xFF1D2458.toInt()
    val BLOCKED = 0xFFFF4D6D.toInt()
    val SUCCESS = 0xFF3FE07A.toInt()
    val PINK = 0xFFFF4FD8.toInt()
    val BLUE = 0xFF3D7BFF.toInt()
    val ORANGE = 0xFFFF9A1F.toInt()
    val RED = 0xFFE0263F.toInt()
    val WORLD_ORANGE = 0xFFFF8A3D.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val BLACK = 0xFF000000.toInt()

    fun a(c: Int, alpha: Float): Int = (c and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24)
    fun mul(c: Int, alpha: Float): Int = a(c, Color.alpha(c) / 255f * alpha)
    fun lerp(x: Int, y: Int, t: Float): Int {
        fun ch(s: Int) = ((x shr s and 0xFF) + ((y shr s and 0xFF) - (x shr s and 0xFF)) * t).toInt().coerceIn(0, 255)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    fun lighten(c: Int, t: Float) = lerp(c, a(WHITE, Color.alpha(c) / 255f), t)
    fun darken(c: Int, t: Float) = lerp(c, a(BLACK, Color.alpha(c) / 255f), t)
    fun wall(accent: Int) = lerp(PANEL, accent, 0.55f)
}

enum class Style { OUTLINE, FILLED, ROUND }
enum class Align { LEFT, CENTER, RIGHT }

/**
 * Immediate-mode neon drawing in "world units": a 9.5 x 17.2 portrait area centred on (0, 0) with y pointing up.
 * Widgets register hit areas every frame. Shapes use soft gradients, glows and drop shadows for depth.
 */
class Ui(private val mainFont: Typeface, val logoFont: Typeface) {
    lateinit var canvas: Canvas
    var ppu = 1f; private set
    private var cx = 0f
    private var cy = 0f
    private var hPx = 1f
    var time = 0f
    /** Half of the visible height in world units (8.6 on a 16:9 phone, more on taller phones). */
    var halfH = 8.6f; private set
    var halfW = 4.75f; private set
    private var shakeLeft = 0f
    private var shakeAmp = 0f
    private var shakeX = 0f
    private var shakeY = 0f
    val particles = Particles()

    class Hit(val l: Float, val b: Float, val r: Float, val t: Float, val id: String, val onTap: (() -> Unit)?, val onDrag: ((Float) -> Unit)?) {
        fun contains(x: Float, y: Float) = x in l..r && y in b..t
    }

    val hits = ArrayList<Hit>()
    var pressedId: String? = null
    var pressedAt = -10f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shaded = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val matrix = Matrix()
    private val glowShaders = HashMap<Int, Shader>()
    private val linShaders = HashMap<Long, Shader>()
    private val sphereShaders = HashMap<Int, Shader>()
    private var bgShader: Shader? = null

    fun begin(c: Canvas, w: Int, h: Int, t: Float) {
        canvas = c
        time = t
        ppu = min(w / 9.5f, h / 17.2f)
        cx = w / 2f
        cy = h / 2f
        if (h.toFloat() != hPx) bgShader = null
        hPx = h.toFloat()
        halfH = h / ppu / 2f
        halfW = w / ppu / 2f
        hits.clear()
        c.drawColor(Neon.BG)
    }

    /** Called once per frame before drawing, with the frame time. */
    fun tick(dt: Float) {
        if (shakeLeft > 0f) {
            shakeLeft -= dt
            val a = shakeAmp * (shakeLeft.coerceAtLeast(0f) / 0.25f)
            shakeX = (Math.random().toFloat() - 0.5f) * 2f * a
            shakeY = (Math.random().toFloat() - 0.5f) * 2f * a
        } else { shakeX = 0f; shakeY = 0f }
        particles.update(dt)
    }

    fun shake(amount: Float = 0.12f) { shakeLeft = 0.25f; shakeAmp = amount }

    fun sx(x: Float) = cx + (x + shakeX) * ppu
    fun sy(y: Float) = cy - (y + shakeY) * ppu
    fun toWorldX(px: Float) = (px - cx) / ppu
    fun toWorldY(py: Float) = (cy - py) / ppu

    /** Modal panels call this so that only their own buttons can be tapped. */
    fun blockBelow() = hits.clear()

    fun dim(alpha: Float = 0.8f) {
        fill.color = Neon.a(0xFF05041A.toInt(), alpha)
        canvas.drawPaint(fill)
        blockBelow()
    }

    // ---------------- Shaders ----------------

    private fun vertical(top: Int, bottom: Int, yTopPx: Float, hPx: Float): Shader {
        val key = (top.toLong() shl 32) or (bottom.toLong() and 0xFFFFFFFFL)
        if (linShaders.size > 500) linShaders.clear()
        val s = linShaders.getOrPut(key) { LinearGradient(0f, 0f, 0f, 1f, top, bottom, Shader.TileMode.CLAMP) }
        matrix.setScale(1f, hPx.coerceAtLeast(1f))
        matrix.postTranslate(0f, yTopPx)
        s.setLocalMatrix(matrix)
        return s
    }

    // ---------------- Shapes ----------------

    /** Soft radial glow filling a w x h ellipse. */
    fun glow(x: Float, y: Float, w: Float, h: Float, color: Int) {
        if (Color.alpha(color) == 0) return
        val shader = glowShaders.getOrPut(color) {
            RadialGradient(0f, 0f, 1f, intArrayOf(color, Neon.mul(color, 0.45f), Neon.mul(color, 0.12f), 0), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        }
        if (glowShaders.size > 400) glowShaders.clear()
        glowPaint.shader = shader
        canvas.save()
        canvas.translate(sx(x), sy(y))
        canvas.scale(w * 0.5f * ppu, h * 0.5f * ppu)
        canvas.drawCircle(0f, 0f, 1f, glowPaint)
        canvas.restore()
    }

    fun rrect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = defaultRadius(w, h)) {
        fill.color = color
        rect.set(sx(x - w / 2), sy(y + h / 2), sx(x + w / 2), sy(y - h / 2))
        canvas.drawRoundRect(rect, radius * ppu, radius * ppu, fill)
    }

    /** Rounded rectangle with a top-to-bottom gradient. */
    fun grad(x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int, radius: Float = defaultRadius(w, h)) {
        rect.set(sx(x - w / 2), sy(y + h / 2), sx(x + w / 2), sy(y - h / 2))
        shaded.shader = vertical(top, bottom, rect.top, rect.height())
        canvas.drawRoundRect(rect, radius * ppu, radius * ppu, shaded)
    }

    /** Soft drop shadow under a rounded shape. */
    fun dropShadow(x: Float, y: Float, w: Float, h: Float, radius: Float, strength: Float = 1f) {
        rrect(x, y - 0.12f, w + 0.1f, h + 0.06f, Neon.a(Neon.BLACK, 0.22f * strength), radius + 0.05f)
        rrect(x, y - 0.07f, w, h, Neon.a(Neon.BLACK, 0.3f * strength), radius)
    }

    fun rrectStroke(x: Float, y: Float, w: Float, h: Float, color: Int, width: Float, radius: Float = defaultRadius(w, h)) {
        stroke.color = color
        stroke.strokeWidth = width * ppu
        rect.set(sx(x - w / 2), sy(y + h / 2), sx(x + w / 2), sy(y - h / 2))
        canvas.drawRoundRect(rect, radius * ppu, radius * ppu, stroke)
    }

    fun defaultRadius(w: Float, h: Float) = min(0.4f, min(w, h) * 0.32f)

    fun box(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fill.color = color
        canvas.drawRect(sx(x - w / 2), sy(y + h / 2), sx(x + w / 2), sy(y - h / 2), fill)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = width * ppu
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(sx(x1), sy(y1), sx(x2), sy(y2), stroke)
        stroke.strokeCap = Paint.Cap.BUTT
    }

    fun circle(x: Float, y: Float, d: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(sx(x), sy(y), d * 0.5f * ppu, fill)
    }

    fun gradCircle(x: Float, y: Float, d: Float, top: Int, bottom: Int) {
        val r = d * 0.5f * ppu
        shaded.shader = vertical(top, bottom, sy(y) - r, 2 * r)
        canvas.drawCircle(sx(x), sy(y), r, shaded)
    }

    /** Shiny ball: light from the top left. */
    fun sphere(x: Float, y: Float, w: Float, h: Float, color: Int) {
        val s = sphereShaders.getOrPut(color) {
            RadialGradient(-0.35f, -0.4f, 1.45f, intArrayOf(Neon.lighten(color, 0.6f), color, Neon.darken(color, 0.38f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        if (sphereShaders.size > 200) sphereShaders.clear()
        matrix.setScale(w * 0.5f * ppu, h * 0.5f * ppu)
        matrix.postTranslate(sx(x), sy(y))
        s.setLocalMatrix(matrix)
        shaded.shader = s
        rect.set(sx(x - w / 2), sy(y + h / 2), sx(x + w / 2), sy(y - h / 2))
        canvas.drawOval(rect, shaded)
    }

    fun ellipse(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fill.color = color
        rect.set(sx(x - w / 2), sy(y + h / 2), sx(x + w / 2), sy(y - h / 2))
        canvas.drawOval(rect, fill)
    }

    fun ring(x: Float, y: Float, w: Float, h: Float, color: Int, width: Float = 0.09f) {
        stroke.color = color
        stroke.strokeWidth = width * ppu
        rect.set(sx(x - w / 2 + width / 2), sy(y + h / 2 - width / 2), sx(x + w / 2 - width / 2), sy(y - h / 2 + width / 2))
        canvas.drawOval(rect, stroke)
    }

    private fun starPath(x: Float, y: Float, size: Float) {
        path.reset()
        val outer = size * 0.5f
        val inner = outer * 0.48f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val ang = Math.PI / 2 + i * Math.PI / 5
            val px = sx(x + (r * cos(ang)).toFloat())
            val py = sy(y + (r * sin(ang)).toFloat())
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    fun star(x: Float, y: Float, size: Float, color: Int) {
        starPath(x, y, size)
        if (Color.alpha(color) > 200) {
            shaded.shader = vertical(Neon.lighten(color, 0.35f), Neon.darken(color, 0.2f), sy(y + size / 2), size * ppu)
            canvas.drawPath(path, shaded)
        } else {
            fill.color = color
            canvas.drawPath(path, fill)
        }
    }

    fun stars(x: Float, y: Float, size: Float, filled: Int, on: Int = Neon.ENERGY, off: Int = Neon.a(Neon.TEXT, 0.2f)) {
        for (i in 0 until 3) star(x + (i - 1) * size * 1.05f, y, size, if (i < filled) on else off)
    }

    /** Arrow pointing along angle (degrees, 0 = right, 90 = up). */
    fun arrow(x: Float, y: Float, size: Float, angleDeg: Float, color: Int) {
        val pts = floatArrayOf(0.5f, 0f, 0.0f, 0.42f, 0.0f, 0.17f, -0.45f, 0.17f, -0.45f, -0.17f, 0.0f, -0.17f, 0.0f, -0.42f)
        val a = Math.toRadians(angleDeg.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        path.reset()
        for (i in pts.indices step 2) {
            val px = pts[i] * size
            val py = pts[i + 1] * size
            val wx = x + px * c - py * s
            val wy = y + px * s + py * c
            if (i == 0) path.moveTo(sx(wx), sy(wy)) else path.lineTo(sx(wx), sy(wy))
        }
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
    }

    /** Filled polygon from world-space x, y pairs. */
    fun poly(pts: FloatArray, color: Int) {
        path.reset()
        for (i in pts.indices step 2) {
            if (i == 0) path.moveTo(sx(pts[i]), sy(pts[i + 1])) else path.lineTo(sx(pts[i]), sy(pts[i + 1]))
        }
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
    }

    // ---------------- Icons ----------------

    /** Small line icons drawn from shapes, so they stay crisp at any size. */
    fun icon(name: String, x: Float, y: Float, s: Float, color: Int) {
        val w = s * 0.13f
        when (name) {
            "back" -> { line(x + s * 0.18f, y + s * 0.3f, x - s * 0.15f, y, w, color); line(x - s * 0.15f, y, x + s * 0.18f, y - s * 0.3f, w, color) }
            "pause" -> { rrect(x - s * 0.15f, y, s * 0.15f, s * 0.6f, color, s * 0.05f); rrect(x + s * 0.15f, y, s * 0.15f, s * 0.6f, color, s * 0.05f) }
            "close" -> { line(x - s * 0.25f, y - s * 0.25f, x + s * 0.25f, y + s * 0.25f, w, color); line(x - s * 0.25f, y + s * 0.25f, x + s * 0.25f, y - s * 0.25f, w, color) }
            "play" -> poly(floatArrayOf(x - s * 0.22f, y + s * 0.32f, x + s * 0.32f, y, x - s * 0.22f, y - s * 0.32f), color)
            "skip" -> { poly(floatArrayOf(x - s * 0.32f, y + s * 0.26f, x, y, x - s * 0.32f, y - s * 0.26f), color); poly(floatArrayOf(x, y + s * 0.26f, x + s * 0.32f, y, x, y - s * 0.26f), color) }
            "undo", "restart" -> {
                val mx = if (name == "undo") 1f else -1f
                val r = s * 0.27f
                var px = 0f; var py = 0f
                for (deg in 60..330 step 15) {
                    val t = Math.toRadians(deg.toDouble())
                    val qx = x + mx * r * cos(t).toFloat(); val qy = y + r * sin(t).toFloat()
                    if (deg > 60) line(px, py, qx, qy, w, color)
                    px = qx; py = qy
                }
                val t = Math.toRadians(330.0)
                val dx = -sin(t).toFloat() * mx; val dy = cos(t).toFloat()
                val nx = cos(t).toFloat() * mx; val ny = sin(t).toFloat()
                poly(floatArrayOf(px + dx * s * 0.18f, py + dy * s * 0.18f, px + nx * s * 0.14f, py + ny * s * 0.14f, px - nx * s * 0.14f, py - ny * s * 0.14f), color)
            }
            "hint" -> {
                circle(x, y + s * 0.06f, s * 0.48f, color)
                rrect(x, y - s * 0.28f, s * 0.24f, s * 0.12f, color, s * 0.04f)
                circle(x - s * 0.07f, y + s * 0.13f, s * 0.12f, Neon.a(Neon.WHITE, 0.6f))
            }
            "gear" -> {
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    circle(x + (cos(a) * s * 0.3f).toFloat(), y + (sin(a) * s * 0.3f).toFloat(), s * 0.2f, color)
                }
                circle(x, y, s * 0.58f, color)
                circle(x, y, s * 0.24f, Neon.PANEL)
            }
            "trophy" -> {
                rrect(x, y + s * 0.12f, s * 0.46f, s * 0.4f, color, s * 0.16f)
                ring(x - s * 0.26f, y + s * 0.16f, s * 0.22f, s * 0.22f, color, s * 0.07f)
                ring(x + s * 0.26f, y + s * 0.16f, s * 0.22f, s * 0.22f, color, s * 0.07f)
                box(x, y - s * 0.14f, s * 0.08f, s * 0.16f, color)
                rrect(x, y - s * 0.27f, s * 0.36f, s * 0.1f, color, s * 0.03f)
            }
            "calendar" -> {
                rrect(x, y - s * 0.03f, s * 0.6f, s * 0.54f, color, s * 0.08f)
                box(x, y + s * 0.12f, s * 0.6f, s * 0.04f, Neon.PANEL)
                box(x - s * 0.15f, y + s * 0.27f, s * 0.07f, s * 0.14f, color); box(x + s * 0.15f, y + s * 0.27f, s * 0.07f, s * 0.14f, color)
                for (i in 0 until 3) box(x + (i - 1) * s * 0.15f, y - s * 0.1f, s * 0.09f, s * 0.09f, Neon.PANEL)
            }
            "clock" -> {
                ring(x, y, s * 0.62f, s * 0.62f, color, w)
                line(x, y, x, y + s * 0.18f, w, color); line(x, y, x + s * 0.13f, y - s * 0.06f, w, color)
            }
            "globe" -> {
                ring(x, y, s * 0.62f, s * 0.62f, color, w * 0.8f)
                ring(x, y, s * 0.28f, s * 0.62f, color, w * 0.7f)
                line(x - s * 0.3f, y, x + s * 0.3f, y, w * 0.7f, color)
            }
            "bag" -> {
                rrect(x, y - s * 0.07f, s * 0.56f, s * 0.46f, color, s * 0.08f)
                ring(x, y + s * 0.17f, s * 0.3f, s * 0.3f, color, w * 0.8f)
            }
            "lock" -> {
                rrect(x, y - s * 0.08f, s * 0.5f, s * 0.38f, color, s * 0.07f)
                ring(x, y + s * 0.13f, s * 0.32f, s * 0.36f, color, w * 0.8f)
            }
            "home" -> {
                poly(floatArrayOf(x - s * 0.34f, y + s * 0.02f, x, y + s * 0.34f, x + s * 0.34f, y + s * 0.02f), color)
                rrect(x, y - s * 0.14f, s * 0.46f, s * 0.34f, color, s * 0.04f)
            }
            "grid" -> for (i in 0 until 4) rrect(x + (if (i % 2 == 0) -1 else 1) * s * 0.14f, y + (if (i < 2) 1 else -1) * s * 0.14f, s * 0.22f, s * 0.22f, color, s * 0.05f)
            "plus" -> { line(x - s * 0.22f, y, x + s * 0.22f, y, w, color); line(x, y - s * 0.22f, x, y + s * 0.22f, w, color) }
        }
    }

    private val glyphIcons = mapOf("←" to "back", "II" to "pause", "×" to "close", "↺" to "undo", "↻" to "restart", "?" to "hint", "»" to "skip")

    // ---------------- Text ----------------

    /** height is roughly the line height in world units. Returns the text width in world units. */
    fun text(s: String, x: Float, y: Float, height: Float, color: Int, align: Align = Align.CENTER, logo: Boolean = false): Float {
        if (s.isEmpty()) return 0f
        textPaint.typeface = if (logo) logoFont else mainFont
        textPaint.textSize = height * 0.82f * ppu
        textPaint.textAlign = when (align) {
            Align.LEFT -> Paint.Align.LEFT
            Align.CENTER -> Paint.Align.CENTER
            Align.RIGHT -> Paint.Align.RIGHT
        }
        val lines = s.split('\n')
        val fm = textPaint.fontMetrics
        val lineH = textPaint.textSize * 1.25f
        val total = lineH * (lines.size - 1)
        val shadow = height >= 0.4f && Color.alpha(color) > 150
        var widest = 0f
        for (pass in (if (shadow) 0 else 1)..1) {
            var py = sy(y) - total / 2f - (fm.ascent + fm.descent) / 2f
            textPaint.color = if (pass == 0) Neon.a(0xFF06041C.toInt(), 0.55f * Color.alpha(color) / 255f) else color
            val off = if (pass == 0) height * 0.06f * ppu else 0f
            for (line in lines) {
                canvas.drawText(line, sx(x), py + off, textPaint)
                if (pass == 1) widest = maxOf(widest, textPaint.measureText(line))
                py += lineH
            }
        }
        return widest / ppu
    }

    fun textWidth(s: String, height: Float, logo: Boolean = false): Float {
        textPaint.typeface = if (logo) logoFont else mainFont
        textPaint.textSize = height * 0.82f * ppu
        return textPaint.measureText(s) / ppu
    }

    // ---------------- Composite widgets ----------------

    /** Glass panel: drop shadow, soft accent glow, bright rim and a gradient body. */
    fun card(x: Float, y: Float, w: Float, h: Float, accent: Int, fillAlpha: Float = 1f) {
        val r = min(0.5f, min(w, h) * 0.25f)
        dropShadow(x, y, w, h, r)
        glow(x, y, w * 1.18f, h * 1.18f, Neon.a(accent, 0.14f))
        grad(x, y, w, h, Neon.lighten(accent, 0.15f), Neon.darken(accent, 0.25f), r)
        val top = Neon.a(Neon.lerp(Neon.PANEL, accent, 0.22f), fillAlpha)
        val bottom = Neon.a(Neon.darken(Neon.PANEL, 0.35f), fillAlpha)
        grad(x, y, w - 0.1f, h - 0.1f, top, bottom, r - 0.04f)
        rrect(x, y + h / 2 - 0.12f, w - 0.5f, 0.03f, Neon.a(Neon.WHITE, 0.07f), 0.015f)
    }

    /** Calm night sky: indigo gradient, two slow colour glows and a few twinkling stars. */
    fun background(a: Int, b: Int) {
        val bs = bgShader ?: LinearGradient(0f, 0f, 0f, hPx, intArrayOf(0xFF2A1670.toInt(), 0xFF130E45.toInt(), 0xFF080A26.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP).also { bgShader = it }
        shaded.shader = bs
        canvas.drawPaint(shaded)
        val t = time
        glow(-1.8f + sin(t * 0.2f) * 0.6f, halfH * 0.55f, 11f, 9f, Neon.a(a, 0.13f))
        glow(1.8f + cos(t * 0.17f) * 0.6f, -halfH * 0.55f, 11f, 9f, Neon.a(b, 0.13f))
        for (i in 0 until 22) {
            val seed = i * 97.31f
            val px = ((seed * 13.7f) % (2 * halfW)) - halfW
            val py = ((seed * 7.3f) % (2 * halfH)) - halfH
            val tw = 0.25f + 0.25f * sin(t * (0.8f + (i % 4) * 0.3f) + i)
            circle(px, py, 0.05f + (i % 3) * 0.025f, Neon.a(Neon.WHITE, tw))
        }
    }

    /** Gold coin. */
    fun coin(x: Float, y: Float, d: Float) {
        circle(x, y - d * 0.06f, d, Neon.darken(Neon.ENERGY, 0.35f))
        gradCircle(x, y, d, Neon.lighten(Neon.ENERGY, 0.45f), Neon.darken(Neon.ENERGY, 0.12f))
        ring(x, y, d * 0.68f, d * 0.68f, Neon.a(Neon.darken(Neon.ENERGY, 0.35f), 0.7f), d * 0.09f)
    }

    /** Coin counter pill. */
    fun coins(x: Float, y: Float, height: Float, amount: Int) {
        val label = amount.toString()
        val tw = textWidth(label, height)
        val w = tw + height * 2.3f
        val left = x - height * 0.65f
        rrect(left + w / 2, y, w, height * 1.55f, Neon.a(0xFF0A0826.toInt(), 0.7f), height * 0.78f)
        rrectStroke(left + w / 2, y, w, height * 1.55f, Neon.a(Neon.ENERGY, 0.35f), 0.03f, height * 0.78f)
        coin(x, y, height * 1.15f)
        text(label, x + height * 0.8f, y, height, Neon.TEXT, Align.LEFT)
    }

    /** Glowing orb character with eyes. */
    fun character(x: Float, y: Float, color: Int, scale: Float = 1.6f) {
        glow(x, y, 1.7f * scale, 1.7f * scale, Neon.a(color, 0.7f))
        sphere(x, y, 0.9f * scale, 0.9f * scale, color)
        val blink = if ((time + (color and 7)) % 4.1f < 0.12f) 0.25f else 1f
        ellipse(x - 0.13f * scale, y + 0.05f * scale, 0.13f * scale, 0.19f * scale * blink, 0xFF0B0A2A.toInt())
        ellipse(x + 0.13f * scale, y + 0.05f * scale, 0.13f * scale, 0.19f * scale * blink, 0xFF0B0A2A.toInt())
        circle(x - 0.11f * scale, y + 0.1f * scale, 0.045f * scale, Neon.a(Neon.WHITE, 0.9f))
        circle(x + 0.15f * scale, y + 0.1f * scale, 0.045f * scale, Neon.a(Neon.WHITE, 0.9f))
    }

    fun logo(y: Float, height: Float) {
        glow(0f, y - height * 0.45f, 9f, height * 2.6f, Neon.a(Neon.SHADOW, 0.3f))
        gradText("NEON", -0.2f, y, height, Neon.lighten(Neon.LIGHT, 0.5f), Neon.LIGHT)
        gradText("SHIFT", 0.25f, y - height * 0.95f, height, Neon.lighten(Neon.PINK, 0.45f), Neon.PINK)
    }

    private fun gradText(s: String, x: Float, y: Float, height: Float, top: Int, bottom: Int) {
        text(s, x, y - height * 0.03f, height, Neon.a(0xFF06041C.toInt(), 0.6f), logo = true)
        textPaint.typeface = logoFont
        textPaint.textSize = height * 0.82f * ppu
        textPaint.textAlign = Paint.Align.CENTER
        val fm = textPaint.fontMetrics
        val py = sy(y) - (fm.ascent + fm.descent) / 2f
        textPaint.shader = vertical(top, bottom, py + fm.ascent, -fm.ascent)
        canvas.drawText(s, sx(x), py, textPaint)
        textPaint.shader = null
    }

    private fun pressScale(id: String) = if (id == pressedId && time - pressedAt < 0.12f) 0.93f else 1f

    /**
     * Button. FILLED is a glossy candy button, OUTLINE a glass button with a neon rim, ROUND a glass circle.
     * Labels "←", "II", "×", "↺", "↻", "?", "»" are drawn as icons. The click arrives later through the hit.
     */
    fun button(
        label: String, x: Float, y: Float, w0: Float, h0: Float, accent: Int,
        style: Style = Style.OUTLINE, enabled: Boolean = true, caption: String? = null,
        labelSize: Float = 0f, icon: String? = null, onClick: () -> Unit
    ) {
        val id = "b:$x:$y"
        val k = pressScale(id)
        val w = w0 * k
        val h = h0 * k
        val al = if (enabled) 1f else 0.4f
        val glyph = glyphIcons[label]
        val iconName = icon ?: glyph
        val text = if (glyph != null) "" else label
        when (style) {
            Style.ROUND -> {
                circle(x, y - 0.07f, w, Neon.a(Neon.BLACK, 0.3f))
                if (enabled) glow(x, y, w * 1.5f, w * 1.5f, Neon.a(accent, 0.18f))
                gradCircle(x, y, w, Neon.a(Neon.lighten(accent, 0.2f), al), Neon.a(Neon.darken(accent, 0.3f), al))
                gradCircle(x, y, w - 0.1f, Neon.lerp(Neon.PANEL, accent, 0.3f), Neon.darken(Neon.PANEL, 0.3f))
                circle(x, y + w * 0.18f, w * 0.55f, Neon.a(Neon.WHITE, 0.05f))
            }
            Style.FILLED -> {
                val r = min(h * 0.5f, 0.55f)
                dropShadow(x, y, w, h, r, al)
                if (enabled) glow(x, y, w * 1.25f, h * 1.6f, Neon.a(accent, 0.22f))
                rrect(x, y - 0.08f, w, h, Neon.a(Neon.darken(accent, 0.45f), al), r)
                grad(x, y, w, h, Neon.a(Neon.lighten(accent, 0.3f), al), Neon.a(Neon.darken(accent, 0.12f), al), r)
                rrect(x, y + h * 0.2f, w - r * 0.9f, h * 0.32f, Neon.a(Neon.WHITE, 0.2f * al), h * 0.16f)
            }
            Style.OUTLINE -> {
                val r = min(h * 0.5f, 0.5f)
                dropShadow(x, y, w, h, r, al)
                if (enabled) glow(x, y, w * 1.2f, h * 1.6f, Neon.a(accent, 0.14f))
                grad(x, y, w, h, Neon.a(Neon.lighten(accent, 0.2f), al), Neon.a(Neon.darken(accent, 0.2f), al), r)
                grad(x, y, w - 0.09f, h - 0.09f, Neon.lerp(Neon.PANEL, accent, 0.32f), Neon.darken(Neon.lerp(Neon.PANEL, accent, 0.1f), 0.3f), r - 0.04f)
                rrect(x, y + h * 0.22f, w - r * 1.2f, h * 0.22f, Neon.a(Neon.WHITE, 0.05f), h * 0.11f)
            }
        }
        val th = if (labelSize > 0f) labelSize else if (style == Style.ROUND) h * 0.42f else min(h * 0.42f, 0.56f)
        val tc = Neon.a(Neon.TEXT, al)
        if (iconName != null && text.isEmpty()) {
            icon(iconName, x, y, (if (style == Style.ROUND) w * 0.62f else h * 0.62f) * k, tc)
        } else if (iconName != null) {
            val tw = textWidth(text, th * k)
            val iw = h * 0.5f
            val total = iw + 0.25f + tw
            icon(iconName, x - total / 2 + iw / 2, y, iw, tc)
            text(text, x - total / 2 + iw + 0.25f, y, th * k, tc, Align.LEFT)
        } else {
            text(text, x, y, th * k, tc)
        }
        if (caption != null) text(caption, x, y - h0 * 0.5f - 0.32f, 0.3f, Neon.a(Neon.TEXT, 0.75f))
        if (enabled) hits += Hit(x - w0 / 2 - 0.05f, y - h0 / 2 - 0.05f, x + w0 / 2 + 0.05f, y + h0 / 2 + 0.05f, id, onClick, null)
    }

    /** On/off pill switch with a label on the left. */
    fun toggle(label: String, x: Float, y: Float, width: Float, value: Boolean, onChange: (Boolean) -> Unit) {
        text(label, x - width / 2, y, 0.42f, Neon.TEXT, Align.LEFT)
        val tx = x + width / 2 - 0.6f
        if (value) grad(tx, y, 1.2f, 0.64f, Neon.lighten(Neon.SUCCESS, 0.2f), Neon.darken(Neon.SUCCESS, 0.2f), 0.32f)
        else rrect(tx, y, 1.2f, 0.64f, Neon.a(Neon.WHITE, 0.14f), 0.32f)
        val kx = tx + if (value) 0.29f else -0.29f
        circle(kx, y - 0.04f, 0.54f, Neon.a(Neon.BLACK, 0.25f))
        gradCircle(kx, y, 0.52f, Neon.WHITE, 0xFFD5DAF0.toInt())
        hits += Hit(x - width / 2, y - 0.5f, x + width / 2, y + 0.5f, "t:$x:$y", { onChange(!value) }, null)
    }

    /** Horizontal 0..1 slider; tap or drag on it. */
    fun slider(label: String, x: Float, y: Float, width: Float, value: Float, accent: Int, onChange: (Float) -> Unit) {
        if (label.isNotEmpty()) text(label, x - width / 2 - 0.3f, y, 0.38f, Neon.TEXT, Align.RIGHT)
        rrect(x, y, width, 0.24f, Neon.a(Neon.BLACK, 0.35f), 0.12f)
        val v = value.coerceIn(0f, 1f)
        val fw = width * v
        if (fw > 0.05f) grad(x - width / 2 + fw / 2, y, fw, 0.24f, Neon.lighten(accent, 0.3f), Neon.darken(accent, 0.1f), 0.12f)
        circle(x - width / 2 + fw, y - 0.04f, 0.46f, Neon.a(Neon.BLACK, 0.25f))
        gradCircle(x - width / 2 + fw, y, 0.44f, Neon.WHITE, 0xFFD5DAF0.toInt())
        hits += Hit(x - width / 2 - 0.3f, y - 0.5f, x + width / 2 + 0.3f, y + 0.5f, "s:$x:$y", null) { wx ->
            onChange(((wx - (x - width / 2)) / width).coerceIn(0f, 1f))
        }
    }

    /** Banner ribbon with folded tails, for Level Complete / Failed titles. */
    fun ribbon(x: Float, y: Float, w: Float, h: Float, color: Int) {
        val dark = Neon.darken(color, 0.45f)
        val tx = w / 2 + 0.55f
        poly(floatArrayOf(x - w / 2 + 0.3f, y + h * 0.25f, x - tx, y + h * 0.25f, x - tx + 0.35f, y - h * 0.2f, x - tx, y - h * 0.65f, x - w / 2 + 0.3f, y - h * 0.65f), dark)
        poly(floatArrayOf(x + w / 2 - 0.3f, y + h * 0.25f, x + tx, y + h * 0.25f, x + tx - 0.35f, y - h * 0.2f, x + tx, y - h * 0.65f, x + w / 2 - 0.3f, y - h * 0.65f), dark)
        dropShadow(x, y, w, h, 0.2f)
        grad(x, y, w, h, Neon.lighten(color, 0.3f), Neon.darken(color, 0.15f), 0.2f)
        rrect(x, y + h * 0.22f, w - 0.4f, h * 0.22f, Neon.a(Neon.WHITE, 0.22f), h * 0.11f)
    }

    /** Gradient progress bar. */
    fun bar(x: Float, y: Float, w: Float, h: Float, t: Float, color: Int) {
        rrect(x, y, w, h, Neon.a(Neon.BLACK, 0.35f), h / 2)
        val fw = w * t.coerceIn(0f, 1f)
        if (fw > h * 0.6f) {
            grad(x - w / 2 + fw / 2, y, fw, h, Neon.lighten(color, 0.3f), Neon.darken(color, 0.1f), h / 2)
            rrect(x - w / 2 + fw / 2, y + h * 0.18f, fw - h * 0.5f, h * 0.22f, Neon.a(Neon.WHITE, 0.25f), h * 0.11f)
        }
    }

    fun drawParticles() = particles.draw(this)

    fun hitAt(x: Float, y: Float): Hit? {
        for (i in hits.indices.reversed()) if (hits[i].contains(x, y)) return hits[i]
        return null
    }
}
