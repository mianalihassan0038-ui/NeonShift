package com.neonshift.game.core

// Game rules. Plain Kotlin with no Android imports, so they run in JVM unit tests.

/** A cell on a board. x = column (0 = left), y = row (0 = TOP), the same as the level JSON files. */
data class GridPos(val x: Int, val y: Int) {
    fun step(d: Direction) = GridPos(x + d.dx, y + d.dy)
    override fun toString() = "($x,$y)"
}

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    fun opposite(): Direction = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
    }
}

/**
 * One board of a level.
 * - cores: energy cores; the board's exit only counts once all of them are collected.
 * - portals: pairs of cells; stepping onto one moves the token to the other.
 * - switches / gates: stepping onto any switch (on either board) flips ALL gates on BOTH boards. Gates start closed.
 * - crumble: floor that breaks into a hole after the token leaves it.
 */
class BoardData(
    var start: GridPos = GridPos(0, 0),
    var exit: GridPos = GridPos(0, 0),
    var walls: List<GridPos> = emptyList(),
    var cores: List<GridPos> = emptyList(),
    var portals: List<Pair<GridPos, GridPos>> = emptyList(),
    var switches: List<GridPos> = emptyList(),
    var gates: List<GridPos> = emptyList(),
    var crumble: List<GridPos> = emptyList(),
    /** A frozen board never moves (used by the single-board tutorial levels). */
    var frozen: Boolean = false
) {
    fun specialCells(): List<GridPos> = walls + cores + portals.flatMap { listOf(it.first, it.second) } + switches + gates + crumble
    fun copy() = BoardData(start, exit, walls.toList(), cores.toList(), portals.toList(), switches.toList(), gates.toList(), crumble.toList(), frozen)
}

class StarThresholds(var threeStarsMaxMoves: Int = 0, var twoStarsMaxMoves: Int = 0, var oneStarMaxMoves: Int = 0)

class LevelData(
    var levelId: Int = 1,
    var worldId: Int = 1,
    var gridWidth: Int = 5,
    var gridHeight: Int = 5,
    var maxMoves: Int = 0,          // 0 = no limit
    var hint: String = "",
    var light: BoardData = BoardData(),
    var shadow: BoardData = BoardData(),
    var starThresholds: StarThresholds = StarThresholds(),
    /** Tutorial style for early levels: "swipe" (guided), "shadow" (guided Shadow intro) or empty. */
    var tutorial: String = ""
) {
    /** Only the Light board is played. */
    val solo get() = shadow.frozen
    val hasCores get() = light.cores.isNotEmpty() || shadow.cores.isNotEmpty()
    val hasPortals get() = light.portals.isNotEmpty() || shadow.portals.isNotEmpty()
    val hasGates get() = light.gates.isNotEmpty() || shadow.gates.isNotEmpty() || light.switches.isNotEmpty() || shadow.switches.isNotEmpty()
    val hasCrumble get() = light.crumble.isNotEmpty() || shadow.crumble.isNotEmpty()

    /** Problems with the level. Empty means it is structurally valid. */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (gridWidth < 2 || gridHeight < 2) errors += "grid must be at least 2x2"
        if (gridWidth * gridHeight > 64) errors += "grid too large"
        check("light", light, errors)
        check("shadow", shadow, errors)
        return errors
    }

    private fun check(name: String, b: BoardData, errors: MutableList<String>) {
        if (!inBounds(b.start)) errors += "$name: start ${b.start} is outside the grid"
        if (!inBounds(b.exit)) errors += "$name: exit ${b.exit} is outside the grid"
        if (b.start == b.exit && !b.frozen) errors += "$name: start and exit are the same cell"
        val special = b.specialCells()
        for (c in special) {
            if (!inBounds(c)) errors += "$name: cell $c is outside the grid"
            if (c == b.start) errors += "$name: special tile on start $c"
            if (c == b.exit) errors += "$name: special tile on exit $c"
        }
        if (special.size != special.toSet().size) errors += "$name: two special tiles share a cell"
    }

    private fun inBounds(p: GridPos) = p.x in 0 until gridWidth && p.y in 0 until gridHeight
}

enum class TileType { EMPTY, WALL, EXIT, CORE, PORTAL, SWITCH, GATE, CRUMBLE }

