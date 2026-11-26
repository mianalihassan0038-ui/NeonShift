package com.neonshift.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Colour palette from the game plan. */
object Neon {
    val BG = 0xFF090D1B.toInt()
    val LIGHT = 0xFF00E5FF.toInt()
    val SHADOW = 0xFFA855F7.toInt()
    val ENERGY = 0xFFFFD54A.toInt()
    val TEXT = 0xFFF4F7FF.toInt()
    val PANEL = 0xFF121A33.toInt()
    val EMPTY = 0xFF18223F.toInt()
    val BLOCKED = 0xFFFF4D6D.toInt()
    val SUCCESS = 0xFF3EE06B.toInt()
    val PINK = 0xFFFF4FD8.toInt()
    val BLUE = 0xFF2F7BFF.toInt()
    val ORANGE = 0xFFFF9F1C.toInt()
    val RED = 0xFFD7263D.toInt()
    val WORLD_ORANGE = 0xFFFF8A3D.toInt()

    fun a(c: Int, alpha: Float): Int = (c and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24)
    fun mul(c: Int, alpha: Float): Int = a(c, Color.alpha(c) / 255f * alpha)
    fun lerp(x: Int, y: Int, t: Float): Int {
        fun ch(s: Int) = ((x shr s and 0xFF) + ((y shr s and 0xFF) - (x shr s and 0xFF)) * t).toInt().coerceIn(0, 255)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    fun wall(accent: Int) = lerp(PANEL, accent, 0.4f)
}

enum class Style { OUTLINE, FILLED, ROUND }
enum class Align { LEFT, CENTER, RIGHT }

/**
 * Immediate-mode neon drawing in "world units": a 9.5 x 17.2 portrait area centred on (0, 0) with y pointing up,
 * the same layout units the Unity version used. Widgets register hit areas every frame.
 */
class Ui(private val mainFont: Typeface, val logoFont: Typeface) {
    lateinit var canvas: Canvas
    var ppu = 1f; private set
    private var cx = 0f
    private var cy = 0f
    var time = 0f

    class Hit(val l: Float, val b: Float, val r: Float, val t: Float, val id: String, val onTap: (() -> Unit)?, val onDrag: ((Float) -> Unit)?) {
        fun contains(x: Float, y: Float) = x in l..r && y in b..t
    }

    val hits = ArrayList<Hit>()
    var pressedId: String? = null
    var pressedAt = -10f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val glowShaders = HashMap<Int, Shader>()

    fun begin(c: Canvas, w: Int, h: Int, t: Float) {
        canvas = c
        time = t
        ppu = min(w / 9.5f, h / 17.2f)
        cx = w / 2f
        cy = h / 2f
        hits.clear()
        c.drawColor(Neon.BG)
    }

    fun sx(x: Float) = cx + x * ppu
    fun sy(y: Float) = cy - y * ppu
    fun toWorldX(px: Float) = (px - cx) / ppu
    fun toWorldY(py: Float) = (cy - py) / ppu

    /** Modal panels call this so that only their own buttons can be tapped. */
    fun blockBelow() = hits.clear()

