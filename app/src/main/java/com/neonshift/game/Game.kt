package com.neonshift.game

import com.neonshift.game.core.*
import com.neonshift.game.ui.*
import java.time.LocalDate
import kotlin.math.sin

enum class AppScreen { SPLASH, MAIN_MENU, WORLD_SELECT, LEVEL_SELECT, GAMEPLAY, DAILY, ACHIEVEMENTS, SETTINGS, PROFILE, SHOP, TIME_ATTACK }

/** App state and navigation. Each screen draws itself every frame (immediate mode) with [Ui]. */
class Game(
    val save: SaveData,
    val levels: LevelManager,
    val sound: Sound?,
    val haptics: Haptics?,
    val versionName: String,
    val openUrl: (String) -> Unit,
    val exitApp: () -> Unit
) {
    var screen = AppScreen.SPLASH; private set
    private var fade = 0f
    private var screenTime = 0f
    var worldId = 1
    val gameplay = Gameplay(this)
    private var ui: Ui? = null

    val today: Int get() { val d = LocalDate.now(); return DailyChallenge.dayNumber(d.year, d.monthValue, d.dayOfMonth) }
    val dailyDoneToday get() = save.dailyLastDay == today
    private var dailyCache: LevelData? = null
    private var dailyCacheDay = 0
    val dailyLevel: LevelData
        get() {
            if (dailyCache == null || dailyCacheDay != today) { dailyCacheDay = today; dailyCache = DailyChallenge.generate(today) }
            return dailyCache!!
        }

    // Daily login reward popup.
    private var loginOpen = false
    private var loginChecked = false
    private var loginClaimed = 0
    private var shopMessage = ""
    private var shopMessageAt = -10f

    val skin get() = SKINS[save.skin.coerceIn(0, SKINS.size - 1)]

    fun play(s: Sfx, rate: Float = 1f) = sound?.play(s, rate)
    fun pulse(ms: Long) = haptics?.pulse(ms)

    fun show(s: AppScreen) {
        if (s != AppScreen.GAMEPLAY) ui?.particles?.clear()
        screen = s
        screenTime = 0f
        if (s != AppScreen.SPLASH) fade = 0.6f
    }

    fun openWorld(id: Int) {
        if (!levels.isWorldUnlocked(id)) return
        worldId = id
        show(AppScreen.LEVEL_SELECT)
    }

    fun continueGame() { if (levels.count > 0) startLevel(levels.currentIndex) }

    fun startLevel(index: Int) {
        if (!levels.isUnlocked(index)) return
        levels.select(index)
        worldId = levels.all[index].worldId
        show(AppScreen.GAMEPLAY)
        gameplay.load(levels.current!!, PlayMode.LEVEL)
    }

    fun startDaily() {
        show(AppScreen.GAMEPLAY)
        gameplay.load(dailyLevel, PlayMode.DAILY)
    }

    fun startTimeAttack() {
        show(AppScreen.GAMEPLAY)
        gameplay.startTimeAttack()
    }

    /** Android back button. Returns false when the app should close. */
    fun back(): Boolean {
        when (screen) {
            AppScreen.SPLASH -> return false
            AppScreen.MAIN_MENU -> { if (loginOpen) { closeLogin(); return true }; return false }
            AppScreen.LEVEL_SELECT -> show(AppScreen.WORLD_SELECT)
            AppScreen.GAMEPLAY -> gameplay.back()
            else -> show(AppScreen.MAIN_MENU)
        }
        return true
    }

    fun swipe(d: Direction) { if (screen == AppScreen.GAMEPLAY) gameplay.swipe(d) }

    fun frame(ui: Ui, dt: Float) {
        this.ui = ui
        screenTime += dt
        when (screen) {
            AppScreen.SPLASH -> splash(ui)
            AppScreen.MAIN_MENU -> mainMenu(ui)
            AppScreen.WORLD_SELECT -> worldSelect(ui)
            AppScreen.LEVEL_SELECT -> levelMap(ui)
            AppScreen.GAMEPLAY -> gameplay.frame(ui, dt)
            AppScreen.DAILY -> daily(ui)
            AppScreen.ACHIEVEMENTS -> achievements(ui)
            AppScreen.SETTINGS -> settings(ui)
            AppScreen.PROFILE -> profile(ui)
            AppScreen.SHOP -> shop(ui)
            AppScreen.TIME_ATTACK -> timeAttack(ui)
        }
        ui.drawParticles()
        if (fade > 0f) {
            fade = maxOf(0f, fade - dt * 4f)
            ui.box(0f, 0f, 60f, 60f, Neon.a(Neon.BG, fade))
        }
    }

    // ---------------- Screens ----------------

    private fun header(ui: Ui, title: String, onBack: () -> Unit) {
        val y = ui.halfH - 0.85f
        ui.button("←", -ui.halfW + 0.75f, y, 1.0f, 1.0f, Neon.LIGHT, Style.ROUND, onClick = onBack)
        ui.text(title, -0.3f, y, 0.58f, Neon.TEXT)
        ui.coins(ui.halfW - 1.75f, y, 0.4f, save.coins)
    }

    private fun splash(ui: Ui) {
        val duration = 1.8f
        ui.background(Neon.LIGHT, Neon.SHADOW)
        ui.logo(4.6f, 1.6f)
        ui.text("One Move. Two Worlds.", 0f, 2.1f, 0.42f, Neon.TEXT)
        ui.glow(0f, -2.4f, 7f, 2.6f, Neon.a(Neon.SHADOW, 0.5f))
        ui.ring(0f, -2.4f, 5.2f, 1.6f, Neon.LIGHT, 0.1f)
        val bob = sin(ui.time * 2.2f) * 0.12f
        ui.character(-1.0f, -1.2f + bob, skin.light)
        ui.character(1.0f, -1.2f - bob, skin.shadow)
        val t = (screenTime / duration).coerceIn(0f, 1f)
        ui.rrect(0f, -5.6f, 5.5f, 0.32f, Neon.a(Neon.TEXT, 0.15f), 0.16f)
        val w = 5.3f * t
        if (w > 0.02f) ui.rrect(-2.65f + w / 2, -5.6f, w, 0.18f, Neon.LIGHT, 0.09f)
        ui.text("Loading... ${(t * 100).toInt()}%", 0f, -6.3f, 0.34f, Neon.TEXT)
        ui.text("v$versionName", 0f, -7.1f, 0.28f, Neon.a(Neon.TEXT, 0.5f))
        if (t >= 1f) show(AppScreen.MAIN_MENU)
    }

    private fun mainMenu(ui: Ui) {
        if (!loginChecked) { loginChecked = true; if (save.loginAvailable(today)) { loginOpen = true; loginClaimed = 0 } }
        ui.background(skin.light, skin.shadow)
        val top = ui.halfH - 0.85f
        ui.button("", -ui.halfW + 0.75f, top, 1.0f, 1.0f, Neon.ENERGY, Style.ROUND) { show(AppScreen.PROFILE) }
        ui.character(-ui.halfW + 0.75f, top, skin.shadow, 0.7f)
        ui.coins(-1.6f, top, 0.42f, save.coins)
        ui.circle(1.25f, top, 0.6f, Neon.ENERGY)
        ui.text("?", 1.25f, top, 0.36f, Neon.BG)
        ui.text(save.hints.toString(), 1.7f, top, 0.42f, Neon.TEXT, Align.LEFT)
        ui.button("", ui.halfW - 0.75f, top, 1.0f, 1.0f, Neon.LIGHT, Style.ROUND) { show(AppScreen.SETTINGS) }
        gear(ui, ui.halfW - 0.75f, top, 0.56f, Neon.LIGHT)

        ui.logo(5.4f, 1.45f)
        val bob = sin(ui.time * 2.2f) * 0.12f
        ui.character(-3.3f, 3.0f + bob, skin.light, 0.8f)
        ui.character(3.3f, 3.0f - bob, skin.shadow, 0.8f)
        val k = 1f + 0.06f * sin(ui.time * 2.5f)
        ui.glow(0f, 2.3f, 5.5f * k, 4.5f * k, Neon.a(skin.light, 0.45f))
        ui.button("▶  PLAY", 0f, 2.3f, 4.6f, 1.8f, skin.light, labelSize = 0.75f) { continueGame() }
        levels.current?.let { ui.text("World ${it.worldId}  ·  Level ${it.levelId}", 0f, 1.05f, 0.3f, Neon.a(Neon.TEXT, 0.6f)) }

        ui.button("Daily", -1.95f, -0.4f, 3.7f, 1.35f, Neon.PINK) { show(AppScreen.DAILY) }
        ui.text(if (dailyDoneToday) "✓" else "NEW", -0.55f, 0.1f, 0.28f, Neon.ENERGY)
        ui.button("Time Attack", 1.95f, -0.4f, 3.7f, 1.35f, Neon.ENERGY) { show(AppScreen.TIME_ATTACK) }
        if (save.timeAttackBest > 0) ui.text("Best ${save.timeAttackBest}", 1.95f, -1.35f, 0.26f, Neon.a(Neon.TEXT, 0.6f))
        ui.button("Worlds", -1.95f, -2.3f, 3.7f, 1.35f, Neon.LIGHT) { show(AppScreen.WORLD_SELECT) }
        ui.button("Shop", 1.95f, -2.3f, 3.7f, 1.35f, Neon.SUCCESS) { show(AppScreen.SHOP) }
        ui.button("Achievements", 0f, -4.1f, 7.6f, 1.2f, Neon.SHADOW) { show(AppScreen.ACHIEVEMENTS) }
        ui.text("Think. Solve. Conquer.", 0f, -ui.halfH + 0.8f, 0.28f, Neon.a(Neon.TEXT, 0.4f))
        if (loginOpen) loginPanel(ui)
    }

    private fun closeLogin() { loginOpen = false }

    private fun loginPanel(ui: Ui) {
        ui.dim(0.88f)
        ui.card(0f, 0f, 8.2f, 10.2f, Neon.ENERGY)
        ui.text("DAILY REWARD", 0f, 4.15f, 0.72f, Neon.ENERGY, logo = true)
        ui.text("Come back every day for bigger rewards!", 0f, 3.35f, 0.3f, Neon.a(Neon.TEXT, 0.75f))
        val day = save.loginDay(today)
        val claimedToday = loginClaimed > 0
        for (i in 1..7) {
            val row = if (i <= 4) 0 else 1
            val col = if (row == 0) i - 1 else i - 5
            val n = if (row == 0) 4 else 3
            val x = (col - (n - 1) / 2f) * 1.85f
            val y = 1.8f - row * 2.25f
            val done = i < day || (i == day && claimedToday)
            val isToday = i == day
            val accent = when { isToday -> Neon.ENERGY; done -> Neon.SUCCESS; else -> Neon.BLUE }
            if (isToday && !claimedToday) ui.glow(x, y, 2.6f, 2.8f, Neon.a(Neon.ENERGY, 0.35f + 0.2f * sin(ui.time * 4f)))
            ui.rrect(x, y, 1.65f, 2.0f, accent, 0.25f)
            ui.rrect(x, y, 1.55f, 1.9f, Neon.PANEL, 0.22f)
            ui.text(if (i == 7) "DAY 7" else "Day $i", x, y + 0.65f, 0.28f, Neon.a(Neon.TEXT, 0.8f))
            if (done) {
                ui.circle(x, y, 0.8f, Neon.a(Neon.SUCCESS, 0.9f))
                ui.text("✓", x, y, 0.5f, Neon.BG)
            } else {
                ui.circle(x, y, 0.7f, Neon.ENERGY)
                ui.ring(x, y, 0.48f, 0.48f, Neon.a(Neon.BG, 0.35f), 0.06f)
            }
            ui.text(Rewards.loginReward(i).toString(), x, y - 0.65f, 0.3f, Neon.TEXT)
        }
        ui.text("Day 7 also gives 2 free hints!", 0f, -1.95f, 0.28f, Neon.a(Neon.ENERGY, 0.85f))
        if (!claimedToday) {
            ui.button("CLAIM  +${Rewards.loginReward(day)}", 0f, -3.35f, 5.2f, 1.25f, Neon.SUCCESS, Style.FILLED) {
                loginClaimed = save.claimLogin(today)
                if (loginClaimed > 0) {
                    play(Sfx.WIN); pulse(40)
                    ui.particles.confetti(intArrayOf(Neon.ENERGY, Neon.SUCCESS, Neon.PINK, Neon.LIGHT), ui.halfW, ui.halfH, 90)
                    ui.particles.burst(0f, -3.35f, Neon.ENERGY, 30, 5f, 0.16f, 0.8f)
                } else loginOpen = false
            }
        } else {
            ui.text("+$loginClaimed coins!" + if (day == 7) "  +2 hints!" else "", 0f, -2.75f, 0.45f, Neon.ENERGY)
            ui.button("AWESOME", 0f, -3.9f, 4.4f, 1.1f, Neon.ENERGY, Style.FILLED) { closeLogin() }
        }
    }

    private fun gear(ui: Ui, x: Float, y: Float, size: Float, color: Int) {
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            val dx = (Math.cos(a) * size * 0.42f).toFloat()
            val dy = (Math.sin(a) * size * 0.42f).toFloat()
            ui.circle(x + dx, y + dy, size * 0.26f, color)
        }
        ui.circle(x, y, size * 0.8f, color)
        ui.circle(x, y, size * 0.36f, Neon.PANEL)
    }

    private val worldNames = listOf("First Contact", "Duality", "Distortion", "Collapse", "Singularity")
    private val worldMechanics = listOf("The basics", "Energy Cores", "Portals", "Switches & Gates", "Crumble + everything")
    private val worldColors = listOf(Neon.LIGHT, Neon.SHADOW, Neon.BLUE, Neon.WORLD_ORANGE, Neon.PINK)
    fun worldName(id: Int) = worldNames.getOrElse(id - 1) { "Daily" }

    private fun worldSelect(ui: Ui) {
        ui.background(Neon.SHADOW, Neon.LIGHT)
        header(ui, "Select World") { show(AppScreen.MAIN_MENU) }
        for (i in worldNames.indices) {
            val id = i + 1
            val y = 5.0f - i * 2.75f
            val color = worldColors[i]
            val count = levels.countInWorld(id)
            val unlocked = levels.isWorldUnlocked(id)
            ui.button("", 0f, y, 7.6f, 2.3f, color, enabled = unlocked) { openWorld(id) }
            val dimA = if (unlocked) 0.9f else 0.35f
            ui.glow(-2.4f, y, 2.4f, 2.4f, Neon.a(color, dimA))
            ui.circle(-2.4f, y, 1.0f, Neon.a(color, dimA))
            ui.text("$id. ${worldNames[i]}", -0.9f, y + 0.5f, 0.5f, Neon.a(Neon.TEXT, if (unlocked) 1f else 0.6f), Align.LEFT)
            ui.text(worldMechanics[i], -0.9f, y - 0.05f, 0.32f, Neon.a(color, if (unlocked) 1f else 0.5f), Align.LEFT)
            val sub = when {
                count == 0 -> "Coming soon"
                !unlocked -> "Locked"
                else -> "★ ${levels.starsInWorld(id)}/${count * 3}"
            }
            ui.text(sub, -0.9f, y - 0.6f, 0.32f, if (unlocked) Neon.ENERGY else Neon.a(Neon.TEXT, 0.5f), Align.LEFT)
        }
    }

    private fun levelMap(ui: Ui) {
        ui.background(Neon.LIGHT, Neon.SHADOW)
        ui.button("←", -ui.halfW + 0.75f, ui.halfH - 0.85f, 1.0f, 1.0f, Neon.LIGHT, Style.ROUND) { show(AppScreen.WORLD_SELECT) }
        ui.text("World $worldId\n${worldName(worldId)}", 0f, ui.halfH - 0.85f, 0.5f, Neon.TEXT)
        val indices = levels.indicesInWorld(worldId)
        val n = indices.size
        val pts = (0 until n).map { k ->
            val t = if (n <= 1) 0f else k.toFloat() / (n - 1)
            Pair(sin(t * Math.PI.toFloat() * 2.2f) * 2.4f, -5.6f + 11.0f * t)
        }
        for (k in 0 until n - 1) {
            val done = levels.isUnlocked(indices[k + 1])
            ui.line(pts[k].first, pts[k].second, pts[k + 1].first, pts[k + 1].second, 0.12f, Neon.a(if (done) Neon.LIGHT else Neon.TEXT, if (done) 0.6f else 0.15f))
        }
        var stars = 0
        for (k in 0 until n) {
            val index = indices[k]
            val level = levels.all[index]
            val unlocked = levels.isUnlocked(index)
            val current = index == levels.currentIndex
            val s = save.stars(level)
            stars += s
            val (x, y) = pts[k]
            if (current) {
                val p = (ui.time % 1f)
                val d = 1.3f + 0.6f * p
                ui.ring(x, y, d, d, Neon.a(Neon.ENERGY, 1f - p), 0.08f)
            }
            val accent = if (current) Neon.ENERGY else if (s > 0) Neon.LIGHT else Neon.BLUE
            ui.button(if (unlocked) level.levelId.toString() else "×", x, y, 1.15f, 1.15f, accent, Style.ROUND, enabled = unlocked) { startLevel(index) }
            ui.stars(x, y - 0.85f, 0.3f, s)
        }
        ui.star(-0.9f, -7.4f, 0.65f, Neon.ENERGY)
        ui.text("$stars / ${n * 3}", -0.45f, -7.4f, 0.45f, Neon.TEXT, Align.LEFT)
    }

    private fun daily(ui: Ui) {
        ui.background(Neon.PINK, Neon.LIGHT)
        header(ui, "Daily Challenge") { show(AppScreen.MAIN_MENU) }
        ui.text("Solve today's puzzle!", 0f, 6.1f, 0.42f, Neon.TEXT)
        val level = dailyLevel
        val lb = BoardState(level.gridWidth, level.gridHeight, level.light)
        val sb = BoardState(level.gridWidth, level.gridHeight, level.shadow)
        drawBoard(ui, lb, Neon.LIGHT, -2.0f, 3.2f, 0.62f)
        drawBoard(ui, sb, Neon.SHADOW, 2.0f, 3.2f, 0.62f)
        cellOffset(lb, lb.start, 0.62f).let { ui.character(-2.0f + it.first, 3.2f + it.second, skin.light, 0.56f) }
        cellOffset(sb, sb.start, 0.62f).let { ui.character(2.0f + it.first, 3.2f + it.second, skin.shadow, 0.56f) }
        ui.text("Reward:", 0f, -0.4f, 0.42f, Neon.TEXT)
        ui.circle(-0.85f, -1.2f, 0.75f, Neon.ENERGY)
        ui.ring(-0.85f, -1.2f, 0.51f, 0.51f, Neon.a(Neon.BG, 0.35f), 0.07f)
        ui.text("+${Rewards.DAILY_COINS}", -0.34f, -1.2f, 0.6f, Neon.TEXT, Align.LEFT)
        val done = dailyDoneToday
        ui.text(if (done) "Done for today. Come back tomorrow!" else "Best solution: ${level.starThresholds.threeStarsMaxMoves} moves", 0f, -2.2f, 0.34f, Neon.a(Neon.TEXT, 0.75f))
        ui.button(if (done) "Play Again" else "Play Now", 0f, -3.7f, 5.2f, 1.3f, Neon.SUCCESS, Style.FILLED) { startDaily() }
        ui.text("Streak: ${save.activeDailyStreak(today)} days", 0f, -5.0f, 0.42f, Neon.TEXT)
    }

    private fun achievements(ui: Ui) {
        ui.background(Neon.ENERGY, Neon.SHADOW)
        header(ui, "Achievements") { show(AppScreen.MAIN_MENU) }
        val all = Achievements.evaluate(save.snapshot(levels.all))
        val top = ui.halfH - 1.75f
        val avail = top - (-ui.halfH + 0.3f)
        val rowH = minOf(1.5f, avail / all.size)
        val k = (rowH / 1.5f).coerceIn(0.7f, 1f)
        for ((i, a) in all.withIndex()) {
            val y = top - rowH * (i + 0.5f)
            val accent = if (a.unlocked) Neon.ENERGY else Neon.SHADOW
            ui.card(0f, y, 8.6f, rowH - 0.14f, accent, 0.95f)
            if (a.unlocked) ui.star(-3.6f, y, 0.85f * k, accent) else ui.ring(-3.6f, y, 0.7f * k, 0.7f * k, accent, 0.08f)
            ui.text(a.title, -2.95f, y + 0.3f * k, 0.38f * k, Neon.TEXT, Align.LEFT)
            ui.text(a.description, -2.95f, y - 0.08f * k, 0.27f * k, Neon.a(Neon.TEXT, 0.7f), Align.LEFT)
            val w = 4.6f
            val t = (a.progress.toFloat() / a.goal).coerceIn(0f, 1f)
            ui.rrect(-2.95f + w / 2, y - 0.43f * k, w, 0.13f, Neon.a(Neon.TEXT, 0.12f), 0.065f)
            if (t > 0f) ui.rrect(-2.95f + w * t / 2, y - 0.43f * k, w * t, 0.13f, accent, 0.065f)
            ui.text("${a.progress}/${a.goal}", 4.0f, y - 0.43f * k, 0.27f * k, Neon.TEXT, Align.RIGHT)
        }
    }

    private fun shop(ui: Ui) {
        ui.background(skin.light, skin.shadow)
        header(ui, "Orb Shop") { show(AppScreen.MAIN_MENU) }
        val bob = sin(ui.time * 2.2f) * 0.1f
        ui.character(-0.9f, 5.4f + bob, skin.light, 1.1f)
        ui.character(0.9f, 5.4f - bob, skin.shadow, 1.1f)
        ui.text(skin.name, 0f, 4.25f, 0.4f, Neon.TEXT)
        for ((i, s) in SKINS.withIndex()) {
            val x = if (i % 2 == 0) -2.05f else 2.05f
            val y = 2.55f - (i / 2) * 2.55f
            val owned = save.ownsSkin(i)
            val equipped = save.skin == i
            ui.button("", x, y, 3.85f, 2.3f, if (equipped) Neon.ENERGY else s.light) {
                when {
                    equipped -> {}
                    owned -> { save.skin = i; play(Sfx.STAR); shopMessage = "${s.name} equipped!"; shopMessageAt = ui.time }
                    save.buySkin(i, s.price) -> {
                        save.skin = i
                        play(Sfx.WIN); pulse(40)
                        ui.particles.burst(x, y, s.light, 24, 4f, 0.14f, 0.7f)
                        ui.particles.burst(x, y, s.shadow, 24, 4f, 0.14f, 0.7f)
                        shopMessage = "Unlocked ${s.name}!"; shopMessageAt = ui.time
                    }
                    else -> { play(Sfx.BLOCKED); ui.shake(0.06f); shopMessage = "Need ${s.price - save.coins} more coins. Solve levels to earn more!"; shopMessageAt = ui.time }
                }
            }
            val b = sin(ui.time * 2.5f + i) * 0.06f
            ui.character(x - 0.55f, y + 0.35f + b, s.light, 0.62f)
            ui.character(x + 0.55f, y + 0.35f - b, s.shadow, 0.62f)
            ui.text(s.name, x, y - 0.45f, 0.32f, Neon.TEXT)
            when {
                equipped -> ui.text("EQUIPPED", x, y - 0.85f, 0.26f, Neon.ENERGY)
                owned -> ui.text("Tap to equip", x, y - 0.85f, 0.26f, Neon.a(Neon.TEXT, 0.6f))
                else -> {
                    ui.circle(x - 0.45f, y - 0.85f, 0.3f, Neon.ENERGY)
                    ui.text(s.price.toString(), x - 0.22f, y - 0.85f, 0.28f, if (save.coins >= s.price) Neon.TEXT else Neon.BLOCKED, Align.LEFT)
                }
            }
        }
        val age = ui.time - shopMessageAt
        if (age < 2.5f) ui.text(shopMessage, 0f, -6.5f, 0.32f, Neon.a(Neon.ENERGY, (2.5f - age).coerceAtMost(1f)))
    }

    private fun timeAttack(ui: Ui) {
        ui.background(Neon.ENERGY, Neon.PINK)
        header(ui, "Time Attack") { show(AppScreen.MAIN_MENU) }
        val k = 1f + 0.05f * sin(ui.time * 3f)
        ui.glow(0f, 4.2f, 6f * k, 4f * k, Neon.a(Neon.ENERGY, 0.35f))
        ui.ring(0f, 4.2f, 3.0f, 3.0f, Neon.ENERGY, 0.14f)
        val a = ui.time * 1.5f
        ui.line(0f, 4.2f, (sin(a) * 1.0f), 4.2f + (kotlin.math.cos(a) * 1.0f), 0.12f, Neon.ENERGY)
        ui.line(0f, 4.2f, 0f, 4.85f, 0.14f, Neon.TEXT)
        ui.circle(0f, 4.2f, 0.25f, Neon.TEXT)
        ui.text("Beat the clock!", 0f, 2.1f, 0.55f, Neon.TEXT)
        ui.text("${TimeAttack.START_SECONDS.toInt()} seconds to start.\nEvery solved puzzle adds time.\nPuzzles get harder as you go.", 0f, 0.6f, 0.36f, Neon.a(Neon.TEXT, 0.8f))
        ui.card(0f, -1.45f, 4.2f, 1.4f, Neon.PINK, 0.9f)
        ui.text("BEST", 0f, -1.1f, 0.3f, Neon.a(Neon.TEXT, 0.7f))
        ui.text(save.timeAttackBest.toString(), 0f, -1.65f, 0.6f, Neon.ENERGY, logo = true)
        ui.button("▶  START", 0f, -3.4f, 5.2f, 1.45f, Neon.ENERGY, Style.FILLED, labelSize = 0.6f) { startTimeAttack() }
        ui.text("15 coins for every puzzle solved", 0f, -4.7f, 0.3f, Neon.a(Neon.TEXT, 0.6f))
    }

    private fun settings(ui: Ui) {
        ui.background(Neon.BLUE, Neon.SHADOW)
        header(ui, "Settings") { show(AppScreen.MAIN_MENU) }
        ui.card(0f, 1.6f, 7.8f, 9.0f, Neon.BLUE)
        ui.toggle("Music", 0f, 5.0f, 6.4f, save.music) { save.music = it; sound?.applySettings() }
        ui.slider("", 0.5f, 4.0f, 5.0f, save.musicVolume, Neon.LIGHT) { save.musicVolume = it; sound?.applySettings() }
        ui.toggle("Sound Effects", 0f, 2.8f, 6.4f, save.sound) { save.sound = it }
        ui.slider("", 0.5f, 1.8f, 5.0f, save.soundVolume, Neon.LIGHT) { save.soundVolume = it }
        ui.toggle("Vibration", 0f, 0.6f, 6.4f, save.vibration) { save.vibration = it; pulse(40) }
        ui.button("Privacy Policy", 0f, -1.0f, 6.2f, 0.95f, Neon.BLUE) { openUrl(PRIVACY_URL) }
        ui.button("About Us", 0f, -2.2f, 6.2f, 0.95f, Neon.BLUE) { openUrl(ABOUT_URL) }
        ui.text("NEON SHIFT  v$versionName", 0f, -4.3f, 0.28f, Neon.a(Neon.TEXT, 0.45f))
        ui.text("Works offline. No account, no ads, no tracking.", 0f, -4.9f, 0.26f, Neon.a(Neon.TEXT, 0.45f))
    }

    private fun profile(ui: Ui) {
        ui.background(Neon.SHADOW, Neon.LIGHT)
        header(ui, "My Profile") { show(AppScreen.MAIN_MENU) }
        ui.ring(0f, 4.4f, 3.0f, 3.0f, Neon.SHADOW, 0.12f)
        ui.character(0f, 4.4f, skin.shadow, 2.4f)
        ui.text("NeonPlayer", 0f, 2.3f, 0.6f, Neon.TEXT)
        val p = save.snapshot(levels.all)
        val totalStars = levels.totalStars
        val xp = Rewards.xp(p.levelsCompleted, totalStars)
        val into = Rewards.xpIntoLevel(xp)
        ui.text("Level ${Rewards.playerLevel(xp)}", 0f, 1.55f, 0.42f, Neon.LIGHT)
        ui.rrect(0f, 0.85f, 5.5f, 0.32f, Neon.a(Neon.TEXT, 0.15f), 0.16f)
        val w = 5.3f * into / Rewards.XP_PER_LEVEL
        if (w > 0.02f) ui.rrect(-2.65f + w / 2, 0.85f, w, 0.18f, Neon.LIGHT, 0.09f)
        ui.text("$into / ${Rewards.XP_PER_LEVEL} XP", 0f, 0.3f, 0.28f, Neon.a(Neon.TEXT, 0.7f))
        stat(ui, "Completed", -2.65f, p.levelsCompleted)
        stat(ui, "Stars", 0f, totalStars)
        stat(ui, "Best Streak", 2.65f, p.bestDailyStreak)
        ui.card(0f, -5.2f, 7f, 1.2f, Neon.PINK)
        ui.text("Think. Solve. Conquer.", 0f, -5.2f, 0.45f, Neon.TEXT)
    }

    private fun stat(ui: Ui, label: String, x: Float, value: Int) {
        ui.card(x, -1.8f, 2.45f, 2.2f, Neon.BLUE)
        ui.text(label, x, -1.25f, 0.3f, Neon.a(Neon.TEXT, 0.75f))
        ui.text(value.toString(), x, -2.1f, 0.8f, Neon.LIGHT)
    }

    companion object {
        const val PRIVACY_URL = "https://github.com/mianalihassan0038-ui/NeonShift/blob/main/PRIVACY.md"
        const val ABOUT_URL = "https://github.com/mianalihassan0038-ui/NeonShift"
    }
}
