using UnityEngine;

namespace NeonShift
{
    /// <summary>Draws one 5x5 board with SpriteRenderers and converts grid cells to positions.</summary>
    public sealed class BoardRenderer : MonoBehaviour
    {
        public const float CellSize = 1.1f;

        BoardState board;
        Color accent;
        SpriteRenderer exitGlow;

        public BoardState Board { get { return board; } }

        public void Build(BoardState boardState, Color accentColor)
        {
            board = boardState;
            accent = accentColor;

            for (int i = transform.childCount - 1; i >= 0; i--)
                Destroy(transform.GetChild(i).gameObject);

            float w = board.Width * CellSize;
            float h = board.Height * CellSize;

            // Soft halo + frame behind the grid.
            var halo = MakeSprite("Halo", NeonSprites.Glow, NeonTheme.WithAlpha(accent, 0.10f), 0);
            halo.transform.localScale = new Vector3(w * 1.6f, h * 1.6f, 1f);
            var frame = MakeSprite("Frame", NeonSprites.RoundedFrame, NeonTheme.WithAlpha(accent, 0.55f), 1);
            frame.transform.localScale = new Vector3(w + 0.28f, h + 0.28f, 1f);
            var inner = MakeSprite("Inner", NeonSprites.RoundedFrame, NeonTheme.Panel, 2);
            inner.transform.localScale = new Vector3(w + 0.16f, h + 0.16f, 1f);

            for (int y = 0; y < board.Height; y++)
            {
                for (int x = 0; x < board.Width; x++)
                {
                    var cell = new GridPos(x, y);
                    var pos = CellToLocal(cell);
                    TileType tile = board.GetTile(cell);

                    Color tileColor = tile == TileType.Wall ? NeonTheme.WallFor(accent) : NeonTheme.EmptyCell;
                    var sr = MakeSprite("Cell " + x + "," + y, NeonSprites.RoundedTile, tileColor, 3);
                    sr.transform.localPosition = pos;
                    sr.transform.localScale = Vector3.one * (CellSize * 0.9f);

                    if (tile == TileType.Exit)
                    {
                        exitGlow = MakeSprite("ExitGlow", NeonSprites.Glow, NeonTheme.WithAlpha(accent, 0.45f), 4);
                        exitGlow.transform.localPosition = pos;
                        exitGlow.transform.localScale = Vector3.one * (CellSize * 1.4f);
                        var ring = MakeSprite("Exit", NeonSprites.Ring, accent, 5);
                        ring.transform.localPosition = pos;
                        ring.transform.localScale = Vector3.one * (CellSize * 0.85f);
                    }
                }
            }
        }

        void Update()
        {
            if (exitGlow != null)
            {
                float a = 0.30f + 0.20f * Mathf.Sin(Time.time * 3f);
                exitGlow.color = NeonTheme.WithAlpha(accent, a);
            }
        }

        /// <summary>Local position of a cell centre. Row 0 is the top row.</summary>
        public Vector3 CellToLocal(GridPos p)
        {
            float ox = (board.Width - 1) * 0.5f;
            float oy = (board.Height - 1) * 0.5f;
            return new Vector3((p.x - ox) * CellSize, (oy - p.y) * CellSize, 0f);
        }

        SpriteRenderer MakeSprite(string name, Sprite sprite, Color color, int order)
        {
            var go = new GameObject(name);
            go.transform.SetParent(transform, false);
            var sr = go.AddComponent<SpriteRenderer>();
            sr.sprite = sprite;
            sr.color = color;
            sr.sortingOrder = order;
            return sr;
        }
    }
}
