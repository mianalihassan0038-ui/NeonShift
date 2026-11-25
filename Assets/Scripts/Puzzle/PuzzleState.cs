namespace NeonShift
{
    public enum MoveResult
    {
        Moved,
        Blocked,
        AlreadySolved
    }

    public struct MoveOutcome
    {
        public MoveResult result;
        public bool lightBlocked;
        public bool shadowBlocked;
        public GridPos lightFrom, lightTo;
        public GridPos shadowFrom, shadowTo;
        public bool solvedNow;
    }

    /// <summary>
    /// The live puzzle: both boards, both characters and the move counter.
    /// Implements the paired-movement rule from the plan:
    /// Light moves with the swipe, Shadow moves opposite, and both destinations
    /// are validated BEFORE either character moves. If either is blocked, nothing moves.
    /// </summary>
    public sealed class PuzzleState
    {
        public readonly LevelData Level;
        public readonly BoardState LightBoard;
        public readonly BoardState ShadowBoard;

        public GridPos LightPos { get; private set; }
        public GridPos ShadowPos { get; private set; }
        public int MoveCount { get; private set; }

        public bool IsSolved { get { return LightPos == LightBoard.Exit && ShadowPos == ShadowBoard.Exit; } }

        public PuzzleState(LevelData level)
        {
            Level = level;
            LightBoard = new BoardState(level.gridWidth, level.gridHeight, level.light);
            ShadowBoard = new BoardState(level.gridWidth, level.gridHeight, level.shadow);
            Reset();
        }

        public void Reset()
        {
            LightPos = LightBoard.Start;
            ShadowPos = ShadowBoard.Start;
            MoveCount = 0;
        }

        public MoveOutcome TryMove(Direction swipe)
        {
            var outcome = new MoveOutcome
            {
                lightFrom = LightPos,
                shadowFrom = ShadowPos,
                lightTo = LightPos,
                shadowTo = ShadowPos
            };

            if (IsSolved)
            {
                outcome.result = MoveResult.AlreadySolved;
                return outcome;
            }

            // 1. Calculate both candidate cells.
            GridPos lightTarget = LightPos.Step(swipe);
            GridPos shadowTarget = ShadowPos.Step(swipe.Opposite());

            // 2. Validate both before committing anything.
            outcome.lightBlocked = !CollisionChecker.CanEnter(LightBoard, lightTarget);
            outcome.shadowBlocked = !CollisionChecker.CanEnter(ShadowBoard, shadowTarget);

            if (outcome.lightBlocked || outcome.shadowBlocked)
            {
                outcome.result = MoveResult.Blocked; // Neither moves, counter unchanged.
                return outcome;
            }

            // 3. Commit both together.
            LightPos = lightTarget;
            ShadowPos = shadowTarget;
            MoveCount++;

            outcome.result = MoveResult.Moved;
            outcome.lightTo = lightTarget;
            outcome.shadowTo = shadowTarget;
            outcome.solvedNow = IsSolved;
            return outcome;
        }
    }
}
