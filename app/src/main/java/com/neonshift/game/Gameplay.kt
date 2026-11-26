package com.neonshift.game

import com.neonshift.game.core.*
import com.neonshift.game.ui.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.PI
import kotlin.math.sin

enum class PlayMode { LEVEL, DAILY, TIME_ATTACK }

/**
 * Gameplay screen: two boards that fill the screen, two tokens, HUD, effects and the
 * Pause / Level Complete / Level Failed / Hint / mechanic intro / Time Attack panels.
 */
class Gameplay(private val game: Game) {
    private enum class Panel { NONE, PAUSE, VICTORY, FAILURE, HINT, INTRO, TIME_UP, CHEST }
    private enum class State { PLAYING, ANIMATING, PAUSED, VICTORY, FAILED }

    private val moveDuration = 0.13f
    private val teleportDuration = 0.32f
    private val bumpDuration = 0.16f

    private var puzzle: PuzzleState? = null
    var mode = PlayMode.LEVEL; private set
    private val daily get() = mode == PlayMode.DAILY
    private var usedHint = false
    private var state = State.PLAYING
    private var panel = Panel.NONE
    private val light = Token(Neon.LIGHT)
    private val shadow = Token(Neon.SHADOW)
    private var colorL = Neon.LIGHT
    private var colorS = Neon.SHADOW
    private var message: String? = null
    private var arrow: Direction? = null
    private var pendingOutcome: MoveOutcome? = null
    private var animLeft = 0f
    private var celebrateLeft = 0f
    private var queued: Direction? = null
    private var ui: Ui? = null

    // Layout, recomputed every frame from the screen size.
    private var scale = 0f
    private var lightY = 3f
    private var shadowY = -3.5f
    private var hintY = 0f

    // Combo: quick consecutive moves raise the pitch.
    private var combo = 0
    private var lastMoveAt = -10f

    // Victory panel data and animation.
    private var vStars = 0
    private var vCoins = 0
    private var vLast = false
    private var vTime = 0f
    private var vStarsShown = 0
    private var vCoinTick = 0f
    private var vXpFrom = 0
    private var vXpTo = 0
    private var vXpGain = 0
    private var vLevelShown = 1
    private var vLevelUpAt = -10f
    private var vLevelUpText = ""
    private var vStreak = 0
    private var vMult = 1
    private var vChestReady = false
    private var vAuto = 0f
    private var chestT = 0f
    private var chestText = ""
    private var chestOpened = false

    // Guided tutorial and near-miss nudge.
    private var guide: Direction? = null
    private var nearMiss = false
    private var shadowAppear = 1f
    private var pendingHint = Direction.UP
    private var hintCostText = ""

