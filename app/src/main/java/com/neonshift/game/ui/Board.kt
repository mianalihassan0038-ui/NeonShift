package com.neonshift.game.ui

import com.neonshift.game.core.BoardState
import com.neonshift.game.core.Direction
import com.neonshift.game.core.GridPos
import com.neonshift.game.core.PuzzleState
import com.neonshift.game.core.TileType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

const val CELL = 1.1f

/** Orb colour pairs sold in the shop. */
class Skin(val name: String, val light: Int, val shadow: Int, val price: Int)

val SKINS = listOf(
    Skin("Classic", Neon.LIGHT, Neon.SHADOW, 0),
    Skin("Sunset", 0xFFFFB13B.toInt(), 0xFFFF4F81.toInt(), 400),
    Skin("Toxic", 0xFFB6FF3B.toInt(), 0xFF1DE9B6.toInt(), 600),
    Skin("Ice & Fire", 0xFF8FE9FF.toInt(), 0xFFFF6A3D.toInt(), 800),
    Skin("Royal", 0xFFFFD54A.toInt(), 0xFF6C63FF.toInt(), 1000),
    Skin("Sakura", 0xFFFFB7E1.toInt(), 0xFF9C6BFF.toInt(), 1300),
    Skin("Matrix", 0xFF39FF88.toInt(), 0xFF00B3FF.toInt(), 1600),
    Skin("Mono", 0xFFFFFFFF.toInt(), 0xFF8A93B8.toInt(), 2000)
)

/** Centre of a cell relative to the board centre (world units, y up). Row 0 is the top row. */
fun cellOffset(board: BoardState, p: GridPos, scale: Float = 1f): Pair<Float, Float> {
    val ox = (board.width - 1) * 0.5f
    val oy = (board.height - 1) * 0.5f
    return Pair((p.x - ox) * CELL * scale, (oy - p.y) * CELL * scale)
}

/**
 * Draws one board. [puzzle] supplies the live state (collected cores, broken tiles, gates);
 * pass null for a static preview.
 */
