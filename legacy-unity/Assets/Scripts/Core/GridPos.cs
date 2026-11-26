using System;

namespace NeonShift
{
    /// <summary>
    /// A cell coordinate on a board. x = column (0 = left), y = row (0 = TOP).
    /// The same convention is used by the level JSON files.
    /// Plain C# (no UnityEngine) so the rules can be unit tested outside Unity.
    /// </summary>
    [Serializable]
    public struct GridPos : IEquatable<GridPos>
    {
        public int x;
        public int y;

        public GridPos(int x, int y)
        {
            this.x = x;
            this.y = y;
        }

        public GridPos Step(Direction direction)
        {
            GridPos d = direction.Delta();
            return new GridPos(x + d.x, y + d.y);
        }

        public bool Equals(GridPos other) { return x == other.x && y == other.y; }
        public override bool Equals(object obj) { return obj is GridPos && Equals((GridPos)obj); }
        public override int GetHashCode() { return (x * 397) ^ y; }
        public static bool operator ==(GridPos a, GridPos b) { return a.Equals(b); }
        public static bool operator !=(GridPos a, GridPos b) { return !a.Equals(b); }
        public override string ToString() { return "(" + x + "," + y + ")"; }
    }
}
