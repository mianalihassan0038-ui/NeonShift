package com.neonshift.game.core

import java.util.Random

/** What a generated level may contain. */
class GenConfig(
    val size: Int = 5,
    val minMoves: Int = 6,
    val maxMoves: Int = 12,
    val cores: Int = 0,          // up to this many cores per board
    val portals: Boolean = false,
    val gates: Boolean = false,
    val crumble: Int = 0,        // up to this many crumble tiles per board
    val minDensity: Double = 0.18,
    val maxDensity: Double = 0.34,
    val strict: Boolean = true   // every mechanic in the level must change the best solution
)

/** Random level search, checked by the solver. Used for the daily puzzle, Time Attack and to build the level packs. */
object LevelGen {
    fun generate(rng: Random, cfg: GenConfig, attempts: Int = 20000, slack: Int = 5): LevelData? {
        repeat(attempts) {
            val level = randomLevel(rng, cfg) ?: return@repeat
            if (level.validate().isNotEmpty()) return@repeat
            val sol = PuzzleSolver.solve(level, 150_000) ?: return@repeat
            if (sol.size < cfg.minMoves || sol.size > cfg.maxMoves) return@repeat
            if (!accept(level, sol, cfg)) return@repeat
            val best = sol.size
            level.maxMoves = best + slack
            level.starThresholds = StarThresholds(best, best + 2, best + slack)
            return level
        }
        return null
    }

    private fun randomLevel(rng: Random, cfg: GenConfig): LevelData? {
        val n = cfg.size
        fun rp() = GridPos(rng.nextInt(n), rng.nextInt(n))
        val ls = rp()
        val ss = rp()
        val le: GridPos
        val se: GridPos
        if (cfg.portals) {
            le = rp(); se = rp()
        } else {
            // Without portals Light's total movement mirrors Shadow's, so the exits must mirror too.
            val dx = rng.nextInt(2 * n - 1) - (n - 1)
            val dy = rng.nextInt(2 * n - 1) - (n - 1)
            if (dx == 0 && dy == 0) return null
            le = GridPos(ls.x + dx, ls.y + dy)
            se = GridPos(ss.x - dx, ss.y - dy)
        }
        if (le == ls || se == ss || !inside(le, n) || !inside(se, n)) return null
        val density = cfg.minDensity + rng.nextDouble() * (cfg.maxDensity - cfg.minDensity)
        val light = board(rng, cfg, density, ls, le)
        val shadow = board(rng, cfg, density, ss, se)
        if (cfg.portals && light.portals.isEmpty() && shadow.portals.isEmpty()) {
            (if (rng.nextBoolean()) light else shadow).let { addPortal(rng, n, it) }
        }
        if (cfg.gates) {
            // At least one switch somewhere and at least one gate somewhere.
            if (light.switches.isEmpty() && shadow.switches.isEmpty()) addTo(rng, n, if (rng.nextBoolean()) light else shadow, 1) { b, p -> b.switches = b.switches + p }
            if (light.gates.isEmpty() && shadow.gates.isEmpty()) addTo(rng, n, if (rng.nextBoolean()) light else shadow, 1) { b, p -> b.gates = b.gates + p }
        }
        if (cfg.cores > 0 && light.cores.isEmpty() && shadow.cores.isEmpty()) addTo(rng, n, light, 1) { b, p -> b.cores = b.cores + p }
        return LevelData(levelId = 1, worldId = 0, gridWidth = n, gridHeight = n, light = light, shadow = shadow)
    }

    private fun board(rng: Random, cfg: GenConfig, density: Double, start: GridPos, exit: GridPos): BoardData {
        val n = cfg.size
        val b = BoardData(start, exit)
        val walls = mutableListOf<GridPos>()
        for (y in 0 until n) for (x in 0 until n) {
            val p = GridPos(x, y)
            if (p != start && p != exit && rng.nextDouble() < density) walls += p
        }
        b.walls = walls
        if (cfg.cores > 0) addTo(rng, n, b, rng.nextInt(cfg.cores + 1)) { bd, p -> bd.cores = bd.cores + p }
        if (cfg.portals && rng.nextInt(3) > 0) addPortal(rng, n, b)
        if (cfg.gates) {
            if (rng.nextInt(2) == 0) addTo(rng, n, b, 1) { bd, p -> bd.switches = bd.switches + p }
            addTo(rng, n, b, rng.nextInt(3)) { bd, p -> bd.gates = bd.gates + p }
        }
        if (cfg.crumble > 0) addTo(rng, n, b, 1 + rng.nextInt(cfg.crumble)) { bd, p -> bd.crumble = bd.crumble + p }
        return b
    }

    private fun free(n: Int, b: BoardData, p: GridPos) = p != b.start && p != b.exit && p !in b.specialCells()

    private fun addTo(rng: Random, n: Int, b: BoardData, count: Int, add: (BoardData, GridPos) -> Unit) {
        var placed = 0
        var tries = 0
        while (placed < count && tries++ < 60) {
            val p = GridPos(rng.nextInt(n), rng.nextInt(n))
            if (free(n, b, p)) { add(b, p); placed++ }
        }
    }

    private fun addPortal(rng: Random, n: Int, b: BoardData) {
        repeat(60) {
            val a = GridPos(rng.nextInt(n), rng.nextInt(n))
            val c = GridPos(rng.nextInt(n), rng.nextInt(n))
            if (a != c && free(n, b, a) && free(n, b, c) && Math.abs(a.x - c.x) + Math.abs(a.y - c.y) >= 2) {
                b.portals = b.portals + (a to c)
                return
            }
        }
    }

