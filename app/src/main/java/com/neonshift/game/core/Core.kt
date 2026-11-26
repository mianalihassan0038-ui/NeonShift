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

class BoardData(
    var start: GridPos = GridPos(0, 0),
    var exit: GridPos = GridPos(0, 0),
    var walls: List<GridPos> = emptyList()
)

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
    var starThresholds: StarThresholds = StarThresholds()
) {
    /** Problems with the level. Empty means it is structurally valid. */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (gridWidth < 2 || gridHeight < 2) errors += "grid must be at least 2x2"
        check("light", light, errors)
        check("shadow", shadow, errors)
        return errors
    }

    private fun check(name: String, b: BoardData, errors: MutableList<String>) {
        if (!inBounds(b.start)) errors += "$name: start ${b.start} is outside the grid"
        if (!inBounds(b.exit)) errors += "$name: exit ${b.exit} is outside the grid"
        if (b.start == b.exit) errors += "$name: start and exit are the same cell"
        for (w in b.walls) {
            if (!inBounds(w)) errors += "$name: wall $w is outside the grid"
            if (w == b.start) errors += "$name: wall on start $w"
            if (w == b.exit) errors += "$name: wall on exit $w"
        }
    }

    private fun inBounds(p: GridPos) = p.x in 0 until gridWidth && p.y in 0 until gridHeight
}

enum class TileType { EMPTY, WALL, EXIT }

/** Static layout of one board (Light or Shadow). */
class BoardState(val width: Int, val height: Int, data: BoardData) {
    val start = data.start
    val exit = data.exit
    private val walls = data.walls.toHashSet()

    fun inBounds(p: GridPos) = p.x in 0 until width && p.y in 0 until height
    fun isWall(p: GridPos) = p in walls
    fun canEnter(p: GridPos) = inBounds(p) && !isWall(p)
    fun tile(p: GridPos) = when {
        isWall(p) -> TileType.WALL
        p == exit -> TileType.EXIT
        else -> TileType.EMPTY
    }
}

enum class MoveResult { MOVED, BLOCKED, ALREADY_SOLVED, OUT_OF_MOVES }

class MoveOutcome(
    val result: MoveResult,
    val lightBlocked: Boolean = false,
    val shadowBlocked: Boolean = false,
    val lightTo: GridPos? = null,
    val shadowTo: GridPos? = null,
    val solvedNow: Boolean = false,
    val failedNow: Boolean = false
)

/**
 * The live puzzle. Light moves with the swipe and Shadow moves the opposite way.
 * Both targets are checked BEFORE anything moves: if either is blocked, neither moves.
 */
class PuzzleState(val level: LevelData) {
    val lightBoard = BoardState(level.gridWidth, level.gridHeight, level.light)
    val shadowBoard = BoardState(level.gridWidth, level.gridHeight, level.shadow)
    private val history = ArrayDeque<Triple<GridPos, GridPos, Int>>()

    var lightPos = lightBoard.start; private set
    var shadowPos = shadowBoard.start; private set
    var moveCount = 0; private set

    val maxMoves get() = level.maxMoves
    val hasMoveLimit get() = level.maxMoves > 0
    val movesLeft get() = if (hasMoveLimit) maxOf(0, level.maxMoves - moveCount) else Int.MAX_VALUE
    val isSolved get() = lightPos == lightBoard.exit && shadowPos == shadowBoard.exit
    val isFailed get() = !isSolved && hasMoveLimit && moveCount >= level.maxMoves
    val canUndo get() = history.isNotEmpty()

    fun reset() {
        lightPos = lightBoard.start
        shadowPos = shadowBoard.start
        moveCount = 0
        history.clear()
    }

    /** Steps back one accepted move. Works after a win or a failure too. */
    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        lightPos = s.first; shadowPos = s.second; moveCount = s.third
        return true
    }

    fun tryMove(swipe: Direction): MoveOutcome {
        if (isSolved) return MoveOutcome(MoveResult.ALREADY_SOLVED)
        if (isFailed) return MoveOutcome(MoveResult.OUT_OF_MOVES)
        val l = lightPos.step(swipe)
        val s = shadowPos.step(swipe.opposite())
        val lb = !lightBoard.canEnter(l)
        val sb = !shadowBoard.canEnter(s)
        if (lb || sb) return MoveOutcome(MoveResult.BLOCKED, lb, sb)
        history.addLast(Triple(lightPos, shadowPos, moveCount))
        lightPos = l; shadowPos = s; moveCount++
        return MoveOutcome(MoveResult.MOVED, lightTo = l, shadowTo = s, solvedNow = isSolved, failedNow = isFailed)
    }
}