fun drawBoard(ui: Ui, board: BoardState, accent: Int, bx: Float, by: Float, scale: Float = 1f, puzzle: PuzzleState? = null, isLight: Boolean = true) {
    val c = CELL * scale
    val w = board.width * c
    val h = board.height * c
    val t = ui.time
    ui.glow(bx, by, w * 1.6f, h * 1.6f, Neon.a(accent, 0.12f))
    ui.rrect(bx, by, w + 0.28f * scale, h + 0.28f * scale, Neon.a(accent, 0.65f), 0.3f * scale)
    ui.rrect(bx, by, w + 0.16f * scale, h + 0.16f * scale, Neon.PANEL, 0.26f * scale)
    val gatesOpen = puzzle?.gatesOpen ?: false
    val coresLeft = puzzle?.coresLeft(isLight) ?: board.data.cores.size
    for (y in 0 until board.height) for (x in 0 until board.width) {
        val p = GridPos(x, y)
        val (dx, dy) = cellOffset(board, p, scale)
        val cx = bx + dx
        val cy = by + dy
        val tile = c * 0.9f
        val r = 0.18f * scale
        if (puzzle != null && puzzle.isBroken(isLight, p)) {
            ui.rrect(cx, cy, tile, tile, 0xFF05070F.toInt(), r)
            ui.rrectStroke(cx, cy, tile * 0.8f, tile * 0.8f, Neon.a(Neon.BLOCKED, 0.25f), 0.04f * scale, r)
            continue
        }
        when (board.tile(p)) {
            TileType.WALL -> {
                ui.rrect(cx, cy, tile, tile, Neon.wall(accent), r)
                ui.rrect(cx, cy + c * 0.18f, c * 0.7f, c * 0.3f, Neon.a(accent, 0.14f), 0.1f * scale)
            }
            TileType.EXIT -> {
                ui.rrect(cx, cy, tile, tile, Neon.EMPTY, r)
                val locked = coresLeft > 0
                val a = if (locked) 0.12f else 0.30f + 0.20f * sin(t * 3f)
                ui.glow(cx, cy, c * 1.5f, c * 1.5f, Neon.a(accent, a))
                ui.ring(cx, cy, c * 0.72f, c * 0.72f, Neon.a(accent, if (locked) 0.4f else 1f), 0.09f * scale)
                if (locked) {
                    ui.text(coresLeft.toString(), cx, cy, c * 0.42f, Neon.ENERGY)
                } else {
                    val k = (t * 0.8f) % 1f
                    ui.ring(cx, cy, c * (0.3f + 0.4f * k), c * (0.3f + 0.4f * k), Neon.a(accent, 1f - k), 0.05f * scale)
                }
            }
            TileType.CORE -> {
                ui.rrect(cx, cy, tile, tile, Neon.EMPTY, r)
                val has = puzzle?.hasCore(isLight, p) ?: true
                if (has) {
                    val bob = sin(t * 3f + x + y) * 0.04f * scale
                    ui.glow(cx, cy + bob, c * 0.9f, c * 0.9f, Neon.a(Neon.ENERGY, 0.55f))
                    diamond(ui, cx, cy + bob, c * 0.38f, Neon.ENERGY, t * 1.5f)
                }
            }
            TileType.PORTAL -> {
                ui.rrect(cx, cy, tile, tile, Neon.EMPTY, r)
                val pc = 0xFFFF8A3D.toInt()
                ui.glow(cx, cy, c * 1.1f, c * 1.1f, Neon.a(pc, 0.45f))
                for (i in 0 until 3) {
                    val k = ((t * 0.9f + i / 3f) % 1f)
                    val d = c * (0.75f - 0.55f * k)
                    ui.ring(cx, cy, d, d, Neon.a(pc, 0.3f + 0.7f * k), 0.06f * scale)
                }
                ui.circle(cx, cy, c * 0.14f, 0xFFFFFFFF.toInt())
            }
            TileType.SWITCH -> {
                ui.rrect(cx, cy, tile, tile, Neon.EMPTY, r)
                val sc = if (gatesOpen) Neon.SUCCESS else Neon.PINK
                ui.glow(cx, cy, c * 0.9f, c * 0.9f, Neon.a(sc, 0.35f))
                ui.circle(cx, cy, c * 0.56f, Neon.a(sc, 0.9f))
                ui.circle(cx, cy, c * 0.4f, Neon.PANEL)
                ui.circle(cx, cy, c * 0.22f, sc)
            }
            TileType.GATE -> {
                if (gatesOpen) {
                    ui.rrect(cx, cy, tile, tile, Neon.EMPTY, r)
                    ui.rrectStroke(cx, cy, tile * 0.86f, tile * 0.86f, Neon.a(Neon.SUCCESS, 0.45f), 0.04f * scale, r)
                } else {
                    ui.rrect(cx, cy, tile, tile, Neon.a(Neon.PINK, 0.22f), r)
                    for (i in -1..1) ui.box(cx + i * c * 0.24f, cy, c * 0.08f, tile * 0.86f, Neon.PINK)
                    ui.box(cx, cy + tile * 0.32f, tile * 0.8f, c * 0.06f, Neon.PINK)
                    ui.box(cx, cy - tile * 0.32f, tile * 0.8f, c * 0.06f, Neon.PINK)
                }
            }
            TileType.CRUMBLE -> {
                ui.rrect(cx, cy, tile, tile, 0xFF2A2338.toInt(), r)
                val cr = Neon.a(0xFFFFB13B.toInt(), 0.55f)
                ui.line(cx - tile * 0.3f, cy + tile * 0.25f, cx, cy, 0.035f * scale, cr)
                ui.line(cx, cy, cx + tile * 0.12f, cy + tile * 0.32f, 0.035f * scale, cr)
                ui.line(cx, cy, cx + tile * 0.28f, cy - tile * 0.28f, 0.035f * scale, cr)
                ui.line(cx, cy, cx - tile * 0.2f, cy - tile * 0.3f, 0.035f * scale, cr)
            }
            TileType.EMPTY -> ui.rrect(cx, cy, tile, tile, Neon.EMPTY, r)
        }
    }
}

private fun diamond(ui: Ui, x: Float, y: Float, size: Float, color: Int, spin: Float) {
    val w = size * (0.55f + 0.45f * kotlin.math.abs(cos(spin)))
    ui.poly(floatArrayOf(x, y + size, x + w, y, x, y - size, x - w, y), color)
    ui.poly(floatArrayOf(x, y + size * 0.55f, x + w * 0.45f, y, x, y - size * 0.55f, x - w * 0.45f, y), Neon.a(0xFFFFFFFF.toInt(), 0.5f))
}

/** Visual token for Light or Shadow. The grid position lives in PuzzleState; this only animates. */
class Token(var accent: Int) {
    var x = 0f; var y = 0f
    private var fromX = 0f; private var fromY = 0f
    private var toX = 0f; private var toY = 0f
    private var bumpX = 0f; private var bumpY = 0f
    private var mode = 0 // 0 idle, 1 move, 2 bump, 3 celebrate, 4 teleport
    private var t = 0f
    private var dur = 1f
    private var flash = 0f
    private var scale = 1f
    private var dirX = 0f
    private var dirY = 0f
    private var stretch = 0f
    private var tpX = 0f
    private var tpY = 0f
    var size = 1f

