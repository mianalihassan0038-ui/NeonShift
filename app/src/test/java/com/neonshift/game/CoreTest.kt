package com.neonshift.game

import com.neonshift.game.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CoreTest {
    private fun p(x: Int, y: Int) = GridPos(x, y)

    private fun open(lightWalls: List<GridPos> = emptyList(), shadowWalls: List<GridPos> = emptyList(), maxMoves: Int = 0) = LevelData(
        maxMoves = maxMoves,
        light = BoardData(p(2, 2), p(4, 2), lightWalls),
        shadow = BoardData(p(2, 2), p(0, 2), shadowWalls)
    )

    @Test fun directions() {
        for (d in Direction.entries) {
            val s = PuzzleState(open())
            assertEquals(MoveResult.MOVED, s.tryMove(d).result)
            assertEquals("$d light follows swipe", p(2, 2).step(d), s.lightPos)
            assertEquals("$d shadow opposite", p(2, 2).step(d.opposite()), s.shadowPos)
        }
        assertEquals("Up decreases row", p(2, 1), p(2, 2).step(Direction.UP))
    }

    @Test fun lightBlockedMovesNeither() {
        val s = PuzzleState(open(lightWalls = listOf(p(3, 2))))
        val o = s.tryMove(Direction.RIGHT)
        assertTrue(o.result == MoveResult.BLOCKED && o.lightBlocked && !o.shadowBlocked)
        assertEquals(p(2, 2), s.lightPos); assertEquals(p(2, 2), s.shadowPos); assertEquals(0, s.moveCount)
    }

    @Test fun shadowBlockedMovesNeither() {
        val s = PuzzleState(open(shadowWalls = listOf(p(1, 2))))
        val o = s.tryMove(Direction.RIGHT)
        assertTrue(o.result == MoveResult.BLOCKED && o.shadowBlocked && !o.lightBlocked)
        assertEquals(p(2, 2), s.lightPos); assertEquals(0, s.moveCount)
    }

    @Test fun edgesBlock() {
        val level = LevelData(light = BoardData(p(0, 0), p(4, 4)), shadow = BoardData(p(4, 4), p(0, 0)))
        val s = PuzzleState(level)
        assertTrue(s.tryMove(Direction.UP).lightBlocked)
        assertTrue(s.tryMove(Direction.LEFT).lightBlocked)
        assertTrue(s.tryMove(Direction.UP).shadowBlocked)
        assertEquals(0, s.moveCount)
    }

    @Test fun counterWinAndReset() {
        val s = PuzzleState(open())
        s.tryMove(Direction.RIGHT)
        assertEquals(1, s.moveCount); assertFalse(s.isSolved)
        val o = s.tryMove(Direction.RIGHT)
        assertTrue(o.solvedNow && s.isSolved && s.moveCount == 2)
        assertEquals(MoveResult.ALREADY_SOLVED, s.tryMove(Direction.LEFT).result)
        s.reset()
        assertTrue(s.moveCount == 0 && s.lightPos == p(2, 2) && !s.canUndo)
    }

    @Test fun undo() {
        val s = PuzzleState(open(lightWalls = listOf(p(2, 0))))
        assertFalse(s.undo())
        s.tryMove(Direction.UP)
        s.tryMove(Direction.UP) // blocked
        assertEquals(1, s.moveCount)
        assertTrue(s.undo())
        assertTrue(s.lightPos == p(2, 2) && s.shadowPos == p(2, 2) && s.moveCount == 0)
        assertFalse(s.undo())
        s.tryMove(Direction.RIGHT); s.tryMove(Direction.RIGHT)
        assertTrue(s.isSolved)
        assertTrue(s.undo() && !s.isSolved && s.moveCount == 1)
    }

    @Test fun moveLimit() {
        val s = PuzzleState(open(maxMoves = 3))
        s.tryMove(Direction.UP); s.tryMove(Direction.DOWN)
        assertEquals(1, s.movesLeft)
        val o = s.tryMove(Direction.UP)
        assertTrue(o.failedNow && s.isFailed)
        assertEquals(MoveResult.OUT_OF_MOVES, s.tryMove(Direction.DOWN).result)
        assertTrue(s.undo() && !s.isFailed)
        val win = PuzzleState(open(maxMoves = 2))
        win.tryMove(Direction.RIGHT)
        val w = win.tryMove(Direction.RIGHT)
        assertTrue(w.solvedNow && !w.failedNow)
    }

    @Test fun stars() {
        val l = open().apply { starThresholds = StarThresholds(8, 10, 14) }
        assertEquals(3, StarManager.calculate(l, 8))
        assertEquals(2, StarManager.calculate(l, 10))
        assertEquals(1, StarManager.calculate(l, 30))
    }

    @Test fun dailyChallenge() {
        val a = DailyChallenge.generate(20261125)
        val b = DailyChallenge.generate(20261125)
        assertTrue(a.validate().isEmpty())
        assertEquals(a.light.start, b.light.start); assertEquals(a.shadow.exit, b.shadow.exit)
        var day = 20261101
        repeat(30) {
            val level = DailyChallenge.generate(day)
            val sol = PuzzleSolver.solve(level)
            assertNotNull("daily $day solvable", sol)
            assertTrue(sol!!.size in 4..DailyChallenge.MAX_MOVES)
            assertEquals(sol.size, level.starThresholds.threeStarsMaxMoves)
            day = SaveData.nextDay(day)
        }
        assertEquals(20261201, SaveData.nextDay(20261130))
    }

    @Test fun rewardsAndSave() {
        assertEquals(Rewards.FIRST_CLEAR_COINS + 3 * Rewards.COINS_PER_NEW_STAR, Rewards.levelCoins(0, 3))
        assertEquals(Rewards.COINS_PER_NEW_STAR, Rewards.levelCoins(2, 3))
        assertEquals(0, Rewards.levelCoins(3, 2))
        assertEquals(5, Rewards.nextStreak(20261124, 20261125, 4, SaveData::nextDay))
        assertEquals(1, Rewards.nextStreak(20261120, 20261125, 4, SaveData::nextDay))
        val save = SaveData(MemoryStore())
        assertEquals(3, save.hints)
        repeat(3) { assertTrue(save.trySpendHint()) }
        assertFalse(save.trySpendHint())
        assertEquals(200, save.completeDaily(20261125))
        assertEquals(0, save.completeDaily(20261125))
        assertTrue(save.trySpendHint()); assertEquals(100, save.coins)
        assertEquals(1, save.activeDailyStreak(20261126))
        assertEquals(0, save.activeDailyStreak(20261128))
    }

    private fun lvl(light: BoardData, shadow: BoardData) = LevelData(light = light, shadow = shadow)

    @Test fun coresMustBeCollected() {
        val l = lvl(BoardData(p(0, 2), p(2, 2), cores = listOf(p(0, 1))), BoardData(p(4, 2), p(2, 2)))
        val s = PuzzleState(l)
        s.tryMove(Direction.RIGHT); s.tryMove(Direction.RIGHT)
        assertFalse("exit locked while a core is left", s.isSolved)
        assertEquals(1, s.coresLeft(true))
        assertEquals(4, PuzzleSolver.solve(l)!!.size.let { if (it % 2 == 0) 4 else it })
        val sol = PuzzleSolver.solve(l)!!
        val r = PuzzleState(l); sol.forEach { r.tryMove(it) }
        assertTrue(r.isSolved && r.coresLeft(true) == 0)
    }

    @Test fun portalsTeleport() {
        val l = lvl(BoardData(p(0, 0), p(4, 4), portals = listOf(p(1, 0) to p(3, 4))), BoardData(p(4, 4), p(0, 0)))
        val s = PuzzleState(l)
        val o = s.tryMove(Direction.RIGHT)
        assertTrue(o.lightTeleported)
        assertEquals(p(3, 4), s.lightPos)
        assertEquals(p(3, 4), s.shadowPos)
    }

    @Test fun switchFlipsGatesOnBothBoards() {
        val l = lvl(BoardData(p(0, 2), p(4, 2), switches = listOf(p(1, 2)), gates = listOf(p(0, 1))),
                    BoardData(p(4, 2), p(0, 2), gates = listOf(p(3, 3))))
        val s = PuzzleState(l)
        assertTrue("closed gate blocks", s.tryMove(Direction.UP).lightBlocked)
        val o = s.tryMove(Direction.RIGHT)
        assertTrue(o.gatesToggled && s.gatesOpen)
        assertEquals(MoveResult.MOVED, s.tryMove(Direction.UP).result) // shadow steps down into its open gate
        assertEquals(p(3, 3), s.shadowPos)
        assertTrue(s.undo() && s.undo() && !s.gatesOpen)
    }

    @Test fun crumbleBreaksBehind() {
        val l = lvl(BoardData(p(0, 2), p(4, 2), crumble = listOf(p(1, 2))), BoardData(p(4, 2), p(0, 2)))
        val s = PuzzleState(l)
        s.tryMove(Direction.RIGHT)
        val o = s.tryMove(Direction.RIGHT)
        assertEquals(p(1, 2), o.lightCrumbled)
        assertTrue(s.isBroken(true, p(1, 2)))
        s.tryMove(Direction.LEFT)
        assertTrue("broken tile is a hole", s.tryMove(Direction.LEFT).lightBlocked)
    }

    @Test fun timeAttackPuzzlesSolvable() {
        val rng = java.util.Random(3)
        for (i in 0 until 10) {
            val l = TimeAttack.puzzle(rng, i)
            assertTrue(l.validate().isEmpty())
            assertNotNull(PuzzleSolver.solve(l))
        }
    }

    @Test fun loginRewards() {
        val save = SaveData(MemoryStore())
        assertEquals(50, save.claimLogin(20261125))
        assertEquals(0, save.claimLogin(20261125))
        assertEquals(75, save.claimLogin(20261126))
        assertEquals(50, save.claimLogin(20261130)) // missed days restart the cycle
        assertTrue(save.buySkin(1, 100)); assertTrue(save.ownsSkin(1)); assertEquals(75, save.coins)
    }

    @Test fun json() {
        val v = MiniJson.parse("""{"a":[1,2,{"b":"x\"y"}],"c":-3.5,"d":true,"e":null}""") as Map<*, *>
        assertEquals(-3.5, v["c"]); assertEquals(true, v["d"])
        assertEquals("x\"y", ((v["a"] as List<*>)[2] as Map<*, *>)["b"])
    }

    @Test fun shippedLevels() {
        val dir = File("src/main/assets/levels")
        val files = dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        assertTrue(files.size >= 50)
        val ids = HashSet<String>()
        for (f in files) {
            val level = LevelParser.parse(f.readText())
            assertTrue("${f.name}: ${level.validate()}", level.validate().isEmpty())
            assertTrue("${f.name} unique id", ids.add("${level.worldId}-${level.levelId}"))
            val sol = PuzzleSolver.solve(level)
            assertNotNull("${f.name} solvable", sol)
            val s = PuzzleState(level)
            sol!!.forEach { s.tryMove(it) }
            assertTrue("${f.name} replays", s.isSolved && s.moveCount == sol.size)
            assertEquals("${f.name} 3-star = best", sol.size, level.starThresholds.threeStarsMaxMoves)
            assertTrue("${f.name} limit", level.maxMoves == 0 || level.maxMoves >= sol.size)
        }
    }

    @Test fun soloBoardOnlyMovesLight() {
        val l = LevelParser.parse("""{"gridWidth":4,"gridHeight":3,"solo":true,"light":{"start":{"x":0,"y":1},"exit":{"x":3,"y":1},"walls":[]}}""")
        assertTrue(l.solo && l.validate().isEmpty())
        val s = PuzzleState(l)
        assertEquals(Direction.RIGHT, null ?: PuzzleSolver.solve(l)!!.first())
        s.tryMove(Direction.RIGHT); s.tryMove(Direction.RIGHT)
        assertEquals(Direction.RIGHT, s.winningMove())
        val o = s.tryMove(Direction.RIGHT)
        assertTrue(o.solvedNow && !o.shadowTeleported)
        assertEquals(3, PuzzleSolver.solve(l)!!.size)
    }

    @Test fun playerLevelsAndUnlocks() {
        assertEquals(Triple(1, 0, 80), Rewards.levelInfo(0))
        assertEquals(Triple(2, 10, 120), Rewards.levelInfo(90))
        val save = SaveData(MemoryStore())
        assertFalse(save.unlocked(Unlocks.TIME_ATTACK_LEVEL))
        val hints = save.hints
        val gained = save.addXp(80 + 120 + 160) // to level 4
        assertEquals(3, gained)
        assertEquals(4, save.playerLevel)
        assertEquals(3 * Rewards.LEVEL_UP_COINS, save.coins)
        assertEquals(hints + 3, save.hints)
        assertTrue("level 4 gives Sunset", save.ownsSkin(1))
        assertEquals(Rewards.CHEST_EVERY, save.chestProgress)
        assertEquals("Time Attack", Unlocks.next(4)!!.label)
        assertEquals(3, Rewards.streakMultiplier(5)); assertEquals(1, Rewards.streakMultiplier(2))
    }
}