    // Time Attack.
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "neon-gen").apply { isDaemon = true } }
    private var taRng = java.util.Random()
    private var taNext: Future<LevelData>? = null
    private var taScore = 0
    private var taTime = 0f
    private var taWaiting = false
    private var taCountdown = 0f
    private var taPopup = 0f
    private var taPopupText = ""
    private var taNewBest = false
    private var taCoins = 0

    fun load(source: LevelData, mode: PlayMode) {
        val relax = mode == PlayMode.LEVEL && game.save.relaxed && game.save.unlocked(Unlocks.RELAX_LEVEL) && source.maxMoves > 0
        val level = if (relax) LevelGen.copyLevel(source).also { it.maxMoves = 0 } else source
        val p = PuzzleState(level)
        puzzle = p
        this.mode = mode
        usedHint = false
        panel = Panel.NONE
        message = null
        arrow = null
        animLeft = 0f
        celebrateLeft = 0f
        pendingOutcome = null
        queued = null
        combo = 0
        val skin = SKINS[game.save.skin.coerceIn(0, SKINS.size - 1)]
        colorL = skin.light; colorS = skin.shadow
        light.accent = colorL; shadow.accent = colorS
        scale = 0f // forces a fresh layout and token snap on the next frame
        nearMiss = false
        shadowAppear = if (level.tutorial == "shadow") 0f else 1f
        updateGuide()
        state = State.PLAYING
        if (mode == PlayMode.LEVEL && level.levelId == 1 && level.worldId in 2..5 && !game.save.introSeen(level.worldId)) {
            state = State.PAUSED
            panel = Panel.INTRO
        }
    }

    fun startTimeAttack() {
        taRng = java.util.Random(System.nanoTime())
        taScore = 0
        taTime = TimeAttack.START_SECONDS
        taNewBest = false
        taPopup = 0f
        taCountdown = 1.6f
        taWaiting = true
        puzzle = null
        mode = PlayMode.TIME_ATTACK
        panel = Panel.NONE
        requestNextPuzzle()
    }

    private fun requestNextPuzzle() {
        val solved = taScore
        val rng = taRng
        taNext = worker.submit<LevelData> { TimeAttack.puzzle(rng, solved) }
    }

    private fun tryLoadNextPuzzle() {
        val f = taNext ?: return
        if (!f.isDone || taCountdown > 0f) return
        taNext = null
        taWaiting = false
        val level = try { f.get() } catch (e: Exception) { TimeAttack.puzzle(java.util.Random(), 0) }
        load(level, PlayMode.TIME_ATTACK)
        requestNextPuzzle()
    }

    /** Guided levels always show the next best swipe with a ghost finger. */
    private fun updateGuide() {
        val p = puzzle ?: return
        guide = if (p.level.tutorial.isNotEmpty() && !p.isSolved) PuzzleSolver.solve(p.level, p.state)?.firstOrNull() else null
        arrow = guide
    }

    private fun tokenPos(light: Boolean): Pair<Float, Float> {
        val p = puzzle!!
        return if (light) cellOffset(p.lightBoard, p.lightPos, scale) else cellOffset(p.shadowBoard, p.shadowPos, scale)
    }

    private fun snapTokens() {
        if (puzzle == null) return
        light.size = scale; shadow.size = scale
        tokenPos(true).let { light.snap(it.first, it.second) }
        tokenPos(false).let { shadow.snap(it.first, it.second) }
    }

    private fun moveTokens() {
        if (puzzle == null) return
        tokenPos(true).let { light.moveTo(it.first, it.second, moveDuration) }
        tokenPos(false).let { shadow.moveTo(it.first, it.second, moveDuration) }
    }

    fun back() {
        when {
            panel == Panel.INTRO -> closeIntro()
            state == State.PAUSED -> resume()
            state == State.PLAYING || state == State.ANIMATING -> pause()
            else -> exitToList()
        }
    }

    private fun exitToList() = game.show(when (mode) {
        PlayMode.DAILY -> AppScreen.DAILY
        PlayMode.TIME_ATTACK -> AppScreen.TIME_ATTACK
        PlayMode.LEVEL -> AppScreen.LEVEL_SELECT
    })

    fun swipe(d: Direction) {
        val p = puzzle ?: return
        if (taWaiting || shadowAppear < 1f) return
        if (state == State.ANIMATING && panel == Panel.NONE) { queued = d; return } // one swipe is remembered while moving
        if (state != State.PLAYING || panel != Panel.NONE) return
        val o = p.tryMove(d)
        if (o.result == MoveResult.ALREADY_SOLVED || o.result == MoveResult.OUT_OF_MOVES) return
        state = State.ANIMATING
        val u = ui
        if (o.result == MoveResult.MOVED) {
            var dur = moveDuration
            val lb = p.lightBoard
            val sb = p.shadowBoard
            if (o.lightTeleported) {
                val st = cellOffset(lb, o.lightStep!!, scale); val en = cellOffset(lb, o.lightTo!!, scale)
                light.teleport(st.first, st.second, en.first, en.second, teleportDuration); dur = teleportDuration
            } else tokenPos(true).let { light.moveTo(it.first, it.second, moveDuration) }
            if (o.shadowTeleported) {
                val st = cellOffset(sb, o.shadowStep!!, scale); val en = cellOffset(sb, o.shadowTo!!, scale)
                shadow.teleport(st.first, st.second, en.first, en.second, teleportDuration); dur = teleportDuration
            } else tokenPos(false).let { shadow.moveTo(it.first, it.second, moveDuration) }
            animLeft = dur
            // Crumble tiles break the moment the token leaves them.
            if (u != null && (o.lightCrumbled != null || o.shadowCrumbled != null)) {
                o.lightCrumbled?.let { c -> cellOffset(lb, c, scale).let { crumbleFx(u, it.first, lightY + it.second) } }
                o.shadowCrumbled?.let { c -> cellOffset(sb, c, scale).let { crumbleFx(u, it.first, shadowY + it.second) } }
                game.play(Sfx.CRUMBLE)
                u.shake(0.05f)
            }
        } else {
            if (o.lightBlocked) light.bump(d, bumpDuration)
            if (o.shadowBlocked) shadow.bump(d.opposite(), bumpDuration)
            animLeft = bumpDuration
            if (u != null) {
                u.shake(0.1f)
                if (o.lightBlocked) u.particles.burst(light.x + d.dx * 0.45f * scale, lightY + light.y - d.dy * 0.45f * scale, Neon.BLOCKED, 8, 2.2f, 0.09f, 0.3f)
                if (o.shadowBlocked) { val e = d.opposite(); u.particles.burst(shadow.x + e.dx * 0.45f * scale, shadowY + shadow.y - e.dy * 0.45f * scale, Neon.BLOCKED, 8, 2.2f, 0.09f, 0.3f) }
            }
        }
        pendingOutcome = o
    }

    private fun crumbleFx(u: Ui, x: Float, y: Float) {
        u.particles.burst(x, y, 0xFFFFB13B.toInt(), 10, 2.5f, 0.1f, 0.45f)
        u.particles.burst(x, y, 0xFF6B5A7A.toInt(), 8, 1.6f, 0.14f, 0.5f)
    }

    private fun moveFinished(o: MoveOutcome) {
        val p = puzzle ?: return
        val u = ui
        if (o.result == MoveResult.MOVED) {
            val now = u?.time ?: 0f
            combo = if (now - lastMoveAt < 0.9f) combo + 1 else 0
            lastMoveAt = now
            game.play(Sfx.MOVE, 1f + 0.06f * minOf(combo, 10))
            arrow = null
            message = null
            if (!game.save.tutorialDone) game.save.tutorialDone = true
            if (u != null) landingFx(u, p, o)
            updateGuide()
            val was = nearMiss
            nearMiss = guide == null && p.moveCount >= 2 && p.winningMove() != null
            if (nearMiss && !was) { game.play(Sfx.STAR, 1.3f); game.pulse(15) }
        } else {
            combo = 0
            game.play(Sfx.BLOCKED)
            game.pulse(25)
        }
        when {
            o.solvedNow -> {
                state = State.VICTORY
                game.play(Sfx.WIN)
                game.pulse(50)
                light.celebrate(0.45f)
                shadow.celebrate(0.45f)
                celebrateLeft = 0.45f
                if (u != null) {
                    u.particles.burst(light.x, lightY + light.y, colorL, 26, 4.5f, 0.16f, 0.7f)
                    u.particles.burst(shadow.x, shadowY + shadow.y, colorS, 26, 4.5f, 0.16f, 0.7f)
                    u.particles.ring(light.x, lightY + light.y, colorL, 2.4f * scale, 0.6f)
                    u.particles.ring(shadow.x, shadowY + shadow.y, colorS, 2.4f * scale, 0.6f)
                    u.shake(0.08f)
                }
            }
            o.failedNow -> {
                state = State.FAILED
                game.save.winStreak = 0
                game.play(Sfx.FAIL)
                game.pulse(60)
                panel = Panel.FAILURE
            }
            else -> {
                state = State.PLAYING
                queued?.let { queued = null; swipe(it) }
            }
        }
        if (state != State.PLAYING && state != State.ANIMATING) queued = null
    }

    private fun landingFx(u: Ui, p: PuzzleState, o: MoveOutcome) {
        val lb = p.lightBoard
        val sb = p.shadowBoard
        fun core(board: BoardState, c: GridPos, by: Float, isLight: Boolean) {
            val (x, y) = cellOffset(board, c, scale)
            u.particles.burst(x, by + y, Neon.ENERGY, 16, 3.5f, 0.13f, 0.55f)
            u.particles.ring(x, by + y, Neon.ENERGY, 1.6f * scale)
            if (p.coresLeft(isLight) == 0) {
                val (ex, ey) = cellOffset(board, board.exit, scale)
                u.particles.ring(ex, by + ey, if (isLight) colorL else colorS, 2.2f * scale, 0.7f)
                u.particles.burst(ex, by + ey, if (isLight) colorL else colorS, 14, 3f, 0.12f, 0.6f)
            }
        }
        if (o.lightCore != null || o.shadowCore != null) {
            o.lightCore?.let { core(lb, it, lightY, true) }
            o.shadowCore?.let { core(sb, it, shadowY, false) }
            game.play(Sfx.CORE, 1f + 0.08f * (p.level.light.cores.size + p.level.shadow.cores.size - p.coresLeft(true) - p.coresLeft(false)))
            game.pulse(15)
        }
        if (o.gatesToggled) {
            val c = if (p.gatesOpen) Neon.SUCCESS else Neon.PINK
            for (g in p.level.light.gates) cellOffset(lb, g, scale).let { u.particles.ring(it.first, lightY + it.second, c, 1.4f * scale) }
            for (g in p.level.shadow.gates) cellOffset(sb, g, scale).let { u.particles.ring(it.first, shadowY + it.second, c, 1.4f * scale) }
            if (lb.isSwitch(p.lightPos)) u.particles.burst(light.x, lightY + light.y, c, 12, 3f, 0.11f, 0.45f)
            if (sb.isSwitch(p.shadowPos)) u.particles.burst(shadow.x, shadowY + shadow.y, c, 12, 3f, 0.11f, 0.45f)
            game.play(Sfx.SWITCH, if (p.gatesOpen) 1.2f else 0.9f)
            game.pulse(20)
        }
    }

    private fun win() {
        val p = puzzle ?: return
        val level = p.level
        val save = game.save
        if (mode == PlayMode.TIME_ATTACK) { timeAttackSolved(p); return }
        val stars = StarManager.calculate(level, p.moveCount)
        val coins: Int
        save.winStreak = save.winStreak + 1
        vStreak = save.winStreak
        vMult = 1
        if (daily) {
            coins = save.completeDaily(game.today)
        } else {
            val previous = save.stars(level)
            vMult = Rewards.streakMultiplier(vStreak)
            coins = (Rewards.levelCoins(previous, stars) + Rewards.WIN_BASE_COINS) * vMult
            save.recordStars(level, stars)
            if (!usedHint) save.markNoHint(level)
            save.addCoins(coins)
            game.levels.markCurrentCompleted()
        }
        vXpFrom = save.xp
        vXpGain = Rewards.winXp(stars)
        vLevelShown = Rewards.levelInfo(vXpFrom).first
        save.addXp(vXpGain)
        vXpTo = save.xp
        if (!daily) save.chestProgress = minOf(Rewards.CHEST_EVERY, save.chestProgress + 1)
        vChestReady = save.chestProgress >= Rewards.CHEST_EVERY
        vStars = stars
        vCoins = coins
        vLast = !daily && game.levels.isLastLevel
        vTime = 0f
        vStarsShown = 0
        vCoinTick = 0f
        vAuto = 0f
        vLevelUpAt = -10f
        panel = Panel.VICTORY
        ui?.let { it.particles.confetti(intArrayOf(colorL, colorS, Neon.ENERGY, Neon.PINK, Neon.SUCCESS), it.halfW, it.halfH, 110) }
    }

    private fun timeAttackSolved(p: PuzzleState) {
        taScore++
        val bonus = TimeAttack.bonusSeconds(p.level.starThresholds.threeStarsMaxMoves)
        taTime += bonus
        taPopupText = "+${bonus.toInt()}s"
        taPopup = 1.2f
        game.play(Sfx.STAR, 1f + 0.03f * minOf(taScore, 15))
        ui?.let { it.particles.confetti(intArrayOf(colorL, colorS, Neon.ENERGY), it.halfW, it.halfH, 35) }
        taWaiting = true
        puzzle = null
        tryLoadNextPuzzle()
    }

    private fun timeUp() {
        state = State.FAILED
        panel = Panel.TIME_UP
        taWaiting = false
        val save = game.save
        taNewBest = taScore > save.timeAttackBest
        if (taNewBest) save.timeAttackBest = taScore
        taCoins = taScore * 15
        save.addCoins(taCoins)
        save.addXp(taScore * 10)
        game.play(Sfx.FAIL)
        game.pulse(60)
        ui?.shake(0.12f)
    }

    private fun undo() {
        val p = puzzle ?: return
        if (state != State.PLAYING && state != State.FAILED) return
        if (light.busy || shadow.busy) return
        if (!p.undo()) return
        game.play(Sfx.UNDO)
        combo = 0
        panel = Panel.NONE
        moveTokens()
        state = State.PLAYING
    }

    private fun restart() {
        if (mode == PlayMode.TIME_ATTACK && panel == Panel.TIME_UP) { startTimeAttack(); return }
        val p = puzzle ?: return
        if (mode == PlayMode.TIME_ATTACK) {
            p.reset(); panel = Panel.NONE; state = State.PLAYING; snapTokens(); return
        }
        load(p.level, mode)
    }

    private fun skipPuzzle() {
        if (state != State.PLAYING || taWaiting) return
        taTime = maxOf(1f, taTime - 10f)
        taPopupText = "-10s"
        taPopup = 1.2f
        game.play(Sfx.UNDO, 0.8f)
        taWaiting = true
        puzzle = null
        tryLoadNextPuzzle()
    }

    private fun next() {
        if (daily) { game.show(AppScreen.MAIN_MENU); return }
        if (game.levels.isLastLevel) { game.show(AppScreen.WORLD_SELECT); return }
        game.levels.advance()
        game.worldId = game.levels.current!!.worldId
        load(game.levels.current!!, PlayMode.LEVEL)
    }

    private fun requestHint() {
        val p = puzzle ?: return
        if (state != State.PLAYING) return
        val path = PuzzleSolver.solve(p.level, p.state)
        val save = game.save
        when {
            path.isNullOrEmpty() -> message = "No route from here. Try Undo or Restart."
            p.hasMoveLimit && path.size > p.movesLeft -> message = "Too few moves left. Try Undo or Restart."
            save.hints <= 0 && save.coins < Rewards.HINT_COST -> message = "No hints left. Earn ${Rewards.HINT_COST} coins for one more."
            save.trySpendHint() -> {
                usedHint = true
                pendingHint = path[0]
                hintCostText = if (save.hints > 0) "(${save.hints} free hints left)" else "(Next hint costs ${Rewards.HINT_COST} coins)"
                state = State.PAUSED
                panel = Panel.HINT
            }
        }
    }

    private fun pause() {
        if (state != State.PLAYING && state != State.ANIMATING) return
        if (state == State.ANIMATING) return
        state = State.PAUSED
        panel = Panel.PAUSE
    }

    private fun resume() {
        if (state != State.PAUSED) return
        panel = Panel.NONE
        state = State.PLAYING
    }

    private fun closeIntro() {
        puzzle?.let { game.save.markIntro(it.level.worldId) }
        panel = Panel.NONE
        state = State.PLAYING
        game.play(Sfx.STAR)
    }

    // ---------------- Frame ----------------

    private fun layout(ui: Ui, level: LevelData) {
        val topH = 1.85f
        val bottomH = 2.15f
        val gap = 0.8f
        val bw = level.gridWidth * CELL + 0.28f
        val bh = level.gridHeight * CELL + 0.28f
        val s: Float
        if (level.solo) {
            val avail = 2f * ui.halfH - topH - bottomH - 2.2f
            s = minOf((2f * ui.halfW - 0.35f) / bw, avail / bh, 1.75f)
            lightY = (ui.halfH - topH - ui.halfH + bottomH) / 2f + 0.6f
            shadowY = -50f
            hintY = lightY - bh * s / 2f - 0.9f
        } else {
            val per = (2f * ui.halfH - topH - bottomH - gap) / 2f
            s = minOf((2f * ui.halfW - 0.35f) / bw, per / bh, 1.75f)
            lightY = ui.halfH - topH - per / 2f
            shadowY = -ui.halfH + bottomH + per / 2f
            hintY = (lightY - per / 2f + shadowY + per / 2f) / 2f
        }
        if (s != scale) { scale = s; snapTokens() }
    }

    fun frame(ui: Ui, dt: Float) {
        this.ui = ui
        if (mode == PlayMode.TIME_ATTACK) timeAttackTick(dt)
        val p = puzzle
        if (p == null) { waitingScreen(ui); return }
        layout(ui, p.level)
        val trailL = 0.42f * scale
        val trail = game.save.trail.takeIf { game.save.ownsTrail(it) } ?: 0
        light.update(dt) { x, y -> trailFx(ui, trail, x, lightY + y, colorL, trailL) }
        shadow.update(dt) { x, y -> trailFx(ui, trail, x, shadowY + y, colorS, trailL) }
        if (shadowAppear < 1f) shadowAppear = minOf(1f, shadowAppear + dt / 0.9f).also { if (it >= 1f) { game.play(Sfx.PORTAL); ui.particles.ring(0f, shadowY, colorS, 6f * scale, 0.6f) } }
        light.teleportedAt?.let { portalFx(ui, it, lightY, p.lightBoard, p.level.light); light.teleportedAt = null }
        shadow.teleportedAt?.let { portalFx(ui, it, shadowY, p.shadowBoard, p.level.shadow); shadow.teleportedAt = null }
        if (animLeft > 0f) {
            animLeft -= dt
            if (animLeft <= 0f) pendingOutcome?.let { pendingOutcome = null; moveFinished(it) }
        }
        if (celebrateLeft > 0f) {
            celebrateLeft -= dt
            if (celebrateLeft <= 0f) win()
        }
        val cur = puzzle ?: run { waitingScreen(ui); return }
        drawHud(ui, cur)
        when (panel) {
            Panel.PAUSE -> pausePanel(ui)
            Panel.VICTORY -> victoryPanel(ui, cur, dt)
            Panel.FAILURE -> failurePanel(ui, cur)
            Panel.HINT -> hintPanel(ui)
            Panel.INTRO -> introPanel(ui, cur.level.worldId)
            Panel.TIME_UP -> timeUpPanel(ui)
            Panel.CHEST -> chestPanel(ui, dt)
            Panel.NONE -> {}
        }
    }

    private fun trailFx(ui: Ui, kind: Int, x: Float, y: Float, color: Int, size: Float) {
        when (kind) {
            1 -> { ui.particles.dot(x, y, Neon.a(color, 0.7f), size * 0.8f, 0.2f); if (Math.random() < 0.5) ui.particles.burst(x, y, color, 1, 1.5f, 0.07f, 0.35f) }
            2 -> ui.particles.dot(x, y, android.graphics.Color.HSVToColor(floatArrayOf((ui.time * 360f) % 360f, 0.8f, 1f)), size, 0.3f)
            3 -> { ui.particles.dot(x, y, Neon.a(color, 0.6f), size * 0.7f, 0.2f); if (Math.random() < 0.35) ui.particles.burst(x, y, Neon.ENERGY, 1, 1.2f, 0.1f, 0.5f) }
            else -> ui.particles.dot(x, y, Neon.a(color, 0.8f), size, 0.22f)
        }
    }

    private fun portalFx(ui: Ui, end: Pair<Float, Float>, by: Float, board: BoardState, data: BoardData) {
        val c = 0xFFFF8A3D.toInt()
        ui.particles.ring(end.first, by + end.second, c, 2f * scale, 0.5f)
        ui.particles.burst(end.first, by + end.second, c, 14, 3.5f, 0.12f, 0.5f)
        // Ring at the portal the token came from too.
        val here = data.portals.firstOrNull { (a, b) -> cellOffset(board, b, scale) == end || cellOffset(board, a, scale) == end }
        here?.let { (a, b) ->
            val from = if (cellOffset(board, b, scale) == end) a else b
            cellOffset(board, from, scale).let { ui.particles.ring(it.first, by + it.second, c, 2f * scale, 0.5f) }
        }
        game.play(Sfx.PORTAL)
        game.pulse(20)
    }

    private fun timeAttackTick(dt: Float) {
        if (taCountdown > 0f) taCountdown -= dt
        if (taPopup > 0f) taPopup -= dt
        if (taWaiting) { tryLoadNextPuzzle(); return }
        if (panel == Panel.NONE && (state == State.PLAYING || state == State.ANIMATING)) {
            taTime -= dt
            if (taTime <= 0f) { taTime = 0f; timeUp() }
        }
    }

    private fun waitingScreen(ui: Ui) {
        ui.background(colorL, colorS)
        if (panel == Panel.TIME_UP) { timeUpPanel(ui); return }
        timeAttackTop(ui)
        val k = 1f + 0.08f * sin(ui.time * 6f)
        if (taCountdown > 0f) {
            ui.text(if (taCountdown > 1.1f) "READY?" else "GO!", 0f, 0.6f, 1.3f * k, Neon.ENERGY, logo = true)
            ui.text("Solve as many puzzles as you can!", 0f, -0.9f, 0.36f, Neon.TEXT)
        } else {
            ui.text("Next puzzle...", 0f, 0f, 0.5f * k, Neon.TEXT)
        }
        if (taPopup > 0f) ui.text(taPopupText, 0f, 2.4f + (1.2f - taPopup), 0.9f, Neon.a(if (taPopupText.startsWith("-")) Neon.BLOCKED else Neon.SUCCESS, taPopup.coerceAtMost(1f)), logo = true)
    }

    private fun timeAttackTop(ui: Ui) {
        val top = ui.halfH
        ui.button("←", -ui.halfW + 0.72f, top - 0.8f, 0.9f, 0.9f, colorL, Style.ROUND) { game.show(AppScreen.TIME_ATTACK) }
        ui.card(0f, top - 0.9f, 4.8f, 1.5f, Neon.ENERGY, 0.9f)
        ui.text("TIME ATTACK  ·  Score $taScore", 0f, top - 0.58f, 0.38f, Neon.TEXT)
        val low = taTime < 10f
        val beat = if (low) 1f + 0.12f * sin(ui.time * 12f) else 1f
        ui.text("${taTime.toInt()}s", 0f, top - 1.18f, 0.6f * beat, if (low) Neon.BLOCKED else Neon.ENERGY, logo = true)
    }

    private fun drawHud(ui: Ui, p: PuzzleState) {
        ui.background(colorL, colorS)
        val top = ui.halfH
        val bottom = -ui.halfH
        val level = p.level

        // Top bar.
        if (mode == PlayMode.TIME_ATTACK) {
            timeAttackTop(ui)
        } else {
            ui.button("←", -ui.halfW + 0.72f, top - 0.8f, 0.9f, 0.9f, colorL, Style.ROUND) { exitToList() }
            ui.card(0f, top - 0.9f, 4.8f, 1.5f, colorL, 0.9f)
            ui.text(if (daily) "Daily Challenge" else "Level ${level.worldId}-${level.levelId}", 0f, top - 0.58f, 0.44f, Neon.TEXT)
            ui.stars(-1.15f, top - 1.22f, 0.34f, if (p.moveCount == 0) 3 else StarManager.calculate(level, p.moveCount))
            val max = p.maxMoves
            val movesColor = if (max > 0 && max - p.moveCount <= 2) Neon.ENERGY else Neon.TEXT
            ui.text(if (max > 0) "${p.moveCount} / $max" else "${p.moveCount} moves", 1.05f, top - 1.22f, 0.36f, movesColor)
        }
        ui.button("II", ui.halfW - 0.72f, top - 0.8f, 0.9f, 0.9f, colorL, Style.ROUND, labelSize = 0.38f) { pause() }

        // Boards and tokens.
        drawBoard(ui, p.lightBoard, colorL, 0f, lightY, scale, p, true)
        val e = 1f - (1f - shadowAppear) * (1f - shadowAppear) * (1f - shadowAppear)
        val sy = shadowY - (1f - e) * (ui.halfH + 6f)
        if (!p.level.solo) drawBoard(ui, p.shadowBoard, colorS, 0f, sy, scale, p, false)
        light.draw(ui, 0f, lightY)
        if (!p.level.solo) shadow.draw(ui, 0f, sy)

        if (shadowAppear >= 1f) arrow?.let { d ->
            val k = (ui.time * 0.8f) % 1f
            val s = k * k * (3 - 2 * k)
            val off = (-0.8f + 1.6f * s) * scale
            val alpha = sin(k * PI).toFloat() * 0.9f
            ui.arrow(d.dx * off, lightY - d.dy * off, 1.6f * scale, angleOf(d), Neon.a(colorL, alpha))
            val o = d.opposite()
            if (!p.level.solo) ui.arrow(o.dx * off, shadowY - o.dy * off, 1.6f * scale, angleOf(o), Neon.a(colorS, alpha))
        }
        // Ghost finger showing the swipe on guided levels.
        val g = guide
        if (g != null && shadowAppear >= 1f && state == State.PLAYING && panel == Panel.NONE) {
            val k = (ui.time * 0.9f) % 1f
            val travel = 2.2f * minOf(scale, 1.3f)
            val s0 = (k / 0.7f).coerceIn(0f, 1f)
            val ease = s0 * s0 * (3 - 2 * s0)
            val a = if (k < 0.7f) 1f else (1f - (k - 0.7f) / 0.3f)
            val fx0 = -g.dx * travel / 2f
            val fy0 = lightY + g.dy * travel / 2f
            val fx = fx0 + g.dx * travel * ease
            val fy = fy0 - g.dy * travel * ease
            for (i in 1..6) {
                val t = (ease - i * 0.05f).coerceAtLeast(0f)
                ui.circle(fx0 + g.dx * travel * t, fy0 - g.dy * travel * t, 0.5f - i * 0.05f, Neon.a(0xFFFFFFFF.toInt(), 0.12f * a))
            }
            ui.glow(fx, fy, 1.6f, 1.6f, Neon.a(0xFFFFFFFF.toInt(), 0.35f * a))
            ui.circle(fx, fy, 0.75f, Neon.a(0xFFFFFFFF.toInt(), 0.9f * a))
            ui.ring(fx, fy, 1.05f, 1.05f, Neon.a(0xFFFFFFFF.toInt(), 0.5f * a), 0.06f)
        }

        val hint = message ?: level.hint.ifEmpty { if (mode == PlayMode.TIME_ATTACK) "Quick! Light and Shadow to their exits." else "" }
        if (nearMiss && message == null) {
            val k = 1f + 0.08f * sin(ui.time * 8f)
            ui.glow(0f, hintY, 6f, 1.4f, Neon.a(Neon.ENERGY, 0.35f))
            ui.text("SO CLOSE! 1 move to win!", 0f, hintY, 0.42f * k, Neon.ENERGY, logo = true)
        } else if (level.tutorial.isNotEmpty() || level.solo) {
            val k = 1f + 0.04f * sin(ui.time * 4f)
            ui.text(wrap(hint, 30), 0f, hintY, 0.5f * k, Neon.TEXT, logo = true)
        } else ui.text(wrap(hint, 42), 0f, hintY, 0.27f, Neon.a(Neon.TEXT, 0.8f))
        if (combo >= 3 && ui.time - lastMoveAt < 0.9f) {
            val a = (1f - (ui.time - lastMoveAt) / 0.9f).coerceIn(0f, 1f)
            ui.text("COMBO x${combo + 1}", ui.halfW - 1.4f, hintY, 0.3f, Neon.a(Neon.ENERGY, a), logo = true)
        }
        if (mode == PlayMode.TIME_ATTACK && taPopup > 0f) {
            ui.text(taPopupText, 0f, top - 2.4f - (1.2f - taPopup) * 0.6f, 0.8f, Neon.a(if (taPopupText.startsWith("-")) Neon.BLOCKED else Neon.SUCCESS, taPopup.coerceAtMost(1f)), logo = true)
        }

        // Bottom controls.
        val by = bottom + 1.3f
        ui.button("↺", -2.6f, by, 1.0f, 1.0f, colorL, Style.ROUND, enabled = p.canUndo, caption = "Undo") { undo() }
        if (mode == PlayMode.TIME_ATTACK) {
            ui.button("»", 0f, by, 1.0f, 1.0f, Neon.ENERGY, Style.ROUND, caption = "Skip -10s") { skipPuzzle() }
        } else {
            ui.button("?", 0f, by, 1.0f, 1.0f, Neon.ENERGY, Style.ROUND, caption = "Hint") { requestHint() }
            ui.circle(0.48f, by + 0.43f, 0.4f, Neon.ENERGY)
            ui.text(if (game.save.hints > 0) game.save.hints.toString() else "$", 0.48f, by + 0.43f, 0.26f, Neon.BG)
        }
        ui.button("↻", 2.6f, by, 1.0f, 1.0f, colorS, Style.ROUND, caption = "Restart") { restart() }
    }

    private fun pausePanel(ui: Ui) {
        ui.dim()
        ui.card(0f, 0.2f, 7.4f, 10.6f, colorS)
        ui.card(0f, -0.1f, 6.2f, 8.6f, colorL, 0.95f)
        ui.rrect(-1.4f, 5.35f, 3.8f, 1.1f, colorS)
        ui.rrect(-1.4f, 5.35f, 3.7f, 1.0f, Neon.PANEL)
        ui.text("PAUSE", -1.4f, 5.35f, 0.7f, colorL)
        ui.button("×", 2.95f, 5.35f, 1.1f, 1.1f, colorL, labelSize = 0.7f) { resume() }
        val labels = listOf("CONTINUE", "REPLAY", if (mode == PlayMode.LEVEL) "LEVEL SELECT" else "QUIT", "MAIN MENU")
        val actions = listOf({ resume() }, { restart(); resume() }, { exitToList() }, { game.show(AppScreen.MAIN_MENU) })
        for (i in labels.indices) ui.button(labels[i], 0f, 2.9f - i * 1.4f, 4.6f, 1.05f, Neon.PINK, onClick = actions[i])
        ui.glow(0f, -2.75f, 4.5f, 0.15f, colorL)
        val save = game.save
        ui.slider("Music", 0.8f, -3.5f, 3.4f, save.musicVolume, colorL) { save.musicVolume = it; save.music = it > 0.01f; game.sound?.applySettings() }
        ui.slider("Sound", 0.8f, -4.35f, 3.4f, save.soundVolume, colorL) { save.soundVolume = it; save.sound = it > 0.01f }
    }

    private fun easeOutBack(t: Float): Float {
        val c = 1.70158f
        val x = t.coerceIn(0f, 1f) - 1f
        return 1f + (c + 1f) * x * x * x + c * x * x
    }

    private fun victoryPanel(ui: Ui, p: PuzzleState, dt: Float) {
        vTime += dt
        ui.dim()
        ui.card(0f, -0.5f, 7.6f, 10.6f, colorS)
        for (i in 0 until 3) {
            val x = (i - 1) * 1.7f
            val y = if (i == 1) 4.75f else 4.4f
            val size = if (i == 1) 1.8f else 1.45f
            val appear = 0.12f + i * 0.2f
            ui.star(x, y, size, Neon.a(Neon.TEXT, 0.15f))
            if (i < vStars && vTime >= appear) {
                if (vStarsShown <= i) {
                    vStarsShown = i + 1
                    game.play(Sfx.STAR, 1f + 0.15f * i)
                    game.pulse(20)
                    ui.particles.burst(x, y, Neon.ENERGY, 20, 4f, 0.13f, 0.6f)
                    ui.particles.ring(x, y, Neon.ENERGY, 2.6f, 0.5f)
                }
                val k = easeOutBack((vTime - appear) / 0.25f)
                ui.glow(x, y, 2.4f * k, 2.4f * k, Neon.a(Neon.ENERGY, 0.5f))
                ui.star(x, y, size * k, Neon.ENERGY)
            }
        }
        ui.rrect(0f, 3.05f, 6.8f, 1.1f, Neon.ORANGE)
        ui.text(if (daily) "Daily Complete!" else if (vLast) "World Clear!" else listOf("Level Complete!", "Awesome!", "Brilliant!", "Great job!")[(p.level.levelId + p.level.worldId) % 4], 0f, 3.05f, 0.58f, Neon.TEXT)
        val best = p.level.starThresholds.threeStarsMaxMoves
        ui.text("${p.moveCount} moves  ·  best $best", 0f, 2.1f, 0.38f, Neon.a(Neon.TEXT, 0.85f))
        if (p.moveCount <= best && vTime > 0.7f) ui.text("PERFECT!", 2.75f, 2.1f, 0.3f, Neon.a(Neon.ENERGY, 0.7f + 0.3f * sin(ui.time * 6f)), logo = true)

        // Coins count up.
        val coinStart = 0.7f
        val t = ((vTime - coinStart) / 0.55f).coerceIn(0f, 1f)
        val shown = (vCoins * t).toInt()
        if (t in 0.001f..0.999f && vCoins > 0) {
            vCoinTick -= dt
            if (vCoinTick <= 0f) { vCoinTick = 0.06f; game.play(Sfx.COIN, 0.9f + 0.4f * t) }
        }
        val cx = if (vMult > 1 || vStreak >= 2) -1.4f else 0f
        ui.card(cx, 1.0f, 3.4f, 1.1f, Neon.BLUE, 0.9f)
        val pop = if (t in 0.01f..0.99f) 1f + 0.08f * sin(ui.time * 30f) else 1f
        ui.circle(cx - 0.85f, 1.0f, 0.6f * pop, Neon.ENERGY)
        ui.ring(cx - 0.85f, 1.0f, 0.4f * pop, 0.4f * pop, Neon.a(Neon.BG, 0.35f), 0.06f)
        ui.text("+$shown", cx - 0.45f, 1.0f, 0.48f, Neon.TEXT, Align.LEFT)
        if (vMult > 1 || vStreak >= 2) {
            val hot = if (vMult > 1) Neon.ORANGE else Neon.PINK
            val k = 1f + 0.05f * sin(ui.time * 7f)
            ui.card(1.85f, 1.0f, 2.9f, 1.1f, hot, 0.9f)
            ui.text("STREAK $vStreak", 1.85f, 1.2f, 0.3f * k, hot, logo = true)
            ui.text(if (vMult > 1) "x$vMult coins!" else "3 = x2 coins", 1.85f, 0.78f, 0.26f, Neon.TEXT)
        }

        // XP bar that fills up, with a level-up burst.
        val xt = ((vTime - 1.0f) / 0.7f).coerceIn(0f, 1f)
        val xpNow = vXpFrom + ((vXpTo - vXpFrom) * xt).toInt()
        val (lv, into, need) = Rewards.levelInfo(xpNow)
        if (lv > vLevelShown) {
            vLevelShown = lv
            vLevelUpAt = ui.time
            vLevelUpText = Unlocks.at(lv).joinToString("  +  ") { it.label }.ifEmpty { "+${Rewards.LEVEL_UP_COINS} coins  +1 hint" }
            game.play(Sfx.WIN, 1.15f)
            game.pulse(60)
            ui.shake(0.1f)
            ui.particles.confetti(intArrayOf(Neon.ENERGY, colorL, colorS, Neon.SUCCESS), ui.halfW, ui.halfH, 80)
            ui.particles.burst(0f, -0.2f, Neon.SUCCESS, 30, 5f, 0.15f, 0.8f)
        }
        ui.text("Lv $lv", -3.2f, -0.2f, 0.36f, Neon.SUCCESS, Align.LEFT, logo = true)
        val bw = 4.4f
        val bx = -2.1f
        ui.rrect(bx + bw / 2, -0.2f, bw, 0.3f, Neon.a(Neon.TEXT, 0.12f), 0.15f)
        val f = (into.toFloat() / need).coerceIn(0f, 1f)
        if (f > 0.01f) ui.rrect(bx + bw * f / 2, -0.2f, bw * f, 0.3f, Neon.SUCCESS, 0.15f)
        ui.text("+$vXpGain XP", 3.3f, -0.2f, 0.28f, Neon.SUCCESS, Align.RIGHT)
        Unlocks.next(lv)?.let { ui.text("Lv ${it.level}: ${it.label}", 0.1f, -0.72f, 0.26f, Neon.a(Neon.TEXT, 0.6f)) }

        // Reward chest.
        if (vChestReady) {
            val k = 1f + 0.06f * sin(ui.time * 6f)
            ui.glow(0f, -1.75f, 5.5f * k, 1.8f * k, Neon.a(Neon.ENERGY, 0.45f))
            ui.button("OPEN CHEST!", 0.35f, -1.75f, 5.2f, 1.05f, Neon.ENERGY, Style.FILLED) { chestT = 0f; chestOpened = false; panel = Panel.CHEST }
            drawChest(ui, -2.0f, -1.75f, 0.75f * k, false)
        } else if (!daily) {
            val prog = game.save.chestProgress
            drawChest(ui, -1.6f, -1.75f, 0.6f, false)
            for (i in 0 until Rewards.CHEST_EVERY) ui.circle(-0.6f + i * 0.55f, -1.75f, 0.32f, if (i < prog) Neon.ENERGY else Neon.a(Neon.TEXT, 0.15f))
            ui.text("Chest", 1.5f, -1.75f, 0.3f, Neon.a(Neon.TEXT, 0.7f))
        }

        // Next level, auto after a short moment.
        val auto = mode == PlayMode.LEVEL && !vLast && !vChestReady
        if (auto && vTime > 1.9f) {
            vAuto += dt
            if (vAuto >= 2.0f) { next(); return }
        }
        ui.button(if (daily || vLast) "Continue" else "Next Level  ▸", 0f, -3.05f, 5.0f, 1.15f, Neon.SUCCESS, Style.FILLED) { next() }
        if (auto && vAuto > 0f) { val w = 4.8f * (vAuto / 2.0f).coerceIn(0f, 1f); ui.rrect(-2.4f + w / 2, -3.55f, w, 0.1f, Neon.TEXT, 0.05f) }
        ui.button("Replay", -1.3f, -4.4f, 2.4f, 0.95f, colorS) { restart() }
        ui.button("Home", 1.3f, -4.4f, 2.4f, 0.95f, Neon.BLUE, Style.FILLED) { game.show(AppScreen.MAIN_MENU) }

        val age = ui.time - vLevelUpAt
        if (age < 2.6f) {
            val k = easeOutBack(age / 0.35f)
            ui.glow(0f, 6.6f, 8f, 3f, Neon.a(Neon.SUCCESS, 0.4f))
            ui.text("LEVEL UP!  Lv $vLevelShown", 0f, 6.85f, 0.8f * k, Neon.SUCCESS, logo = true)
            ui.text(vLevelUpText, 0f, 6.05f, 0.34f, Neon.TEXT)
        }
    }

    private fun drawChest(ui: Ui, x: Float, y: Float, s: Float, open: Boolean) {
        val wood = 0xFF8A4B1F.toInt()
        val gold = Neon.ENERGY
        ui.rrect(x, y - 0.12f * s, 1.5f * s, 0.95f * s, gold, 0.12f * s)
        ui.rrect(x, y - 0.12f * s, 1.36f * s, 0.81f * s, wood, 0.1f * s)
        val ly = if (open) y + 0.75f * s else y + 0.45f * s
        ui.rrect(x, ly, 1.56f * s, 0.5f * s, gold, 0.18f * s)
        ui.rrect(x, ly, 1.42f * s, 0.36f * s, wood, 0.14f * s)
        ui.box(x, y - 0.12f * s, 0.16f * s, 0.95f * s, gold)
        if (!open) ui.circle(x, y + 0.2f * s, 0.32f * s, gold)
    }

    private fun chestPanel(ui: Ui, dt: Float) {
        chestT += dt
        ui.dim(0.9f)
        val openAt = 1.0f
        if (!chestOpened && chestT >= openAt) {
            chestOpened = true
            val save = game.save
            val r = Math.random()
            chestText = when {
                r < 0.15 -> { save.addCoins(300); "JACKPOT!  +300 coins" }
                r < 0.45 -> { save.addHints(2); save.addCoins(40); "+2 hints  +40 coins" }
                else -> { val c = 60 + (Math.random() * 90).toInt(); save.addCoins(c); "+$c coins" }
            }
            save.chestProgress = 0
            game.play(Sfx.WIN, 1.1f)
            game.pulse(70)
            ui.shake(0.15f)
            ui.particles.burst(0f, 1.0f, Neon.ENERGY, 50, 7f, 0.18f, 1.0f)
            ui.particles.ring(0f, 1.0f, Neon.ENERGY, 6f, 0.7f)
            ui.particles.confetti(intArrayOf(Neon.ENERGY, Neon.PINK, Neon.SUCCESS, colorL), ui.halfW, ui.halfH, 120)
        }
        if (!chestOpened) {
            val wob = sin(chestT * 40f) * 0.08f * chestT
            if ((chestT * 10).toInt() % 3 == 0) ui.particles.burst(wob, 1.0f, Neon.ENERGY, 1, 2f, 0.08f, 0.4f)
            ui.glow(0f, 1.0f, 5f + chestT * 2f, 5f + chestT * 2f, Neon.a(Neon.ENERGY, 0.3f + chestT * 0.3f))
            drawChest(ui, wob, 1.0f, 2.2f, false)
            ui.text("Opening...", 0f, -2.2f, 0.5f, Neon.TEXT)
        } else {
            ui.glow(0f, 1.0f, 8f, 8f, Neon.a(Neon.ENERGY, 0.45f + 0.1f * sin(ui.time * 4f)))
            drawChest(ui, 0f, 1.0f, 2.2f, true)
            val k = easeOutBack((chestT - openAt) / 0.35f)
            ui.text(chestText, 0f, 4.2f, 0.7f * k, Neon.ENERGY, logo = true)
            ui.button("COLLECT", 0f, -3.0f, 4.6f, 1.2f, Neon.SUCCESS, Style.FILLED) {
                vChestReady = false
                vAuto = 0f
                vTime = maxOf(vTime, 1.9f)
                panel = Panel.VICTORY
            }
        }
    }

    private fun failurePanel(ui: Ui, p: PuzzleState) {
        ui.dim()
        ui.card(0f, -0.6f, 7.2f, 9.0f, Neon.BLOCKED)
        ui.star(0f, 4.3f, 2.0f, Neon.BLOCKED)
        ui.rrect(0f, 2.7f, 6.6f, 1.25f, Neon.RED)
        ui.text("Out of Moves!", 0f, 2.7f, 0.62f, Neon.TEXT)
        ui.text("Moves:  ${p.moveCount}\nBest:  ${p.level.starThresholds.threeStarsMaxMoves}", 0f, 1.25f, 0.44f, Neon.TEXT)
        ui.button("↻  Retry", 0f, -0.35f, 5.0f, 1.15f, Neon.ENERGY, Style.FILLED) { restart() }
        ui.button("↺  Undo", 0f, -1.75f, 5.0f, 1.0f, colorL, enabled = p.canUndo) { undo() }
        if (game.save.unlocked(Unlocks.RELAX_LEVEL) && !game.save.relaxed) {
            ui.button("Relax mode: no move limit", 0f, -3.0f, 6.2f, 1.0f, Neon.SUCCESS) { game.save.relaxed = true; restart() }
        } else ui.button("Level Select", 0f, -3.0f, 5.0f, 1.0f, colorL) { exitToList() }
        ui.button("Home", 0f, -4.25f, 5.0f, 1.0f, colorL) { game.show(AppScreen.MAIN_MENU) }
    }

    private fun timeUpPanel(ui: Ui) {
        ui.dim()
        ui.card(0f, -0.4f, 7.2f, 9.6f, Neon.ENERGY)
        ui.text("TIME UP!", 0f, 3.9f, 1.0f, Neon.ENERGY, logo = true)
        ui.text("Puzzles solved", 0f, 2.6f, 0.4f, Neon.a(Neon.TEXT, 0.75f))
        ui.text(taScore.toString(), 0f, 1.6f, 1.3f, Neon.TEXT, logo = true)
        if (taNewBest && taScore > 0) ui.text("NEW BEST!", 0f, 0.45f, 0.5f, Neon.a(Neon.SUCCESS, 0.7f + 0.3f * sin(ui.time * 6f)), logo = true)
        else ui.text("Best: ${game.save.timeAttackBest}", 0f, 0.45f, 0.42f, Neon.TEXT)
        ui.coins(-0.6f, -0.5f, 0.45f, taCoins)
        ui.button("Play Again", 0f, -1.9f, 5.0f, 1.15f, Neon.SUCCESS, Style.FILLED) { startTimeAttack() }
        ui.button("Home", 0f, -3.35f, 5.0f, 1.0f, colorL) { game.show(AppScreen.MAIN_MENU) }
    }

    private fun hintPanel(ui: Ui) {
        ui.dim()
        ui.card(0f, 0f, 6.8f, 5.6f, colorL)
        ui.text("Hint", 0f, 1.9f, 0.62f, Neon.TEXT)
        ui.text("Try swiping ${pendingHint.name}!", 0f, 0.7f, 0.46f, colorL)
        ui.button("OK", 0f, -0.8f, 4.0f, 1.15f, Neon.BLUE, Style.FILLED) {
            panel = Panel.NONE
            arrow = pendingHint
            state = State.PLAYING
        }
        ui.text(hintCostText, 0f, -1.95f, 0.32f, Neon.ENERGY)
    }

    private class Intro(val title: String, val body: String, val demo: LevelData, val color: Int)

    private val intros: Map<Int, Intro> by lazy {
        fun g(x: Int) = GridPos(x, 0)
        fun demo(b: BoardData) = LevelData(gridWidth = 5, gridHeight = 1, light = b, shadow = b)
        mapOf(
            2 to Intro("ENERGY CORES", "Collect every core on a board.\nIts exit stays locked until then.",
                demo(BoardData(g(0), g(4), cores = listOf(g(2)))), Neon.ENERGY),
            3 to Intro("PORTALS", "Step into a portal to jump\nto its twin on the same board.",
                demo(BoardData(g(0), g(4), portals = listOf(g(1) to g(3)))), Neon.WORLD_ORANGE),
            4 to Intro("SWITCH & GATES", "Step on a switch to open or close\nALL gates, on both boards.",
                demo(BoardData(g(0), g(4), switches = listOf(g(1)), gates = listOf(g(3)))), Neon.PINK),
            5 to Intro("CRUMBLE TILES", "Cracked tiles break after you leave.\nEach one can be crossed only once!",
                demo(BoardData(g(0), g(4), crumble = listOf(g(1), g(2), g(3)))), 0xFFFFB13B.toInt())
        )
    }

    private fun introPanel(ui: Ui, world: Int) {
        val intro = intros[world] ?: run { closeIntro(); return }
        ui.dim(0.9f)
        ui.card(0f, 0f, 7.8f, 9.4f, intro.color)
        ui.text("NEW MECHANIC", 0f, 3.85f, 0.36f, Neon.a(Neon.TEXT, 0.7f))
        val k = 1f + 0.04f * sin(ui.time * 3f)
        ui.text(intro.title, 0f, 3.0f, 0.8f * k, intro.color, logo = true)
        val b = BoardState(5, 1, intro.demo.light)
        drawBoard(ui, b, colorL, 0f, 1.2f, 1.15f)
        // Small looping demo: the orb walks along the row.
        val step = ((ui.time * 1.3f) % 6f).toInt().coerceAtMost(4)
        val (ox, oy) = cellOffset(b, GridPos(step, 0), 1.15f)
        ui.character(ox, 1.2f + oy, colorL, 0.62f)
        ui.text(intro.body, 0f, -0.75f, 0.4f, Neon.TEXT)
        ui.button("GOT IT!", 0f, -3.0f, 4.6f, 1.2f, intro.color, Style.FILLED) { closeIntro() }
    }

    private fun wrap(s: String, max: Int): String {
        if (s.length <= max || s.contains('\n')) return s
        val words = s.split(' ')
        val out = StringBuilder()
        var line = 0
        for (w in words) {
            if (line > 0 && line + 1 + w.length > max) { out.append('\n'); line = 0 }
            else if (line > 0) { out.append(' '); line++ }
            out.append(w); line += w.length
        }
        return out.toString()
    }
}
