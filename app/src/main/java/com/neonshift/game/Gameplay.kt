package com.neonshift.game

import com.neonshift.game.core.*
import com.neonshift.game.ui.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Gameplay screen: two boards, two tokens, HUD and the Pause / Level Complete / Level Failed / Hint panels.
 * Input is locked while tokens animate, so fast swipes never queue extra moves.
 */
class Gameplay(private val game: Game) {
    private enum class Panel { NONE, PAUSE, VICTORY, FAILURE, HINT }
    private enum class State { PLAYING, ANIMATING, PAUSED, VICTORY, FAILED }

    private val lightY = 3.0f
    private val shadowY = -3.55f
    private val moveDuration = 0.13f
    private val bumpDuration = 0.16f

    private var puzzle: PuzzleState? = null
    private var daily = false
    private var usedHint = false
    private var state = State.PLAYING
    private var panel = Panel.NONE
    private val light = Token(Neon.LIGHT)
    private val shadow = Token(Neon.SHADOW)
    private var scale = 1f
    private var message: String? = null
    private var arrow: Direction? = null
    private var pendingOutcome: MoveOutcome? = null
    private var animLeft = 0f
    private var celebrateLeft = 0f

    // Victory panel data.
    private var vStars = 0
    private var vCoins = 0
    private var vLast = false
    private var pendingHint = Direction.UP
    private var hintCostText = ""

    fun load(level: LevelData, daily: Boolean) {
        val p = PuzzleState(level)
        puzzle = p
        this.daily = daily
        usedHint = false
        scale = minOf(1f, 5f / maxOf(level.gridWidth, level.gridHeight))
        panel = Panel.NONE
        message = null
        arrow = null
        animLeft = 0f
        celebrateLeft = 0f
        pendingOutcome = null
        snapTokens()
        if (!daily && !game.save.tutorialDone && game.levels.currentIndex == 0) arrow = PuzzleSolver.solve(level)?.firstOrNull()
        state = State.PLAYING
    }

    private fun snapTokens() {
        val p = puzzle ?: return
        cellOffset(p.lightBoard, p.lightPos, scale).let { light.snap(it.first, it.second) }
        cellOffset(p.shadowBoard, p.shadowPos, scale).let { shadow.snap(it.first, it.second) }
    }

    private fun moveTokens() {
        val p = puzzle ?: return
        cellOffset(p.lightBoard, p.lightPos, scale).let { light.moveTo(it.first, it.second, moveDuration) }
        cellOffset(p.shadowBoard, p.shadowPos, scale).let { shadow.moveTo(it.first, it.second, moveDuration) }
    }

    fun back() {
        when (state) {
            State.PAUSED -> resume()
            State.PLAYING -> pause()
            else -> exitToList()
        }
    }

    private fun exitToList() = game.show(if (daily) AppScreen.DAILY else AppScreen.LEVEL_SELECT)

    fun swipe(d: Direction) {
        val p = puzzle ?: return
        if (state != State.PLAYING || panel != Panel.NONE) return
        val o = p.tryMove(d)
        if (o.result == MoveResult.ALREADY_SOLVED || o.result == MoveResult.OUT_OF_MOVES) return
        state = State.ANIMATING
        if (o.result == MoveResult.MOVED) {
            moveTokens()
            animLeft = moveDuration
        } else {
            if (o.lightBlocked) light.bump(d, bumpDuration)
            if (o.shadowBlocked) shadow.bump(d.opposite(), bumpDuration)
            animLeft = bumpDuration
        }
        pendingOutcome = o
    }