/** Static layout of one board (Light or Shadow). */
class BoardState(val width: Int, val height: Int, val data: BoardData) {
    val start = data.start
    val exit = data.exit
    val frozen = data.frozen
    private val walls = data.walls.toHashSet()
    private val gates = data.gates.toHashSet()
    private val switches = data.switches.toHashSet()
    private val crumble = data.crumble.toHashSet()
    private val portalMap = HashMap<GridPos, GridPos>().apply { for ((a, b) in data.portals) { put(a, b); put(b, a) } }
    /** Bit for each core, by its index in data.cores. */
    val allCores: Long = if (data.cores.isEmpty()) 0L else (1L shl data.cores.size) - 1

    fun inBounds(p: GridPos) = p.x in 0 until width && p.y in 0 until height
    fun bit(p: GridPos) = 1L shl (p.y * width + p.x)
    fun isWall(p: GridPos) = p in walls
    fun isGate(p: GridPos) = p in gates
    fun isSwitch(p: GridPos) = p in switches
    fun isCrumble(p: GridPos) = p in crumble
    fun portalTarget(p: GridPos) = portalMap[p]
    fun coreIndex(p: GridPos) = data.cores.indexOf(p)

    fun canEnter(p: GridPos, broken: Long, gatesOpen: Boolean) =
        inBounds(p) && !isWall(p) && (broken and bit(p)) == 0L && !(isGate(p) && !gatesOpen)

    fun tile(p: GridPos) = when {
        isWall(p) -> TileType.WALL
        p == exit -> TileType.EXIT
        isGate(p) -> TileType.GATE
        isSwitch(p) -> TileType.SWITCH
        isCrumble(p) -> TileType.CRUMBLE
        portalMap.containsKey(p) -> TileType.PORTAL
        coreIndex(p) >= 0 -> TileType.CORE
        else -> TileType.EMPTY
    }
}

/** Everything that changes during play. Immutable, so it doubles as the undo snapshot and the solver state. */
data class PlayState(
    val light: GridPos, val shadow: GridPos, val gatesOpen: Boolean,
    val coresL: Long, val coresS: Long, val brokenL: Long, val brokenS: Long
)

enum class MoveResult { MOVED, BLOCKED, ALREADY_SOLVED, OUT_OF_MOVES }

class MoveOutcome(
    val result: MoveResult,
    val lightBlocked: Boolean = false,
    val shadowBlocked: Boolean = false,
    val lightTo: GridPos? = null,
    val shadowTo: GridPos? = null,
    val lightStep: GridPos? = null,   // the cell stepped onto (differs from lightTo after a portal)
    val shadowStep: GridPos? = null,
    val lightCore: GridPos? = null,   // core collected this move
    val shadowCore: GridPos? = null,
    val lightCrumbled: GridPos? = null, // cell that broke behind the token
    val shadowCrumbled: GridPos? = null,
    val gatesToggled: Boolean = false,
    val solvedNow: Boolean = false,
    val failedNow: Boolean = false
) {
    val lightTeleported get() = lightStep != null && lightStep != lightTo
    val shadowTeleported get() = shadowStep != null && shadowStep != shadowTo
}

object Rules {
    fun initial(level: LevelData, lb: BoardState, sb: BoardState) =
        PlayState(level.light.start, level.shadow.start, false, lb.allCores, sb.allCores, 0L, 0L)

    fun solved(st: PlayState, lb: BoardState, sb: BoardState) =
        st.light == lb.exit && st.shadow == sb.exit && st.coresL == 0L && st.coresS == 0L

