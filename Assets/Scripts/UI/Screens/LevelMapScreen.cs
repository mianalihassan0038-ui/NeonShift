using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>World map: level nodes along a winding path from the bottom up, with stars.</summary>
    public sealed class LevelMapScreen : UIScreen
    {
        TextMesh title, starTotal;
        Transform content;
        Transform pulse;
        int worldId = 1;

        protected override void Build()
        {
            Background(transform, NeonTheme.Light, NeonTheme.Shadow);
            Button("←", new Vector3(-3.75f, 7.35f, 0f), new Vector2(1.0f, 1.0f), NeonTheme.Light, () => game.ShowScreen(AppScreen.WorldSelect), ButtonStyle.Round);
            title = UIKit.Text("Title", transform, new Vector3(0f, 7.35f, 0f), 0.5f, NeonTheme.Text);
            UIKit.Sprite("StarIcon", transform, NeonSprites.Star, NeonTheme.Energy, new Vector3(-0.9f, -7.4f, 0f), Vector2.one * 0.65f, 20);
            starTotal = UIKit.Text("Stars", transform, new Vector3(-0.45f, -7.4f, 0f), 0.45f, NeonTheme.Text, TextAnchor.MiddleLeft);
        }

        public void SetWorld(int id) { worldId = id; }

        public override void OnShow()
        {
            if (content != null) Destroy(content.gameObject);
            content = new GameObject("Map").transform;
            content.SetParent(transform, false);
            buttons.RemoveAll(b => b == null || b.transform.parent == null || b.transform.parent.name == "Map");
            pulse = null;

            title.text = "World " + worldId + "\n" + WorldSelectScreen.WorldName(worldId);
            List<int> indices = game.Levels.IndicesInWorld(worldId);
            int n = indices.Count;
            var points = new List<Vector3>();
            for (int k = 0; k < n; k++)
            {
                float t = n <= 1 ? 0f : (float)k / (n - 1);
                float y = Mathf.Lerp(-5.6f, 5.4f, t);
                float x = Mathf.Sin(t * Mathf.PI * 2.2f) * 2.4f;
                points.Add(new Vector3(x, y, 0f));
            }

            // Path segments.
            for (int k = 0; k + 1 < n; k++)
            {
                Vector3 a = points[k], b = points[k + 1];
                Vector3 mid = (a + b) * 0.5f;
                float len = Vector3.Distance(a, b);
                bool done = game.Levels.IsUnlocked(indices[k + 1]);
                var seg = UIKit.Sprite("Path", content, NeonSprites.Square, NeonTheme.WithAlpha(done ? NeonTheme.Light : NeonTheme.Text, done ? 0.6f : 0.15f), mid, new Vector2(len, 0.12f), 5);
                seg.transform.localRotation = Quaternion.Euler(0f, 0f, Mathf.Atan2(b.y - a.y, b.x - a.x) * Mathf.Rad2Deg);
            }

            int stars = 0;
            for (int k = 0; k < n; k++)
            {
                int index = indices[k];
                LevelData level = game.Levels.All[index];
                bool unlocked = game.Levels.IsUnlocked(index);
                bool current = index == game.Levels.CurrentIndex;
                int s = SaveManager.GetStars(level);
                stars += s;
                Color accent = current ? NeonTheme.Energy : (s > 0 ? NeonTheme.Light : NeonTheme.Blue);
                var node = WorldButton.Create(content, unlocked ? level.levelId.ToString() : "×", points[k], new Vector2(1.15f, 1.15f), accent, 20, ButtonStyle.Round);
                node.SetEnabled(unlocked);
                node.Clicked = () => game.StartLevel(index);
                buttons.Add(node);
                UIKit.Text("Stars", content, points[k] + new Vector3(0f, -0.85f, 0f), 0.3f, s > 0 ? NeonTheme.Energy : NeonTheme.WithAlpha(NeonTheme.Text, 0.25f), TextAnchor.MiddleCenter, 26).text = UIKit.Stars(s);
                if (current)
                    pulse = UIKit.Sprite("Pulse", content, NeonSprites.Ring, NeonTheme.Energy, points[k], Vector2.one * 1.5f, 19).transform;
            }
            starTotal.text = stars + " / " + n * 3;
        }

        public override void Tick()
        {
            if (pulse == null) return;
            float k = Mathf.Repeat(Time.time, 1f);
            pulse.localScale = Vector3.one * (1.3f + 0.6f * k);
            var sr = pulse.GetComponent<SpriteRenderer>();
            sr.color = NeonTheme.WithAlpha(NeonTheme.Energy, 1f - k);
        }
    }
}