    fun dim(alpha: Float = 0.85f) {
        fill.color = Neon.a(Neon.BG, alpha)
        canvas.drawPaint(fill)
        blockBelow()
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

    fun rrectStroke(x: Float, y: Float, w: Float, h: Float, color: Int, width: Float, radius: Float = defaultRadius(w, h)) {
        stroke.color = color
        stroke.strokeWidth = width * ppu
        rect.set(sx(x - w / 2), sy(y + h / 2), sx(x + w / 2), sy(y - h / 2))
        canvas.drawRoundRect(rect, radius * ppu, radius * ppu, stroke)
    }

    fun defaultRadius(w: Float, h: Float) = min(0.35f, min(w, h) * 0.3f)

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

    fun star(x: Float, y: Float, size: Float, color: Int) {
        path.reset()
        val outer = size * 0.5f
        val inner = outer * 0.45f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val ang = Math.PI / 2 + i * Math.PI / 5
            val px = sx(x + (r * cos(ang)).toFloat())
            val py = sy(y + (r * sin(ang)).toFloat())
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
    }

    fun stars(x: Float, y: Float, size: Float, filled: Int, on: Int = Neon.ENERGY, off: Int = Neon.a(Neon.TEXT, 0.22f)) {
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

    // ---------------- Text ----------------

    /** height is roughly the line height in world units. Returns the text width in world units. */
    fun text(s: String, x: Float, y: Float, height: Float, color: Int, align: Align = Align.CENTER, logo: Boolean = false): Float {
        if (s.isEmpty()) return 0f
        textPaint.typeface = if (logo) logoFont else mainFont
        textPaint.textSize = height * 0.82f * ppu
        textPaint.color = color
        textPaint.textAlign = when (align) {
            Align.LEFT -> Paint.Align.LEFT
            Align.CENTER -> Paint.Align.CENTER
            Align.RIGHT -> Paint.Align.RIGHT
        }
        val lines = s.split('\n')
        val fm = textPaint.fontMetrics
        val lineH = textPaint.textSize * 1.25f
        val total = lineH * (lines.size - 1)
        var py = sy(y) - total / 2f - (fm.ascent + fm.descent) / 2f
        var widest = 0f
        for (line in lines) {
            canvas.drawText(line, sx(x), py, textPaint)
            widest = maxOf(widest, textPaint.measureText(line))
            py += lineH
        }
        return widest / ppu
    }

    // ---------------- Composite widgets ----------------

    /** Glowing card: soft halo, bright border, dark fill. */
    fun card(x: Float, y: Float, w: Float, h: Float, accent: Int, fillAlpha: Float = 1f) {
        glow(x, y, w * 1.35f, h * 1.35f, Neon.a(accent, 0.22f))
        rrect(x, y, w, h, accent)
        rrect(x, y, w - 0.12f, h - 0.12f, Neon.a(Neon.PANEL, fillAlpha))
        rrect(x, y + h * 0.25f, w - 0.2f, h * 0.45f, Neon.a(accent, 0.07f))
    }

    fun background(a: Int, b: Int) {
        glow(-2.5f, 4.5f, 12f, 10f, Neon.a(a, 0.16f))
        glow(2.5f, -4.5f, 12f, 10f, Neon.a(b, 0.16f))
    }

    fun coins(x: Float, y: Float, height: Float, amount: Int) {
        circle(x, y, height * 1.25f, Neon.ENERGY)
        ring(x, y, height * 0.85f, height * 0.85f, Neon.a(Neon.BG, 0.35f), height * 0.12f)
        text(amount.toString(), x + height * 0.85f, y, height, Neon.TEXT, Align.LEFT)
    }

    /** Simple glowing character: body orb with two eyes. */
    fun character(x: Float, y: Float, color: Int, scale: Float = 1.6f) {
        glow(x, y, 1.6f * scale, 1.6f * scale, Neon.a(color, 0.8f))
        circle(x, y, 0.9f * scale, color)
        ellipse(x - 0.13f * scale, y + 0.06f * scale, 0.13f * scale, 0.18f * scale, Neon.BG)
        ellipse(x + 0.13f * scale, y + 0.06f * scale, 0.13f * scale, 0.18f * scale, Neon.BG)
    }

    fun logo(y: Float, height: Float) {
        glow(0f, y - height * 0.5f, 9f, height * 3f, Neon.a(Neon.SHADOW, 0.35f))
        text("NEON", -0.2f, y, height, Neon.LIGHT, logo = true)
        text("SHIFT", 0.25f, y - height * 0.95f, height, Neon.PINK, logo = true)
    }

    private fun pressScale(id: String) = if (id == pressedId && time - pressedAt < 0.12f) 0.92f else 1f

    /**
     * Neon button. Returns nothing; the click arrives later through the registered hit.
     * labelSize overrides the default label height.
     */
    fun button(
        label: String, x: Float, y: Float, w0: Float, h0: Float, accent: Int,
        style: Style = Style.OUTLINE, enabled: Boolean = true, caption: String? = null,
        labelSize: Float = 0f, onClick: () -> Unit
    ) {
        val id = "b:$x:$y"
        val k = pressScale(id)
        val w = w0 * k
        val h = h0 * k
        val al = if (enabled) 1f else 0.25f
        if (enabled) glow(x, y, w * 1.5f, h * 1.5f, Neon.a(accent, 0.28f))
        if (style == Style.ROUND) {
            circle(x, y, w, Neon.a(accent, al))
            circle(x, y, w - 0.1f, Neon.PANEL)
        } else {
            rrect(x, y, w, h, Neon.a(accent, al))
            rrect(x, y, w - 0.1f, h - 0.1f, if (style == Style.FILLED) Neon.a(accent, al) else Neon.PANEL)
            if (style == Style.FILLED) rrect(x, y + h * 0.2f, w - 0.25f, h * 0.35f, Neon.a(Color.WHITE, 0.18f))
        }
        val th = if (labelSize > 0f) labelSize else if (style == Style.ROUND) h * 0.5f else min(h * 0.42f, 0.6f)
        val tc = if (style == Style.FILLED) Neon.BG else Neon.a(accent, al)
        text(label, x, y, th * k, tc)
        if (caption != null) text(caption, x, y - h0 * 0.5f - 0.3f, 0.3f, Neon.TEXT)
        if (enabled) hits += Hit(x - w0 / 2 - 0.05f, y - h0 / 2 - 0.05f, x + w0 / 2 + 0.05f, y + h0 / 2 + 0.05f, id, onClick, null)
    }

    /** On/off pill switch with a label on the left. */
    fun toggle(label: String, x: Float, y: Float, width: Float, value: Boolean, onChange: (Boolean) -> Unit) {
        text(label, x - width / 2, y, 0.42f, Neon.TEXT, Align.LEFT)
        val tx = x + width / 2 - 0.6f
        rrect(tx, y, 1.2f, 0.62f, if (value) Neon.SUCCESS else Neon.a(Neon.TEXT, 0.2f), 0.31f)
        circle(tx + if (value) 0.3f else -0.3f, y, 0.54f, Neon.TEXT)
        hits += Hit(x - width / 2, y - 0.5f, x + width / 2, y + 0.5f, "t:$x:$y", { onChange(!value) }, null)
    }

    /** Horizontal 0..1 slider; tap or drag on it. */
    fun slider(label: String, x: Float, y: Float, width: Float, value: Float, accent: Int, onChange: (Float) -> Unit) {
        if (label.isNotEmpty()) text(label, x - width / 2 - 0.3f, y, 0.4f, Neon.TEXT, Align.RIGHT)
        rrect(x, y, width, 0.3f, Neon.a(Neon.TEXT, 0.15f), 0.15f)
        val v = value.coerceIn(0f, 1f)
        val fw = width * v
        if (fw > 0.01f) rrect(x - width / 2 + fw / 2, y, fw, 0.2f, accent, 0.1f)
        circle(x - width / 2 + fw, y, 0.42f, Neon.TEXT)
        hits += Hit(x - width / 2 - 0.3f, y - 0.5f, x + width / 2 + 0.3f, y + 0.5f, "s:$x:$y", null) { wx ->
            onChange(((wx - (x - width / 2)) / width).coerceIn(0f, 1f))
        }
    }

    fun hitAt(x: Float, y: Float): Hit? {
        for (i in hits.indices.reversed()) if (hits[i].contains(x, y)) return hits[i]
        return null
    }
}
