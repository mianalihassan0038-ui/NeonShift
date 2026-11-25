using System;
using System.Collections.Generic;

namespace NeonShift
{
    /// <summary>
    /// Builds one puzzle per calendar day from the date, so every player gets the same daily level.
    /// Plain C#; the solver guarantees the result is solvable and needs a detour.
    /// </summary>
    public static class DailyChallenge
    {
        public const int MinMoves = 8;
        public const int MaxMoves = 13;

        public static int DayNumber(DateTime date) { return date.Year * 10000 + date.Month * 100 + date.Day; }

        public static LevelData Generate(int dayNumber)
        {
            var rng = new Random(dayNumber * 7919 + 17);
            for (int attempt = 0; attempt < 20000; attempt++)
            {
                LevelData level = RandomLevel(rng, dayNumber);
                if (level == null) continue;
                List<Direction> solution = PuzzleSolver.Solve(level);
                if (solution == null || solution.Count < MinMoves || solution.Count > MaxMoves) continue;
                int dx = Math.Abs(level.light.exit.x - level.light.start.x);
                int dy = Math.Abs(level.light.exit.y - level.light.start.y);
                if (solution.Count <= dx + dy) continue; // needs a detour
                int best = solution.Count;
                level.maxMoves = best + 5;
                level.starThresholds = new StarThresholds { threeStarsMaxMoves = best, twoStarsMaxMoves = best + 2, oneStarMaxMoves = best + 5 };
                return level;
            }
            return Fallback(dayNumber);
        }

        static LevelData RandomLevel(Random rng, int dayNumber)
        {
            const int size = 5;
            var ls = new GridPos(rng.Next(size), rng.Next(size));
            var ss = new GridPos(rng.Next(size), rng.Next(size));
            int dx = rng.Next(-4, 5), dy = rng.Next(-4, 5);
            if (dx == 0 && dy == 0) return null;
            var le = new GridPos(ls.x + dx, ls.y + dy);
            var se = new GridPos(ss.x - dx, ss.y - dy);
            if (!In(le, size) || !In(se, size)) return null;

            double density = 0.22 + rng.NextDouble() * 0.16;
            var level = new LevelData
            {
                levelId = 1,
                worldId = 0,
                hint = "Daily puzzle. Same for every player today.",
                light = new BoardData { start = ls, exit = le, walls = Walls(rng, size, density, ls, le) },
                shadow = new BoardData { start = ss, exit = se, walls = Walls(rng, size, density, ss, se) }
            };
            return level;
        }

        static List<GridPos> Walls(Random rng, int size, double density, GridPos a, GridPos b)
        {
            var walls = new List<GridPos>();
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                {
                    var p = new GridPos(x, y);
                    if (p != a && p != b && rng.NextDouble() < density) walls.Add(p);
                }
            return walls;
        }

        static bool In(GridPos p, int size) { return p.x >= 0 && p.y >= 0 && p.x < size && p.y < size; }

        static LevelData Fallback(int dayNumber)
        {
            return new LevelData
            {
                levelId = 1, worldId = 0, maxMoves = 0,
                light = new BoardData { start = new GridPos(0, 2), exit = new GridPos(4, 2) },
                shadow = new BoardData { start = new GridPos(4, 2), exit = new GridPos(0, 2) },
                starThresholds = new StarThresholds { threeStarsMaxMoves = 4, twoStarsMaxMoves = 6, oneStarMaxMoves = 9 }
            };
        }
    }
}