    /** Applies one swipe. Returns null if either token is blocked (then nothing moves). */
    fun step(st: PlayState, d: Direction, lb: BoardState, sb: BoardState): PlayState? {
        val l1 = st.light.step(d)
        val s1 = if (sb.frozen) st.shadow else st.shadow.step(d.opposite())
        if (!lb.canEnter(l1, st.brokenL, st.gatesOpen) || (!sb.frozen && !sb.canEnter(s1, st.brokenS, st.gatesOpen))) return null
        val brokenL = if (lb.isCrumble(st.light)) st.brokenL or lb.bit(st.light) else st.brokenL
        val brokenS = if (!sb.frozen && sb.isCrumble(st.shadow)) st.brokenS or sb.bit(st.shadow) else st.brokenS
        val l2 = lb.portalTarget(l1) ?: l1
        val s2 = sb.portalTarget(s1) ?: s1
        var coresL = st.coresL
        var coresS = st.coresS
        lb.coreIndex(l2).let { if (it >= 0) coresL = coresL and (1L shl it).inv() }
        sb.coreIndex(s2).let { if (it >= 0) coresS = coresS and (1L shl it).inv() }
        var flips = 0
        if (lb.isSwitch(l2)) flips++
        if (sb.isSwitch(s2)) flips++
        val gates = if (flips % 2 == 1) !st.gatesOpen else st.gatesOpen
        return PlayState(l2, s2, gates, coresL, coresS, brokenL, brokenS)
    }
}

/**
 * The live puzzle. Light moves with the swipe and Shadow moves the opposite way.
 * Both targets are checked BEFORE anything moves: if either is blocked, neither moves.
 */
class PuzzleState(val level: LevelData) {
    val lightBoard = BoardState(level.gridWidth, level.gridHeight, level.light)
    val shadowBoard = BoardState(level.gridWidth, level.gridHeight, level.shadow)
    private val history = ArrayDeque<Pair<PlayState, Int>>()

    var state = Rules.initial(level, lightBoard, shadowBoard); private set
    var moveCount = 0; private set

    val lightPos get() = state.light
    val shadowPos get() = state.shadow
    val gatesOpen get() = state.gatesOpen
    val maxMoves get() = level.maxMoves
    val hasMoveLimit get() = level.maxMoves > 0
    val movesLeft get() = if (hasMoveLimit) maxOf(0, level.maxMoves - moveCount) else Int.MAX_VALUE
    val isSolved get() = Rules.solved(state, lightBoard, shadowBoard)
    val isFailed get() = !isSolved && hasMoveLimit && moveCount >= level.maxMoves
    val canUndo get() = history.isNotEmpty()

    fun coresLeft(light: Boolean) = java.lang.Long.bitCount(if (light) state.coresL else state.coresS)
    fun hasCore(light: Boolean, p: GridPos): Boolean {
        val b = if (light) lightBoard else shadowBoard
        val i = b.coreIndex(p)
        return i >= 0 && ((if (light) state.coresL else state.coresS) and (1L shl i)) != 0L
    }
    fun isBroken(light: Boolean, p: GridPos): Boolean {
        val b = if (light) lightBoard else shadowBoard
        return b.inBounds(p) && ((if (light) state.brokenL else state.brokenS) and b.bit(p)) != 0L
    }

    fun reset() {
        state = Rules.initial(level, lightBoard, shadowBoard)
        moveCount = 0
        history.clear()
    }

    /** Steps back one accepted move. Works after a win or a failure too. */
    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        state = s.first
        moveCount = s.second
        return true
    }

    /** A swipe that wins right now, or null. */
    fun winningMove(): Direction? {
        if (isSolved) return null
        for (d in Direction.entries) {
            val n = Rules.step(state, d, lightBoard, shadowBoard) ?: continue
            if (Rules.solved(n, lightBoard, shadowBoard)) return d
        }
        return null
    }

    fun tryMove(swipe: Direction): MoveOutcome {
        if (isSolved) return MoveOutcome(MoveResult.ALREADY_SOLVED)
        if (isFailed) return MoveOutcome(MoveResult.OUT_OF_MOVES)
        val before = state
        val next = Rules.step(before, swipe, lightBoard, shadowBoard)
        if (next == null) {
            val l = before.light.step(swipe)
            val s = before.shadow.step(swipe.opposite())
            return MoveOutcome(
                MoveResult.BLOCKED,
                !lightBoard.canEnter(l, before.brokenL, before.gatesOpen),
                !shadowBoard.frozen && !shadowBoard.canEnter(s, before.brokenS, before.gatesOpen)
            )
        }
        history.addLast(before to moveCount)
        state = next
        moveCount++
        val lc = if (next.coresL != before.coresL) next.light else null
        val sc = if (next.coresS != before.coresS) next.shadow else null
        return MoveOutcome(
            MoveResult.MOVED,
            lightTo = next.light, shadowTo = next.shadow,
            lightStep = before.light.step(swipe), shadowStep = if (shadowBoard.frozen) before.shadow else before.shadow.step(swipe.opposite()),
            lightCore = lc, shadowCore = sc,
            lightCrumbled = if (next.brokenL != before.brokenL) before.light else null,
            shadowCrumbled = if (next.brokenS != before.brokenS) before.shadow else null,
            gatesToggled = next.gatesOpen != before.gatesOpen,
            solvedNow = isSolved, failedNow = isFailed
        )
    }
}

