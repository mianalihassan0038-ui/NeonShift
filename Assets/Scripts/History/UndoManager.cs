using System.Collections.Generic;

namespace NeonShift
{
    /// <summary>Complete puzzle state at one moment. Add collected energy nodes here if they are added.</summary>
    public struct PuzzleSnapshot
    {
        public readonly GridPos LightPos;
        public readonly GridPos ShadowPos;
        public readonly int MoveCount;

        public PuzzleSnapshot(GridPos light, GridPos shadow, int moveCount)
        {
            LightPos = light;
            ShadowPos = shadow;
            MoveCount = moveCount;
        }
    }

    /// <summary>Stack of snapshots taken before each accepted move.</summary>
    public sealed class UndoManager
    {
        readonly Stack<PuzzleSnapshot> stack = new Stack<PuzzleSnapshot>();

        public int Count { get { return stack.Count; } }
        public void Push(PuzzleSnapshot s) { stack.Push(s); }
        public void Clear() { stack.Clear(); }

        public bool TryPop(out PuzzleSnapshot s)
        {
            if (stack.Count == 0) { s = default(PuzzleSnapshot); return false; }
            s = stack.Pop();
            return true;
        }
    }
}
