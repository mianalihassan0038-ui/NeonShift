using System.Collections.Generic;

namespace NeonShift
{
    public enum TileType
    {
        Empty,
        Wall,
        Exit
    }

    /// <summary>Static layout of one board (Light or Shadow). Plain C#.</summary>
    public sealed class BoardState
    {
        public readonly int Width;
        public readonly int Height;
        public readonly GridPos Start;
        public readonly GridPos Exit;
        readonly HashSet<GridPos> walls;

        public BoardState(int width, int height, BoardData data)
        {
            Width = width;
            Height = height;
            Start = data.start;
            Exit = data.exit;
            walls = new HashSet<GridPos>(data.walls ?? new List<GridPos>());
        }

        public bool InBounds(GridPos p) { return p.x >= 0 && p.y >= 0 && p.x < Width && p.y < Height; }
        public bool IsWall(GridPos p) { return walls.Contains(p); }

        public TileType GetTile(GridPos p)
        {
            if (IsWall(p)) return TileType.Wall;
            if (p == Exit) return TileType.Exit;
            return TileType.Empty;
        }
    }
}