/** Breadth-first search over the whole play state: the shortest list of swipes, or null. */
object PuzzleSolver {
    fun solve(level: LevelData, limit: Int = 400_000): List<Direction>? {
        val lb = BoardState(level.gridWidth, level.gridHeight, level.light)
        val sb = BoardState(level.gridWidth, level.gridHeight, level.shadow)
        return solveFrom(level, Rules.initial(level, lb, sb), limit)
    }

    fun solve(level: LevelData, state: PlayState) = solveFrom(level, state, 400_000)

    fun solveFrom(level: LevelData, start: PlayState, limit: Int): List<Direction>? {
        val lb = BoardState(level.gridWidth, level.gridHeight, level.light)
        val sb = BoardState(level.gridWidth, level.gridHeight, level.shadow)
        val cameFrom = HashMap<PlayState, Pair<PlayState, Direction>>()
        val visited = hashSetOf(start)
        val queue = ArrayDeque<PlayState>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val st = queue.removeFirst()
            if (Rules.solved(st, lb, sb)) {
                val path = mutableListOf<Direction>()
                var cur = st
                while (cur != start) {
                    val prev = cameFrom.getValue(cur)
                    path += prev.second
                    cur = prev.first
                }
                return path.reversed()
            }
            if (visited.size > limit) return null
            for (d in Direction.entries) {
                val next = Rules.step(st, d, lb, sb) ?: continue
                if (!visited.add(next)) continue
                cameFrom[next] = st to d
                queue.addLast(next)
            }
        }
        return null
    }
}

object StarManager {
    /** A finished level always earns at least 1 star. */
    fun calculate(level: LevelData, moves: Int): Int {
        val t = level.starThresholds
        if (t.threeStarsMaxMoves <= 0) return 3
        if (moves <= t.threeStarsMaxMoves) return 3
        if (moves <= t.twoStarsMaxMoves) return 2
        return 1
    }
}

object Rewards {
    const val FIRST_CLEAR_COINS = 50
    const val COINS_PER_NEW_STAR = 25
    const val DAILY_COINS = 200
    const val HINT_COST = 100
    const val STARTING_HINTS = 3
    const val XP_PER_LEVEL = 1000

    fun levelCoins(previousBestStars: Int, earnedStars: Int): Int {
        var coins = if (previousBestStars == 0) FIRST_CLEAR_COINS else 0
        if (earnedStars > previousBestStars) coins += (earnedStars - previousBestStars) * COINS_PER_NEW_STAR
        return coins
    }

    private val LOGIN = intArrayOf(50, 75, 100, 150, 200, 300, 500)
    fun loginReward(day: Int) = LOGIN[(day - 1).coerceIn(0, 6)]

    fun xp(levelsCompleted: Int, totalStars: Int) = levelsCompleted * 60 + totalStars * 40

    // Player XP (stored): every win gives XP, levels need a little more XP each time.
    const val WIN_BASE_COINS = 5
    const val CHEST_EVERY = 3
    const val LEVEL_UP_COINS = 100
    fun winXp(stars: Int) = 20 + 10 * stars
    fun xpNeeded(level: Int) = 80 + 40 * (level - 1)
    /** (player level, xp into that level, xp needed for the next level). */
    fun levelInfo(totalXp: Int): Triple<Int, Int, Int> {
        var level = 1
        var rest = totalXp
        while (rest >= xpNeeded(level)) { rest -= xpNeeded(level); level++ }
        return Triple(level, rest, xpNeeded(level))
    }
    fun streakMultiplier(streak: Int) = when { streak >= 5 -> 3; streak >= 3 -> 2; else -> 1 }
    fun playerLevel(xp: Int) = 1 + xp / XP_PER_LEVEL
    fun xpIntoLevel(xp: Int) = xp % XP_PER_LEVEL