    private fun moveFinished(o: MoveOutcome) {
        if (o.result == MoveResult.MOVED) {
            game.play(Sfx.MOVE)
            arrow = null
            message = null
            if (!game.save.tutorialDone) game.save.tutorialDone = true
        } else {
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
            }
            o.failedNow -> {
                state = State.FAILED
                game.play(Sfx.FAIL)
                game.pulse(60)
                panel = Panel.FAILURE
            }
            else -> state = State.PLAYING
        }
    }

    private fun win() {
        val p = puzzle ?: return
        val level = p.level
        val stars = StarManager.calculate(level, p.moveCount)
        val save = game.save
        val coins: Int
        if (daily) {
            coins = save.completeDaily(game.today)
        } else {
            val previous = save.stars(level)
            coins = Rewards.levelCoins(previous, stars)
            save.recordStars(level, stars)
            if (!usedHint) save.markNoHint(level)
            save.addCoins(coins)
            game.levels.markCurrentCompleted()
        }
        vStars = stars
        vCoins = coins
        vLast = !daily && game.levels.isLastLevel
        panel = Panel.VICTORY
    }

    private fun undo() {
        val p = puzzle ?: return
        if (state != State.PLAYING && state != State.FAILED) return
        if (light.busy || shadow.busy) return
        if (!p.undo()) return
        game.play(Sfx.UNDO)
        panel = Panel.NONE
        moveTokens()
        state = State.PLAYING
    }

    private fun restart() {
        val p = puzzle ?: return
        load(p.level, daily)
    }

    private fun next() {
        if (daily) { game.show(AppScreen.MAIN_MENU); return }
        if (game.levels.isLastLevel) { game.show(AppScreen.WORLD_SELECT); return }
        game.levels.advance()
        game.worldId = game.levels.current!!.worldId
        load(game.levels.current!!, false)
    }

    private fun requestHint() {
        val p = puzzle ?: return
        if (state != State.PLAYING) return
        val path = PuzzleSolver.solve(p.level, p.lightPos, p.shadowPos)
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
        if (state != State.PLAYING) return
        state = State.PAUSED
        panel = Panel.PAUSE
    }

    private fun resume() {
        if (state != State.PAUSED) return
        panel = Panel.NONE
        state = State.PLAYING
    }

    // ---------------- Frame ----------------

    fun frame(ui: Ui, dt: Float) {
        val p = puzzle ?: return
        light.update(dt)
        shadow.update(dt)
        if (animLeft > 0f) {
            animLeft -= dt
            if (animLeft <= 0f) pendingOutcome?.let { pendingOutcome = null; moveFinished(it) }
        }
        if (celebrateLeft > 0f) {
            celebrateLeft -= dt
            if (celebrateLeft <= 0f) win()
        }
        drawHud(ui, p)
        when (panel) {
            Panel.PAUSE -> pausePanel(ui)
            Panel.VICTORY -> victoryPanel(ui, p)
            Panel.FAILURE -> failurePanel(ui, p)
            Panel.HINT -> hintPanel(ui)
            Panel.NONE -> {}
        }
    }

    private fun drawHud(ui: Ui, p: PuzzleState) {
        ui.glow(0f, lightY, 11f, 8f, Neon.a(Neon.LIGHT, 0.07f))
        ui.glow(0f, shadowY, 11f, 8f, Neon.a(Neon.SHADOW, 0.07f))

        // Top bar.
        ui.button("←", -3.75f, 7.45f, 0.95f, 0.95f, Neon.LIGHT, Style.ROUND) { exitToList() }
        ui.button("II", 3.75f, 7.45f, 0.95f, 0.95f, Neon.LIGHT, Style.ROUND, labelSize = 0.42f) { pause() }
        ui.card(0f, 7.25f, 4.2f, 1.5f, Neon.LIGHT, 0.9f)
        val level = p.level
        ui.text(if (daily) "Daily Challenge" else "Level ${level.worldId}-${level.levelId}", 0f, 7.6f, 0.48f, Neon.TEXT)
        ui.stars(0f, 7.0f, 0.4f, if (p.moveCount == 0) 3 else StarManager.calculate(level, p.moveCount))
        val max = p.maxMoves
        val movesColor = if (max > 0 && max - p.moveCount <= 2) Neon.ENERGY else Neon.TEXT
        ui.text(if (max > 0) "Moves: ${p.moveCount} / $max" else "Moves: ${p.moveCount}", 0f, 6.2f, 0.4f, movesColor)

        // Boards and tokens.
        drawBoard(ui, p.lightBoard, Neon.LIGHT, 0f, lightY, scale)
        drawBoard(ui, p.shadowBoard, Neon.SHADOW, 0f, shadowY, scale)
        light.draw(ui, 0f, lightY)
        shadow.draw(ui, 0f, shadowY)

        arrow?.let { d ->
            val k = (ui.time * 0.8f) % 1f
            val s = k * k * (3 - 2 * k)
            val off = -0.8f + 1.6f * s
            val alpha = sin(k * PI).toFloat() * 0.9f
            ui.arrow(d.dx * off, lightY - d.dy * off, 1.6f, angleOf(d), Neon.a(Neon.LIGHT, alpha))
            val o = d.opposite()
            ui.arrow(o.dx * off, shadowY - o.dy * off, 1.6f, angleOf(o), Neon.a(Neon.SHADOW, alpha))
        }

        val hint = message ?: level.hint.ifEmpty { "←  Swipe to Move  →" }
        ui.text(hint, 0f, -0.27f, 0.28f, Neon.a(Neon.TEXT, 0.8f))

        // Bottom controls.
        ui.button("↺", -2.6f, -7.25f, 1.0f, 1.0f, Neon.LIGHT, Style.ROUND, enabled = p.canUndo, caption = "Undo") { undo() }
        ui.button("?", 0f, -7.25f, 1.0f, 1.0f, Neon.ENERGY, Style.ROUND, caption = "Hint") { requestHint() }
        ui.circle(0.48f, -6.82f, 0.4f, Neon.ENERGY)
        ui.text(if (game.save.hints > 0) game.save.hints.toString() else "$", 0.48f, -6.82f, 0.26f, Neon.BG)
        ui.button("↻", 2.6f, -7.25f, 1.0f, 1.0f, Neon.SHADOW, Style.ROUND, caption = "Restart") { restart() }
    }

    private fun pausePanel(ui: Ui) {
        ui.dim()
        ui.card(0f, 0.2f, 7.4f, 10.6f, Neon.SHADOW)
        ui.card(0f, -0.1f, 6.2f, 8.6f, Neon.LIGHT, 0.95f)
        ui.rrect(-1.4f, 5.35f, 3.8f, 1.1f, Neon.SHADOW)
        ui.rrect(-1.4f, 5.35f, 3.7f, 1.0f, Neon.PANEL)
        ui.text("PAUSE", -1.4f, 5.35f, 0.7f, Neon.LIGHT)
        ui.button("×", 2.95f, 5.35f, 1.1f, 1.1f, Neon.LIGHT, labelSize = 0.7f) { resume() }
        val labels = listOf("CONTINUE", "REPLAY", "LEVEL SELECT", "MAIN MENU")
        val actions = listOf({ resume() }, { restart() }, { exitToList() }, { game.show(AppScreen.MAIN_MENU) })
        for (i in labels.indices) ui.button(labels[i], 0f, 2.9f - i * 1.4f, 4.6f, 1.05f, Neon.PINK, onClick = actions[i])
        ui.glow(0f, -2.75f, 4.5f, 0.15f, Neon.LIGHT)
        val save = game.save
        ui.slider("Music", 0.8f, -3.5f, 3.4f, save.musicVolume, Neon.LIGHT) { save.musicVolume = it; save.music = it > 0.01f; game.sound?.applySettings() }
        ui.slider("Sound", 0.8f, -4.35f, 3.4f, save.soundVolume, Neon.LIGHT) { save.soundVolume = it; save.sound = it > 0.01f }
    }

    private fun victoryPanel(ui: Ui, p: PuzzleState) {
        ui.dim()
        ui.card(0f, -0.6f, 7.2f, 9.0f, Neon.SHADOW)
        for (i in 0 until 3) {
            val x = (i - 1) * 1.7f
            val y = if (i == 1) 4.6f else 4.2f
            val on = i < vStars
            if (on) ui.glow(x, y, 2.4f, 2.4f, Neon.a(Neon.ENERGY, 0.5f))
            ui.star(x, y, if (i == 1) 1.9f else 1.5f, if (on) Neon.ENERGY else Neon.a(Neon.TEXT, 0.15f))
        }
        ui.rrect(0f, 2.7f, 6.6f, 1.25f, Neon.ORANGE)
        ui.text(if (daily) "Daily Complete!" else if (vLast) "World Clear!" else "Level Complete!", 0f, 2.7f, 0.62f, Neon.TEXT)
        ui.text("Moves:  ${p.moveCount}\nBest:  ${p.level.starThresholds.threeStarsMaxMoves}", 0f, 1.25f, 0.44f, Neon.TEXT)
        ui.card(0f, -0.35f, 3.6f, 1.2f, Neon.BLUE, 0.9f)
        ui.circle(-0.9f, -0.35f, 0.62f, Neon.ENERGY)
        ui.ring(-0.9f, -0.35f, 0.42f, 0.42f, Neon.a(Neon.BG, 0.35f), 0.06f)
        ui.text("+$vCoins", -0.48f, -0.35f, 0.5f, Neon.TEXT, Align.LEFT)
        ui.button(if (daily || vLast) "Continue" else "Next Level", 0f, -1.9f, 5.0f, 1.15f, Neon.SUCCESS, Style.FILLED) { next() }
        ui.button("Replay", -1.3f, -3.35f, 2.4f, 1.0f, Neon.SHADOW) { restart() }
        ui.button("Home", 1.3f, -3.35f, 2.4f, 1.0f, Neon.BLUE, Style.FILLED) { game.show(AppScreen.MAIN_MENU) }
    }

    private fun failurePanel(ui: Ui, p: PuzzleState) {
        ui.dim()
        ui.card(0f, -0.6f, 7.2f, 9.0f, Neon.BLOCKED)
        ui.star(0f, 4.3f, 2.0f, Neon.BLOCKED)
        ui.rrect(0f, 2.7f, 6.6f, 1.25f, Neon.RED)
        ui.text("Level Failed!", 0f, 2.7f, 0.62f, Neon.TEXT)
        ui.text("Moves:  ${p.moveCount}\nBest:  ${p.level.starThresholds.threeStarsMaxMoves}", 0f, 1.25f, 0.44f, Neon.TEXT)
        ui.button("↻  Retry", 0f, -0.35f, 5.0f, 1.15f, Neon.ENERGY, Style.FILLED) { restart() }
        ui.button("↺  Undo", 0f, -1.75f, 5.0f, 1.0f, Neon.LIGHT, enabled = p.canUndo) { undo() }
        ui.button("Level Select", 0f, -3.0f, 5.0f, 1.0f, Neon.LIGHT) { exitToList() }
        ui.button("Home", 0f, -4.25f, 5.0f, 1.0f, Neon.LIGHT) { game.show(AppScreen.MAIN_MENU) }
    }

    private fun hintPanel(ui: Ui) {
        ui.dim()
        ui.card(0f, 0f, 6.8f, 5.6f, Neon.LIGHT)
        ui.text("Hint", 0f, 1.9f, 0.62f, Neon.TEXT)
        ui.text("Try swiping ${pendingHint.name}!", 0f, 0.7f, 0.46f, Neon.LIGHT)
        ui.button("OK", 0f, -0.8f, 4.0f, 1.15f, Neon.BLUE, Style.FILLED) {
            panel = Panel.NONE
            arrow = pendingHint
            state = State.PLAYING
        }
        ui.text(hintCostText, 0f, -1.95f, 0.32f, Neon.ENERGY)
    }
}
