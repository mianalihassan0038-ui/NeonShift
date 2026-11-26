package com.neonshift.game

import com.neonshift.game.core.*
import com.neonshift.game.ui.*
import java.time.LocalDate
import kotlin.math.sin

enum class AppScreen { SPLASH, MAIN_MENU, WORLD_SELECT, LEVEL_SELECT, GAMEPLAY, DAILY, ACHIEVEMENTS, SETTINGS, PROFILE }

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

    val today: Int get() { val d = LocalDate.now(); return DailyChallenge.dayNumber(d.year, d.monthValue, d.dayOfMonth) }
    val dailyDoneToday get() = save.dailyLastDay == today
    private var dailyCache: LevelData? = null
    private var dailyCacheDay = 0
    val dailyLevel: LevelData
        get() {
            if (dailyCache == null || dailyCacheDay != today) { dailyCacheDay = today; dailyCache = DailyChallenge.generate(today) }
            return dailyCache!!
        }

    fun play(s: Sfx) = sound?.play(s)
    fun pulse(ms: Long) = haptics?.pulse(ms)

    fun show(s: AppScreen) {
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
        gameplay.load(levels.current!!, daily = false)
    }

    fun startDaily() {
        show(AppScreen.GAMEPLAY)
        gameplay.load(dailyLevel, daily = true)
    }

    /** Android back button. Returns false when the app should close. */
    fun back(): Boolean {
        when (screen) {
            AppScreen.SPLASH -> return false
            AppScreen.MAIN_MENU -> return false
            AppScreen.LEVEL_SELECT -> show(AppScreen.WORLD_SELECT)
            AppScreen.GAMEPLAY -> gameplay.back()
            else -> show(AppScreen.MAIN_MENU)
        }
        return true
    }

    fun swipe(d: Direction) { if (screen == AppScreen.GAMEPLAY) gameplay.swipe(d) }

    fun frame(ui: Ui, dt: Float) {
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
        }
        if (fade > 0f) {
            fade = maxOf(0f, fade - dt * 4f)
            ui.box(0f, 0f, 40f, 40f, Neon.a(Neon.BG, fade))
        }
    }

    // ---------------- Screens ----------------

    private fun header(ui: Ui, title: String, onBack: () -> Unit) {
        ui.button("←", -3.75f, 7.35f, 1.0f, 1.0f, Neon.LIGHT, Style.ROUND, onClick = onBack)
        ui.text(title, 0f, 7.35f, 0.62f, Neon.TEXT)
        ui.coins(2.85f, 7.35f, 0.42f, save.coins)
    }

    private fun splash(ui: Ui) {
        val duration = 1.8f
        ui.background(Neon.LIGHT, Neon.SHADOW)
        ui.logo(4.6f, 1.6f)
        ui.text("One Move. Two Worlds.", 0f, 2.1f, 0.42f, Neon.TEXT)
        ui.glow(0f, -2.4f, 7f, 2.6f, Neon.a(Neon.SHADOW, 0.5f))
        ui.ring(0f, -2.4f, 5.2f, 1.6f, Neon.LIGHT, 0.1f)
        val bob = sin(ui.time * 2.2f) * 0.12f
        ui.character(-1.0f, -1.2f + bob, Neon.LIGHT)
        ui.character(1.0f, -1.2f - bob, Neon.SHADOW)
        val t = (screenTime / duration).coerceIn(0f, 1f)
        ui.rrect(0f, -5.6f, 5.5f, 0.32f, Neon.a(Neon.TEXT, 0.15f), 0.16f)
        val w = 5.3f * t
        if (w > 0.02f) ui.rrect(-2.65f + w / 2, -5.6f, w, 0.18f, Neon.LIGHT, 0.09f)
        ui.text("Loading... ${(t * 100).toInt()}%", 0f, -6.3f, 0.34f, Neon.TEXT)
        ui.text("v$versionName", 0f, -7.1f, 0.28f, Neon.a(Neon.TEXT, 0.5f))
        if (t >= 1f) show(AppScreen.MAIN_MENU)
    }

    private fun mainMenu(ui: Ui) {
        ui.background(Neon.LIGHT, Neon.SHADOW)
        ui.button("", -3.7f, 7.3f, 1.0f, 1.0f, Neon.ENERGY, Style.ROUND) { show(AppScreen.PROFILE) }
        ui.character(-3.7f, 7.3f, Neon.SHADOW, 0.7f)
        ui.coins(-1.2f, 7.3f, 0.42f, save.coins)
        ui.button("", 3.7f, 7.3f, 1.0f, 1.0f, Neon.LIGHT, Style.ROUND) { show(AppScreen.SETTINGS) }
        gear(ui, 3.7f, 7.3f, 0.56f, Neon.LIGHT)

        ui.logo(5.2f, 1.5f)
        val k = 1f + 0.06f * sin(ui.time * 2.5f)
        ui.glow(0f, 0.7f, 5.5f * k, 4.5f * k, Neon.a(Neon.LIGHT, 0.45f))
        ui.button("▶  PLAY", 0f, 0.7f, 4.6f, 1.9f, Neon.LIGHT, labelSize = 0.75f) { continueGame() }
        levels.current?.let { ui.text("World ${it.worldId}  ·  Level ${it.levelId}", 0f, -0.55f, 0.3f, Neon.a(Neon.TEXT, 0.6f)) }

        ui.button("Daily Challenge", 0f, -1.85f, 5.6f, 1.05f, Neon.PINK) { show(AppScreen.DAILY) }
        ui.text(if (dailyDoneToday) "✓" else "NEW", 2.3f, -1.4f, 0.3f, Neon.ENERGY)
        ui.button("Worlds", 0f, -3.15f, 5.6f, 1.05f, Neon.LIGHT) { show(AppScreen.WORLD_SELECT) }
        ui.button("Achievements", 0f, -4.45f, 5.6f, 1.05f, Neon.SHADOW) { show(AppScreen.ACHIEVEMENTS) }
        ui.button("Settings", 0f, -5.75f, 5.6f, 1.05f, Neon.BLUE) { show(AppScreen.SETTINGS) }
        ui.text("Think. Solve. Conquer.", 0f, -7.3f, 0.28f, Neon.a(Neon.TEXT, 0.4f))
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
            ui.text("$id. ${worldNames[i]}", -0.9f, y + 0.35f, 0.5f, Neon.a(Neon.TEXT, if (unlocked) 1f else 0.6f), Align.LEFT)
            val sub = when {
                count == 0 -> "Coming soon"
                !unlocked -> "Locked"
                else -> "★ ${levels.starsInWorld(id)}/${count * 3}"
            }
            ui.text(sub, -0.9f, y - 0.4f, 0.36f, if (unlocked) Neon.ENERGY else Neon.a(Neon.TEXT, 0.5f), Align.LEFT)
        }
    }

    private fun levelMap(ui: Ui) {
        ui.background(Neon.LIGHT, Neon.SHADOW)
        ui.button("←", -3.75f, 7.35f, 1.0f, 1.0f, Neon.LIGHT, Style.ROUND) { show(AppScreen.WORLD_SELECT) }
        ui.text("World $worldId\n${worldName(worldId)}", 0f, 7.35f, 0.5f, Neon.TEXT)
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
        cellOffset(lb, lb.start, 0.62f).let { ui.character(-2.0f + it.first, 3.2f + it.second, Neon.LIGHT, 0.56f) }
        cellOffset(sb, sb.start, 0.62f).let { ui.character(2.0f + it.first, 3.2f + it.second, Neon.SHADOW, 0.56f) }
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
        for ((i, a) in all.withIndex()) {
            val y = 5.4f - i * 2.15f
            val accent = if (a.unlocked) Neon.ENERGY else Neon.SHADOW
            ui.card(0f, y, 7.8f, 1.9f, accent, 0.95f)
            if (a.unlocked) ui.star(-3.0f, y, 1.1f, accent) else ui.ring(-3.0f, y, 0.9f, 0.9f, accent, 0.1f)
            ui.text(a.title, -2.2f, y + 0.45f, 0.42f, Neon.TEXT, Align.LEFT)
            ui.text(a.description, -2.2f, y - 0.05f, 0.3f, Neon.a(Neon.TEXT, 0.7f), Align.LEFT)
            val w = 4.2f
            val t = (a.progress.toFloat() / a.goal).coerceIn(0f, 1f)
            ui.rrect(-2.2f + w / 2, y - 0.55f, w, 0.16f, Neon.a(Neon.TEXT, 0.12f), 0.08f)
            if (t > 0f) ui.rrect(-2.2f + w * t / 2, y - 0.55f, w * t, 0.16f, accent, 0.08f)
            ui.text("${a.progress} / ${a.goal}", 3.6f, y - 0.55f, 0.3f, Neon.TEXT, Align.RIGHT)
        }
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
        ui.character(0f, 4.4f, Neon.SHADOW, 2.4f)
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