    fun nextStreak(lastDay: Int, today: Int, currentStreak: Int, nextDay: (Int) -> Int): Int {
        if (lastDay == today) return currentStreak
        if (lastDay != 0 && nextDay(lastDay) == today) return currentStreak + 1
        return 1
    }
}

class ProgressSnapshot(
    var levelsCompleted: Int = 0,
    var totalLevels: Int = 0,
    var world1Completed: Int = 0,
    var world1Total: Int = 0,
    var perfectLevels: Int = 0,
    var noHintLevels: Int = 0,
    var bestDailyStreak: Int = 0,
    var dailiesCompleted: Int = 0,
    var timeAttackBest: Int = 0,
    val worldCompleted: IntArray = IntArray(8),
    val worldTotal: IntArray = IntArray(8)
)

class Achievement(val title: String, val description: String, val progress: Int, val goal: Int) {
    val unlocked get() = progress >= goal
}

object Achievements {
    fun evaluate(p: ProgressSnapshot) = listOf(
        make("First Contact", "Complete your first level", p.levelsCompleted, 1),
        make("World Clear", "Complete every World 01 level", p.world1Completed, p.world1Total),
        make("Puzzle Master", "Complete all ${p.totalLevels} levels", p.levelsCompleted, p.totalLevels),
        make("Perfect Solver", "Get 3 stars on 10 levels", p.perfectLevels, 10),
        make("No Hint Challenge", "Finish 10 levels without hints", p.noHintLevels, 10),
        make("Daily Player", "Reach a 7-day daily streak", p.bestDailyStreak, 7),
        make("Core Collector", "Complete World 02", p.worldCompleted.getOrElse(2) { 0 }, p.worldTotal.getOrElse(2) { 1 }),
        make("Portal Jumper", "Complete World 03", p.worldCompleted.getOrElse(3) { 0 }, p.worldTotal.getOrElse(3) { 1 }),
        make("Gatekeeper", "Complete World 04", p.worldCompleted.getOrElse(4) { 0 }, p.worldTotal.getOrElse(4) { 1 }),
        make("Singularity", "Complete World 05", p.worldCompleted.getOrElse(5) { 0 }, p.worldTotal.getOrElse(5) { 1 }),
        make("Speed Demon", "Solve 10 puzzles in one Time Attack run", p.timeAttackBest, 10)
    )

    private fun make(title: String, desc: String, progress: Int, goal: Int): Achievement {
        val g = maxOf(1, goal)
        return Achievement(title, desc, minOf(progress, g), g)
    }
}

/** What the player gets at each player level. Every level up also pays coins and a hint. */
object Unlocks {
    enum class Kind { DAILY, CHEST, RELAX, SKIN, TIME_ATTACK, TRAIL }
    class Unlock(val level: Int, val kind: Kind, val id: Int, val label: String)

    val ALL = listOf(
        Unlock(2, Kind.DAILY, 0, "Daily Challenge"),
        Unlock(2, Kind.CHEST, 0, "Free chest"),
        Unlock(3, Kind.RELAX, 0, "Relax mode"),
        Unlock(4, Kind.SKIN, 1, "Sunset orbs"),
        Unlock(5, Kind.TIME_ATTACK, 0, "Time Attack"),
        Unlock(6, Kind.TRAIL, 1, "Spark trail"),
        Unlock(7, Kind.CHEST, 0, "Free chest"),
        Unlock(8, Kind.SKIN, 2, "Toxic orbs"),
        Unlock(10, Kind.TRAIL, 2, "Rainbow trail"),
        Unlock(12, Kind.SKIN, 4, "Royal orbs"),
        Unlock(15, Kind.TRAIL, 3, "Star trail")
    )
    const val DAILY_LEVEL = 2
    const val RELAX_LEVEL = 3
    const val TIME_ATTACK_LEVEL = 5

    fun at(level: Int) = ALL.filter { it.level == level }
    /** The next unlock above [level], or null when everything is unlocked. */
    fun next(level: Int) = ALL.firstOrNull { it.level > level }
    fun trailLevel(id: Int) = ALL.firstOrNull { it.kind == Kind.TRAIL && it.id == id }?.level ?: 1
}
