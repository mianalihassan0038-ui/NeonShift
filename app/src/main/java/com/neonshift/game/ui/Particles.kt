package com.neonshift.game.ui

import kotlin.math.cos
import kotlin.math.sin

/** Small neon particle system in world units: bursts, sparks, confetti and rings. */
class Particles {
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float,
                    val size: Float, val color: Int, val gravity: Float, val kind: Int, var spin: Float)

    private val list = ArrayList<P>()
    private val rnd = java.util.Random()

    fun clear() = list.clear()

    private fun r() = rnd.nextFloat()

    /** Round sparks flying out of a point. */
    fun burst(x: Float, y: Float, color: Int, count: Int = 14, speed: Float = 3f, size: Float = 0.14f, life: Float = 0.5f) {
        repeat(count) {
            val a = r() * 6.2832f
            val v = speed * (0.4f + r() * 0.8f)
            list += P(x, y, cos(a) * v, sin(a) * v, life * (0.6f + r() * 0.6f), life, size * (0.6f + r() * 0.8f), color, 0f, 0, 0f)
        }
    }

    /** Falling confetti rectangles from above. */
    fun confetti(colors: IntArray, halfW: Float, top: Float, count: Int = 90) {
        repeat(count) {
            val c = colors[rnd.nextInt(colors.size)]
            list += P(-halfW + r() * 2 * halfW, top + r() * 2f, (r() - 0.5f) * 2f, -1f - r() * 3f, 2.2f + r(), 3f,
                0.16f + r() * 0.12f, c, -2.5f, 1, r() * 6f)
        }
    }

    /** Expanding ring (used for portals, switches and the exit). */
    fun ring(x: Float, y: Float, color: Int, size: Float = 1.6f, life: Float = 0.45f) {
        list += P(x, y, 0f, 0f, life, life, size, color, 0f, 2, 0f)
    }

    /** A soft trail dot that just fades. */
    fun dot(x: Float, y: Float, color: Int, size: Float, life: Float = 0.25f) {
        list += P(x, y, 0f, 0f, life, life, size, color, 0f, 3, 0f)
    }

    fun update(dt: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vy += p.gravity * dt
            if (p.kind == 0) { p.vx *= (1f - 2.5f * dt); p.vy *= (1f - 2.5f * dt) }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.spin += dt * 6f
        }
        if (list.size > 600) list.subList(0, list.size - 600).clear()
    }

    fun draw(ui: Ui) {
        for (p in list) {
            val k = (p.life / p.maxLife).coerceIn(0f, 1f)
            when (p.kind) {
                0 -> { ui.glow(p.x, p.y, p.size * 3f, p.size * 3f, Neon.a(p.color, 0.35f * k)); ui.circle(p.x, p.y, p.size * k + 0.02f, Neon.a(p.color, k)) }
                1 -> ui.box(p.x, p.y, p.size * (0.4f + 0.6f * kotlin.math.abs(sin(p.spin))), p.size * 0.55f, Neon.a(p.color, minOf(1f, k * 2f)))
                2 -> { val d = p.size * (1.6f - k); ui.ring(p.x, p.y, d, d, Neon.a(p.color, k), 0.08f) }
                else -> ui.circle(p.x, p.y, p.size * k, Neon.a(p.color, 0.5f * k))
            }
        }
    }
}
