using System;
using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>Grid of level tiles. Locked levels are dimmed; completed levels show their best stars.</summary>
    public sealed class LevelSelectUI : MonoBehaviour
    {
        const int Columns = 4;
        const float TileSize = 1.75f;
        const float Spacing = 2.05f;

        public Action<int> LevelChosen;
        public Action BackPressed;

        Transform grid;
        TextMesh worldLabel;
        readonly List<WorldButton> buttons = new List<WorldButton>();
        WorldButton back;

        public void Build()
        {
            GameplayUI.MakeText("Title", transform, new Vector3(0f, 7.0f, 0f), 0.75f, NeonTheme.Text, TextAnchor.MiddleCenter, 20).text = "LEVELS";
            worldLabel = GameplayUI.MakeText("World", transform, new Vector3(0f, 5.9f, 0f), 0.4f, NeonTheme.Light, TextAnchor.MiddleCenter, 20);
            back = WorldButton.Create(transform, "BACK", new Vector3(0f, -7.4f, 0f), new Vector2(3.4f, 1.0f), NeonTheme.Shadow, 20);
            back.Clicked = () => { if (BackPressed != null) BackPressed(); };
        }

        public void Show(LevelManager levels)
        {
            if (grid != null) Destroy(grid.gameObject);
            grid = new GameObject("Grid").transform;
            grid.SetParent(transform, false);
            buttons.Clear();
            buttons.Add(back);

            worldLabel.text = "WORLD 01  ·  FIRST CONTACT";
            int rows = (levels.Count + Columns - 1) / Columns;
            float top = 4.2f;

            for (int i = 0; i < levels.Count; i++)
            {
                int col = i % Columns, row = i / Columns;
                var pos = new Vector3((col - (Columns - 1) * 0.5f) * Spacing, top - row * (Spacing + 0.45f), 0f);
                bool unlocked = levels.IsUnlocked(i);
                bool current = i == levels.CurrentIndex;
                Color accent = current ? NeonTheme.Energy : NeonTheme.Light;

                LevelData level = levels.All[i];
                var tile = WorldButton.Create(grid, unlocked ? level.levelId.ToString() : "-", pos, new Vector2(TileSize, TileSize), accent, 20);
                tile.SetEnabled(unlocked);
                int index = i;
                tile.Clicked = () => { if (LevelChosen != null) LevelChosen(index); };
                buttons.Add(tile);

                int stars = SaveManager.GetStars(level);
                for (int s = 0; s < 3; s++)
                {
                    Color c = s < stars ? NeonTheme.Energy : NeonTheme.WithAlpha(NeonTheme.Text, unlocked ? 0.15f : 0.05f);
                    GameplayUI.MakeSprite("Star", grid, NeonSprites.Star, c, pos + new Vector3((s - 1) * 0.5f, -TileSize * 0.5f - 0.32f, 0f), Vector2.one * 0.45f, 20);
                }
            }

            if (rows == 0) worldLabel.text = "No levels found";
        }

        public bool HandleTap(Vector3 worldPoint) { return WorldButton.TapAny(buttons, worldPoint); }
    }
}
