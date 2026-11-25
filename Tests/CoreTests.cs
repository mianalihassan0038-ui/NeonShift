// Plain C# tests for the NEON SHIFT rules (no Unity needed).
// Run: tools/run-core-tests.sh
using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.Serialization.Json;
using System.Text;
using NeonShift;

static class CoreTests
{
    static int failures;
    static int passed;

    static void Check(bool condition, string name)
    {
        if (condition) { passed++; }
        else { failures++; Console.WriteLine("FAIL: " + name); }
    }

    static GridPos P(int x, int y) { return new GridPos(x, y); }

    static LevelData OpenLevel()
    {
        // Light in the middle, Shadow in the middle, no walls.
        var level = new LevelData();
        level.light.start = P(2, 2); level.light.exit = P(4, 2);
        level.shadow.start = P(2, 2); level.shadow.exit = P(0, 2);
        return level;
    }

    static void TestDirections()
    {
        var level = OpenLevel();
        foreach (var dir in DirectionExtensions.All)
        {
            var state = new PuzzleState(level);
            var outcome = state.TryMove(dir);
            Check(outcome.result == MoveResult.Moved, dir + ": accepted");
            Check(state.LightPos == P(2, 2).Step(dir), dir + ": Light follows swipe");
            Check(state.ShadowPos == P(2, 2).Step(dir.Opposite()), dir + ": Shadow moves opposite");
        }
        Check(P(2, 2).Step(Direction.Up) == P(2, 1), "Up decreases row (y = 0 is the top row)");
    }

    static void TestLightBlocked()
    {
        var level = OpenLevel();
        level.light.walls.Add(P(3, 2));
        var state = new PuzzleState(level);
        var outcome = state.TryMove(Direction.Right);
        Check(outcome.result == MoveResult.Blocked && outcome.lightBlocked && !outcome.shadowBlocked, "light wall reported");
        Check(state.LightPos == P(2, 2) && state.ShadowPos == P(2, 2), "light blocked: neither moves");
        Check(state.MoveCount == 0, "light blocked: counter unchanged");
    }

    static void TestShadowBlocked()
    {
        var level = OpenLevel();
        level.shadow.walls.Add(P(1, 2)); // Swipe right sends Shadow left into this wall.
        var state = new PuzzleState(level);
        var outcome = state.TryMove(Direction.Right);
        Check(outcome.result == MoveResult.Blocked && outcome.shadowBlocked && !outcome.lightBlocked, "shadow wall reported");
        Check(state.LightPos == P(2, 2) && state.ShadowPos == P(2, 2), "shadow blocked: neither moves");
        Check(state.MoveCount == 0, "shadow blocked: counter unchanged");
    }

    static void TestEdges()
    {
        var level = new LevelData();
        level.light.start = P(0, 0); level.light.exit = P(4, 4);
        level.shadow.start = P(2, 2); level.shadow.exit = P(0, 0);
        var state = new PuzzleState(level);
        Check(state.TryMove(Direction.Up).lightBlocked, "light top edge");
        Check(state.TryMove(Direction.Left).lightBlocked, "light left edge");

        level = new LevelData();
        level.light.start = P(2, 2); level.light.exit = P(0, 0);
        level.shadow.start = P(4, 4); level.shadow.exit = P(0, 0);
        state = new PuzzleState(level);
        Check(state.TryMove(Direction.Up).shadowBlocked, "shadow bottom edge (moves down on swipe up)");
        Check(state.TryMove(Direction.Left).shadowBlocked, "shadow right edge (moves right on swipe left)");
        Check(state.MoveCount == 0 && state.LightPos == P(2, 2), "edge blocks leave state untouched");
    }

    static void TestCounterAndWin()
    {
        var state = new PuzzleState(OpenLevel());
        state.TryMove(Direction.Right);
        Check(state.MoveCount == 1, "one accepted move counts once");
        Check(!state.IsSolved, "not solved halfway");
        var outcome = state.TryMove(Direction.Right);
        Check(state.MoveCount == 2 && outcome.solvedNow && state.IsSolved, "both on exits = win");
        Check(state.TryMove(Direction.Left).result == MoveResult.AlreadySolved && state.MoveCount == 2, "no moves after win");
        state.Reset();
        Check(state.MoveCount == 0 && state.LightPos == P(2, 2) && state.ShadowPos == P(2, 2), "reset restores start");
    }

    static void TestOnlyOneOnExitIsNotWin()
    {
        var level = OpenLevel();
        level.shadow.exit = P(0, 0);
        var state = new PuzzleState(level);
        state.TryMove(Direction.Right);
        state.TryMove(Direction.Right);
        Check(state.LightPos == level.light.exit && !state.IsSolved, "only Light on exit is not a win");
    }

    static void TestShippedLevels(string folder)
    {
        var files = Directory.GetFiles(folder, "*.json", SearchOption.AllDirectories);
        Array.Sort(files);
        Check(files.Length >= 5, "at least 5 levels shipped");
        var serializer = new DataContractJsonSerializer(typeof(LevelData));
        var ids = new HashSet<string>();
        foreach (var file in files)
        {
            var name = Path.GetFileName(file);
            LevelData level;
            using (var stream = new MemoryStream(Encoding.UTF8.GetBytes(File.ReadAllText(file))))
                level = (LevelData)serializer.ReadObject(stream);

            var errors = level.Validate();
            Check(errors.Count == 0, name + " valid: " + string.Join("; ", errors.ToArray()));
            Check(ids.Add(level.worldId + "-" + level.levelId), name + " has a unique world/level id");

            var solution = PuzzleSolver.Solve(level);
            Check(solution != null, name + " is solvable");
            if (solution == null) continue;

            // Replay the solution through the real game rules.
            var state = new PuzzleState(level);
            foreach (var dir in solution) state.TryMove(dir);
            Check(state.IsSolved && state.MoveCount == solution.Count, name + " solution replays");
            Check(level.starThresholds.threeStarsMaxMoves == solution.Count,
                name + " 3-star threshold equals best solution (" + solution.Count + ")");
            Console.WriteLine("  " + name + ": best " + solution.Count + " moves");
        }
    }

    static int Main(string[] args)
    {
        TestDirections();
        TestLightBlocked();
        TestShadowBlocked();
        TestEdges();
        TestCounterAndWin();
        TestOnlyOneOnExitIsNotWin();
        TestShippedLevels(args.Length > 0 ? args[0] : "Assets/Resources/Levels");
        Console.WriteLine(passed + " passed, " + failures + " failed");
        return failures == 0 ? 0 : 1;
    }
}
