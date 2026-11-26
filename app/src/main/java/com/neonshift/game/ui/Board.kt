package com.neonshift.game.ui

import com.neonshift.game.core.BoardState
import com.neonshift.game.core.Direction
import com.neonshift.game.core.GridPos
import com.neonshift.game.core.TileType
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

const val CELL = 1.1f

/** Centre of a cell relative to the board centre (world units, y up). Row 0 is the top row. */
fun cellOffset(board: BoardState, p: GridPos, scale: Float = 1f): Pair<Float, Float> {
    val ox = (board.width - 1) * 0.5f
    val oy = (board.height - 1) * 0.5f
    return Pair((p.x - ox) * CELL * scale, (oy - p.y) * CELL * scale)
}

fun drawBoard(ui: Ui, board: BoardState, accent: Int, bx: Float, by: Float, scale: Float = 1f) {
    val c = CELL * scale
    val w = board.width * c
    val h = board.height * c
    ui.glow(bx, by, w * 1.6f, h * 1.6f, Neon.a(accent, 0.10f))
    ui.rrect(bx, by, w + 0.28f * scale, h + 0.28f * scale, Neon.a(accent, 0.55f), 0.3f * scale)
    ui.rrect(bx, by, w + 0.16f * scale, h + 0.16f * scale, Neon.PANEL, 0.26f * scale)
    for (y in 0 until board.height) for (x in 0 until board.width) {
        val p = GridPos(x, y)
        val (dx, dy) = cellOffset(board, p, scale)
        val cx = bx + dx
        val cy = by + dy
        when (board.tile(p)) {
            TileType.WALL -> {
                ui.rrect(cx, cy, c * 0.9f, c * 0.9f, Neon.wall(accent), 0.18f * scale)
                ui.rrect(cx, cy + c * 0.18f, c * 0.7f, c * 0.3f, Neon.a(accent, 0.12f), 0.1f * scale)
            }
            TileType.EXIT -> {
                ui.rrect(cx, cy, c * 0.9f, c * 0.9f, Neon.EMPTY, 0.18f * scale)
                val a = 0.30f + 0.20f * sin(ui.time * 3f)
                ui.glow(cx, cy, c * 1.4f, c * 1.4f, Neon.a(accent, a))
                ui.ring(cx, cy, c * 0.72f, c * 0.72f, accent, 0.09f * scale)
                ui.ring(cx, cy, c * 0.4f, c * 0.4f, Neon.a(accent, 0.6f), 0.06f * scale)
            }
            TileType.EMPTY -> ui.rrect(cx, cy, c * 0.9f, c * 0.9f, Neon.EMPTY, 0.18f * scale)
        }
    }
}

/** Visual token for Light or Shadow. The grid position lives in PuzzleState; this only animates. */
class Token(val accent: Int) {
    var x = 0f; var y = 0f
    private var fromX = 0f; private var fromY = 0f
    private var toX = 0f; private var toY = 0f
    private var bumpX = 0f; private var bumpY = 0f
    private var mode = 0 // 0 idle, 1 move, 2 bump, 3 celebrate
    private var t = 0f
    private var dur = 1f
    private var flash = 0f
    private var scale = 1f

    val busy get() = mode != 0

    fun snap(px: Float, py: Float) { x = px; y = py; mode = 0; flash = 0f; scale = 1f }

    fun moveTo(px: Float, py: Float, duration: Float) {
        fromX = x; fromY = y; toX = px; toY = py; t = 0f; dur = duration; mode = 1; flash = 0f; scale = 1f
    }

    fun bump(d: Direction, duration: Float) {
        fromX = x; fromY = y
        bumpX = d.dx * CELL * 0.18f; bumpY = -d.dy * CELL * 0.18f
        t = 0f; dur = duration; mode = 2
    }

    fun celebrate(duration: Float) { t = 0f; dur = duration; mode = 3 }

    fun update(dt: Float) {
        if (mode == 0) return
        t += dt
        val k = (t / dur).coerceIn(0f, 1f)
        when (mode) {
            1 -> {
                val e = 1f - (1f - k).pow(3)
                x = fromX + (toX - fromX) * e
                y = fromY + (toY - fromY) * e
            }
            2 -> {
                val s = sin(k * PI).toFloat()
                x = fromX + bumpX * s
                y = fromY + bumpY * s
                flash = s
            }
            3 -> scale = 1f + 0.25f * sin(k * PI).toFloat()
        }
        if (k >= 1f) {
            if (mode == 1) { x = toX; y = toY }
            if (mode == 2) { x = fromX; y = fromY }
            mode = 0; flash = 0f; scale = 1f
        }
    }

    fun draw(ui: Ui, bx: Float, by: Float) {
        val px = bx + x
        val py = by + y
        val s = scale
        val color = Neon.lerp(accent, Neon.BLOCKED, flash)
        ui.glow(px, py, CELL * 1.5f * s, CELL * 1.5f * s, Neon.a(accent, 0.8f))
        ui.circle(px, py, CELL * 0.8f * s, color)
        ui.circle(px - 0.1f * s, py + 0.16f * s, CELL * 0.22f * s, Neon.a(0xFFFFFFFF.toInt(), 0.25f))
        ui.ellipse(px - 0.11f * s, py + 0.05f * s, 0.11f * s, 0.16f * s, Neon.BG)
        ui.ellipse(px + 0.11f * s, py + 0.05f * s, 0.11f * s, 0.16f * s, Neon.BG)
    }
}

fun angleOf(d: Direction) = when (d) {
    Direction.UP -> 90f
    Direction.LEFT -> 180f
    Direction.DOWN -> -90f
    Direction.RIGHT -> 0f
}
