namespace NeonShift
{
    /// <summary>Stars from the per-level thresholds. A completed level always earns at least 1 star.</summary>
    public static class StarManager
    {
        public static int Calculate(LevelData level, int moves)
        {
            StarThresholds t = level.starThresholds;
            if (t == null || t.threeStarsMaxMoves <= 0) return 3;
            if (moves <= t.threeStarsMaxMoves) return 3;
            if (moves <= t.twoStarsMaxMoves) return 2;
            return 1;
        }
    }
}
