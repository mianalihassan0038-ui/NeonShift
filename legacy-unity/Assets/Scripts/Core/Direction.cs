namespace NeonShift
{
    public enum Direction
    {
        Up,
        Down,
        Left,
        Right
    }

    public static class DirectionExtensions
    {
        /// <summary>Cell offset for one step. Rows grow downwards, so Up is y - 1.</summary>
        public static GridPos Delta(this Direction direction)
        {
            switch (direction)
            {
                case Direction.Up: return new GridPos(0, -1);
                case Direction.Down: return new GridPos(0, 1);
                case Direction.Left: return new GridPos(-1, 0);
                default: return new GridPos(1, 0);
            }
        }

        public static Direction Opposite(this Direction direction)
        {
            switch (direction)
            {
                case Direction.Up: return Direction.Down;
                case Direction.Down: return Direction.Up;
                case Direction.Left: return Direction.Right;
                default: return Direction.Left;
            }
        }

        public static readonly Direction[] All = { Direction.Up, Direction.Down, Direction.Left, Direction.Right };
    }
}
