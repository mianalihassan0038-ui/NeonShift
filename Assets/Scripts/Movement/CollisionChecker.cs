namespace NeonShift
{
    /// <summary>Board edges and walls block movement.</summary>
    public static class CollisionChecker
    {
        public static bool CanEnter(BoardState board, GridPos cell)
        {
            return board.InBounds(cell) && !board.IsWall(cell);
        }
    }
}