    val busy get() = mode != 0
    /** Set for one frame when a teleport jump happens, so the caller can add effects. */
    var teleportedAt: Pair<Float, Float>? = null

    fun snap(px: Float, py: Float) { x = px; y = py; mode = 0; flash = 0f; scale = 1f; stretch = 0f }

    fun moveTo(px: Float, py: Float, duration: Float) {
        fromX = x; fromY = y; toX = px; toY = py; t = 0f; dur = duration; mode = 1; flash = 0f; scale = 1f
        val dx = px - x; val dy = py - y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        dirX = dx / len; dirY = dy / len
    }

    /** Moves onto the portal cell, then jumps to the twin portal. */
    fun teleport(stepX: Float, stepY: Float, endX: Float, endY: Float, duration: Float) {
        moveTo(stepX, stepY, duration * 0.5f)
        tpX = endX; tpY = endY
        mode = 4
        dur = duration
    }

    fun bump(d: Direction, duration: Float) {
        fromX = x; fromY = y
        bumpX = d.dx * CELL * size * 0.18f; bumpY = -d.dy * CELL * size * 0.18f
        t = 0f; dur = duration; mode = 2
    }

    fun celebrate(duration: Float) { t = 0f; dur = duration; mode = 3 }

    fun update(dt: Float, trail: ((Float, Float) -> Unit)? = null) {
        if (mode == 0) return
        t += dt
        val k = (t / dur).coerceIn(0f, 1f)
        when (mode) {
            1 -> {
                val e = 1f - (1f - k).pow(3)
                x = fromX + (toX - fromX) * e
                y = fromY + (toY - fromY) * e
                stretch = sin(k * PI).toFloat()
                trail?.invoke(x, y)
            }
            4 -> {
                if (k < 0.5f) {
                    val kk = k * 2f
                    val e = 1f - (1f - kk).pow(3)
                    x = fromX + (toX - fromX) * e
                    y = fromY + (toY - fromY) * e
                    scale = 1f - 0.8f * kk
                } else {
                    if (x != tpX || y != tpY) { x = tpX; y = tpY; teleportedAt = Pair(tpX, tpY) }
                    scale = 0.2f + 0.8f * ((k - 0.5f) * 2f)
                }
            }
            2 -> {
                val s = sin(k * PI).toFloat()
                x = fromX + bumpX * s
                y = fromY + bumpY * s
                flash = s
            }
            3 -> scale = 1f + 0.3f * sin(k * PI).toFloat()
        }
        if (k >= 1f) {
            if (mode == 1) { x = toX; y = toY }
            if (mode == 2) { x = fromX; y = fromY }
            if (mode == 4) { x = tpX; y = tpY }
            mode = 0; flash = 0f; scale = 1f; stretch = 0f
        }
    }

    fun draw(ui: Ui, bx: Float, by: Float) {
        val px = bx + x
        val py = by + y
        val s = scale * size
        val color = Neon.lerp(accent, Neon.BLOCKED, flash)
        val pulse = 1f + 0.04f * sin(ui.time * 4f)
        ui.glow(px, py, CELL * 1.6f * s * pulse, CELL * 1.6f * s * pulse, Neon.a(accent, 0.8f))
        // Squash and stretch along the move direction.
        val sx = 1f + 0.22f * stretch * kotlin.math.abs(dirX) - 0.12f * stretch * kotlin.math.abs(dirY)
        val sy = 1f + 0.22f * stretch * kotlin.math.abs(dirY) - 0.12f * stretch * kotlin.math.abs(dirX)
        ui.ellipse(px, py, CELL * 0.8f * s * sx, CELL * 0.8f * s * sy, color)
        ui.circle(px - 0.1f * s, py + 0.16f * s, CELL * 0.22f * s, Neon.a(0xFFFFFFFF.toInt(), 0.3f))
        // Eyes look where the token is going, and blink now and then.
        val lookX = dirX * 0.05f * s
        val lookY = dirY * 0.04f * s
        val blink = if ((ui.time + accent % 7) % 3.7f < 0.12f) 0.25f else 1f
        ui.ellipse(px - 0.11f * s + lookX, py + 0.05f * s + lookY, 0.11f * s, 0.16f * s * blink, Neon.BG)
        ui.ellipse(px + 0.11f * s + lookX, py + 0.05f * s + lookY, 0.11f * s, 0.16f * s * blink, Neon.BG)
    }
}

fun angleOf(d: Direction) = when (d) {
    Direction.UP -> 90f
    Direction.LEFT -> 180f
    Direction.DOWN -> -90f
    Direction.RIGHT -> 0f
}