/** Breadth-first search over (Light, Shadow) positions: the shortest list of swipes, or null. */
object PuzzleSolver {
    fun solve(level: LevelData) = solve(level, level.light.start, level.shadow.start)

    fun solve(level: LevelData, lightPos: GridPos, shadowPos: GridPos): List<Direction>? {
        val lb = BoardState(level.gridWidth, level.gridHeight, level.light)
        val sb = BoardState(level.gridWidth, level.gridHeight, level.shadow)
        val start = lightPos to shadowPos
        val cameFrom = HashMap<Pair<GridPos, GridPos>, Pair<Pair<GridPos, GridPos>, Direction>>()
        val visited = hashSetOf(start)
        val queue = ArrayDeque<Pair<GridPos, GridPos>>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            if (state.first == lb.exit && state.second == sb.exit) {
                val path = mutableListOf<Direction>()
                var cur = state
                while (cur != start) {
                    val prev = cameFrom.getValue(cur)
                    path += prev.second
                    cur = prev.first
                }
                return path.reversed()
            }
            for (d in Direction.entries) {
                val l = state.first.step(d)
                val s = state.second.step(d.opposite())
                if (!lb.canEnter(l) || !sb.canEnter(s)) continue
                val next = l to s
                if (!visited.add(next)) continue
                cameFrom[next] = state to d
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

/** One puzzle per calendar day, built from the date, so every player gets the same one. */
object DailyChallenge {
    const val MIN_MOVES = 8
    const val MAX_MOVES = 13

    fun dayNumber(year: Int, month: Int, day: Int) = year * 10000 + month * 100 + day

    fun generate(dayNumber: Int): LevelData {
        val rng = java.util.Random(dayNumber * 7919L + 17)
        repeat(20000) {
            val level = randomLevel(rng) ?: return@repeat
            val sol = PuzzleSolver.solve(level) ?: return@repeat
            if (sol.size < MIN_MOVES || sol.size > MAX_MOVES) return@repeat
            val direct = Math.abs(level.light.exit.x - level.light.start.x) + Math.abs(level.light.exit.y - level.light.start.y)
            if (sol.size <= direct) return@repeat // must need a detour
            val best = sol.size
            level.maxMoves = best + 5
            level.starThresholds = StarThresholds(best, best + 2, best + 5)
            return level
        }
        return LevelData(
            levelId = 1, worldId = 0,
            light = BoardData(GridPos(0, 2), GridPos(4, 2)),
            shadow = BoardData(GridPos(4, 2), GridPos(0, 2)),
            starThresholds = StarThresholds(4, 6, 9)
        )
    }

    private fun randomLevel(rng: java.util.Random): LevelData? {
        val size = 5
        val ls = GridPos(rng.nextInt(size), rng.nextInt(size))
        val ss = GridPos(rng.nextInt(size), rng.nextInt(size))
        val dx = rng.nextInt(9) - 4
        val dy = rng.nextInt(9) - 4
        if (dx == 0 && dy == 0) return null
        val le = GridPos(ls.x + dx, ls.y + dy)
        val se = GridPos(ss.x - dx, ss.y - dy)
        if (!inside(le, size) || !inside(se, size)) return null
        val density = 0.22 + rng.nextDouble() * 0.16
        return LevelData(
            levelId = 1, worldId = 0,
            hint = "Daily puzzle. Same for every player today.",
            light = BoardData(ls, le, walls(rng, size, density, ls, le)),
            shadow = BoardData(ss, se, walls(rng, size, density, ss, se))
        )
    }

    private fun walls(rng: java.util.Random, size: Int, density: Double, a: GridPos, b: GridPos): List<GridPos> {
        val out = mutableListOf<GridPos>()
        for (y in 0 until size) for (x in 0 until size) {
            val p = GridPos(x, y)
            if (p != a && p != b && rng.nextDouble() < density) out += p
        }
        return out
    }

    private fun inside(p: GridPos, size: Int) = p.x in 0 until size && p.y in 0 until size
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

    fun xp(levelsCompleted: Int, totalStars: Int) = levelsCompleted * 60 + totalStars * 40
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
    var dailiesCompleted: Int = 0
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
        make("Daily Player", "Reach a 7-day daily streak", p.bestDailyStreak, 7)
    )

    private fun make(title: String, desc: String, progress: Int, goal: Int): Achievement {
        val g = maxOf(1, goal)
        return Achievement(title, desc, minOf(progress, g), g)
    }
}
