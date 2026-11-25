namespace NeonShift
{
    public enum MoveResult
    {
        Moved,
        Blocked,
        AlreadySolved,
        OutOfMoves
    }

    public struct MoveOutcome
    {
        public MoveResult result;
        public bool lightBlocked;
        public bool shadowBlocked;
        public GridPos lightFrom, lightTo;
        public GridPos shadowFrom, shadowTo;
        public bool solvedNow;
        public bool failedNow; // this move used the last allowed move without solving
    }

    /// <summary>
    /// The live puzzle: both boards, both characters and the move counter.
    /// Implements the paired-movement rule from the plan:
    /// Light moves with the swipe, Shadow moves opposite, and both destinations
    /// are validated BEFORE either character moves. If either is blocked, nothing moves.
    /// Build 002 adds undo (full-state snapshots) and an optional move limit.
    /// </summary>
    public sealed class PuzzleState
    {
        public readonly LevelData Level;
        public readonly BoardState LightBoard;
        public readonly BoardState ShadowBoard;
        readonly UndoManager history = new UndoManager();

        public GridPos LightPos { get; private set; }
        public GridPos ShadowPos { get; private set; }
        public int MoveCount { get; private set; }

        /// <summary>0 means no limit.</summary>
        public int MaxMoves { get { return Level.maxMoves; } }
        public bool HasMoveLimit { get { return Level.maxMoves > 0; } }
        public int MovesLeft { get { return HasMoveLimit ? System.Math.Max(0, Level.maxMoves - MoveCount) : int.MaxValue; } }

        public bool IsSolved { get { return LightPos == LightBoard.Exit && ShadowPos == ShadowBoard.Exit; } }
        public bool IsFailed { get { return !IsSolved && HasMoveLimit && MoveCount >= Level.maxMoves; } }
        public bool CanUndo { get { return history.Count > 0; } }

        public PuzzleState(LevelData level)
        {
            Level = level;
            LightBoard = new BoardState(level.gridWidth, level.gridHeight, level.light);
            ShadowBoard = new BoardState(level.gridWidth, level.gridHeight, level.shadow);
            Reset();
        }

        /// <summary>Restart: both characters back to start, counter and history cleared.</summary>
        public void Reset()
        {
            LightPos = LightBoard.Start;
            ShadowPos = ShadowBoard.Start;
            MoveCount = 0;
            history.Clear();
        }

        public PuzzleSnapshot Capture()
        {
            return new PuzzleSnapshot(LightPos, ShadowPos, MoveCount);
        }

        void Restore(PuzzleSnapshot s)
        {
            LightPos = s.LightPos;
            ShadowPos = s.ShadowPos;
            MoveCount = s.MoveCount;
        }

        /// <summary>Steps back one accepted move. Works after a win or a failure too.</summary>
        public bool Undo()
        {
            PuzzleSnapshot s;
            if (!history.TryPop(out s)) return false;
            Restore(s);
            return true;
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

            if (IsSolved) { outcome.result = MoveResult.AlreadySolved; return outcome; }
            if (IsFailed) { outcome.result = MoveResult.OutOfMoves; return outcome; }

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
            history.Push(Capture());
            LightPos = lightTarget;
            ShadowPos = shadowTarget;
            MoveCount++;

            outcome.result = MoveResult.Moved;
            outcome.lightTo = lightTarget;
            outcome.shadowTo = shadowTarget;
            outcome.solvedNow = IsSolved;
            outcome.failedNow = IsFailed;
            return outcome;
        }
    }
}