    private fun inside(p: GridPos, n: Int) = p.x in 0 until n && p.y in 0 until n

    /** Checks that each mechanic in the level actually matters for its best solution. */
    fun accept(level: LevelData, sol: List<Direction>, cfg: GenConfig): Boolean {
        val replay = replay(level, sol)
        if (!level.hasPortals) {
            val direct = Math.abs(level.light.exit.x - level.light.start.x) + Math.abs(level.light.exit.y - level.light.start.y)
            if (sol.size <= direct) return false // needs a detour
        }
        if (level.hasPortals && replay.teleports == 0) return false
        if (level.hasGates && replay.toggles == 0) return false
        if (level.hasCrumble && replay.crumbles < 2) return false
        if (!cfg.strict) return true
        fun shorterWithout(edit: (BoardData) -> Unit): Boolean {
            val copy = copyLevel(level)
            edit(copy.light); edit(copy.shadow)
            val s = PuzzleSolver.solve(copy, 150_000) ?: return true
            return s.size < sol.size
        }
        if (level.hasCores && !shorterWithout { it.cores = emptyList() }) return false
        if (level.hasGates && !shorterWithout { it.gates = emptyList(); it.switches = emptyList() }) return false
        if (level.hasPortals) {
            val copy = copyLevel(level)
            copy.light.portals = emptyList(); copy.shadow.portals = emptyList()
            val s = PuzzleSolver.solve(copy, 150_000)
            if (s != null && s.size <= sol.size) return false // the portal must be the shortcut
        }
        return true
    }

    class Replay(val teleports: Int, val toggles: Int, val crumbles: Int)

    fun replay(level: LevelData, sol: List<Direction>): Replay {
        val p = PuzzleState(level)
        var t = 0; var g = 0; var c = 0
        for (d in sol) {
            val o = p.tryMove(d)
            if (o.lightTeleported || o.shadowTeleported) t++
            if (o.gatesToggled) g++
            if (o.lightCrumbled != null || o.shadowCrumbled != null) c++
        }
        return Replay(t, g, c)
    }

    fun copyLevel(l: LevelData) = LevelData(
        l.levelId, l.worldId, l.gridWidth, l.gridHeight, l.maxMoves, l.hint, l.light.copy(), l.shadow.copy(),
        StarThresholds(l.starThresholds.threeStarsMaxMoves, l.starThresholds.twoStarsMaxMoves, l.starThresholds.oneStarMaxMoves)
    )
}

/** One puzzle per calendar day, built from the date, so every player gets the same one. Mechanics rotate by day. */
object DailyChallenge {
    const val MIN_MOVES = 8
    const val MAX_MOVES = 13

    fun dayNumber(year: Int, month: Int, day: Int) = year * 10000 + month * 100 + day

    fun config(dayNumber: Int): GenConfig = when (dayNumber % 5) {
        0 -> GenConfig(minMoves = MIN_MOVES, maxMoves = MAX_MOVES)
        1 -> GenConfig(minMoves = MIN_MOVES, maxMoves = MAX_MOVES, cores = 2, strict = false)
        2 -> GenConfig(minMoves = MIN_MOVES, maxMoves = MAX_MOVES, portals = true, strict = false)
        3 -> GenConfig(minMoves = MIN_MOVES, maxMoves = MAX_MOVES, gates = true, strict = false)
        else -> GenConfig(minMoves = MIN_MOVES, maxMoves = MAX_MOVES, crumble = 4, strict = false)
    }

    fun generate(dayNumber: Int): LevelData {
        val rng = Random(dayNumber * 7919L + 17)
        val level = LevelGen.generate(rng, config(dayNumber), 6000)
            ?: LevelGen.generate(rng, GenConfig(minMoves = MIN_MOVES, maxMoves = MAX_MOVES), 20000)
            ?: LevelData(
                levelId = 1, worldId = 0,
                light = BoardData(GridPos(0, 2), GridPos(4, 2)),
                shadow = BoardData(GridPos(4, 2), GridPos(0, 2)),
                starThresholds = StarThresholds(4, 6, 9)
            )
        level.levelId = 1
        level.worldId = 0
        level.hint = "Daily puzzle. Same for every player today."
        return level
    }
}

/** Time Attack: endless random puzzles that get harder as the score grows. */
object TimeAttack {
    const val START_SECONDS = 90f
    fun bonusSeconds(best: Int) = 6f + best * 1.5f

    fun config(solved: Int): GenConfig {
        val minM = minOf(4 + solved / 3, 9)
        val maxM = minM + 3
        return when (solved % 5) {
            0 -> GenConfig(minMoves = minM, maxMoves = maxM, strict = false)
            1 -> GenConfig(minMoves = minM, maxMoves = maxM, cores = 2, strict = false)
            2 -> GenConfig(minMoves = minM, maxMoves = maxM, portals = true, strict = false)
            3 -> GenConfig(minMoves = minM, maxMoves = maxM, gates = true, strict = false)
            else -> GenConfig(minMoves = minM, maxMoves = maxM, crumble = 3, strict = false)
        }
    }

    fun puzzle(rng: Random, solved: Int): LevelData {
        val level = LevelGen.generate(rng, config(solved), 4000, slack = 0)
            ?: LevelGen.generate(rng, GenConfig(minMoves = 4, maxMoves = 8, strict = false), 20000, slack = 0)!!
        level.maxMoves = 0 // no move limit, the clock is the pressure
        level.worldId = 0
        level.levelId = solved + 1
        level.hint = ""
        return level
    }
}
