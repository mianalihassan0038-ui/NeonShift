using System.Collections.Generic;

namespace NeonShift
{
    /// <summary>
    /// Breadth-first search over (Light, Shadow) positions. Finds the shortest solution.
    /// Used by tests to prove every level is solvable, and later for hints / star tuning.
    /// </summary>
    public static class PuzzleSolver
    {
        /// <summary>Shortest list of swipes that solves the level, or null if unsolvable.</summary>
        public static List<Direction> Solve(LevelData level)
        {
            return Solve(level, level.light.start, level.shadow.start);
        }

        /// <summary>Shortest solution from any position (used by the Hint button).</summary>
        public static List<Direction> Solve(LevelData level, GridPos lightPos, GridPos shadowPos)
        {
            var lightBoard = new BoardState(level.gridWidth, level.gridHeight, level.light);
            var shadowBoard = new BoardState(level.gridWidth, level.gridHeight, level.shadow);

            var start = new KeyValuePair<GridPos, GridPos>(lightPos, shadowPos);
            var cameFrom = new Dictionary<KeyValuePair<GridPos, GridPos>, KeyValuePair<KeyValuePair<GridPos, GridPos>, Direction>>();
            var visited = new HashSet<KeyValuePair<GridPos, GridPos>> { start };
            var queue = new Queue<KeyValuePair<GridPos, GridPos>>();
            queue.Enqueue(start);

            while (queue.Count > 0)
            {
                var state = queue.Dequeue();
                if (state.Key == lightBoard.Exit && state.Value == shadowBoard.Exit)
                {
                    var path = new List<Direction>();
                    var cur = state;
                    while (!cur.Equals(start))
                    {
                        var prev = cameFrom[cur];
                        path.Add(prev.Value);
                        cur = prev.Key;
                    }
                    path.Reverse();
                    return path;
                }

                foreach (var dir in DirectionExtensions.All)
                {
                    GridPos l = state.Key.Step(dir);
                    GridPos s = state.Value.Step(dir.Opposite());
                    if (!CollisionChecker.CanEnter(lightBoard, l) || !CollisionChecker.CanEnter(shadowBoard, s)) continue;
                    var next = new KeyValuePair<GridPos, GridPos>(l, s);
                    if (!visited.Add(next)) continue;
                    cameFrom[next] = new KeyValuePair<KeyValuePair<GridPos, GridPos>, Direction>(state, dir);
                    queue.Enqueue(next);
                }
            }
            return null;
        }
    }
}
