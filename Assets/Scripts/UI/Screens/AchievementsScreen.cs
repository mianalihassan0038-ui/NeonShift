using UnityEngine;

namespace NeonShift
{
    /// <summary>Local achievements with progress bars.</summary>
    public sealed class AchievementsScreen : UIScreen
    {
        TextMesh coins;
        Transform list;

        protected override void Build()
        {
            Background(transform, NeonTheme.Energy, NeonTheme.Shadow);
            Header("Achievements", () => game.ShowScreen(AppScreen.MainMenu), out coins);
        }

        public override void OnShow()
        {
            coins.text = SaveManager.Coins.ToString();
            if (list != null) Destroy(list.gameObject);
            list = new GameObject("List").transform;
            list.SetParent(transform, false);

            Achievement[] all = Achievements.Evaluate(SaveManager.Snapshot(game.Levels.All));
            for (int i = 0; i < all.Length; i++)
            {
                Achievement a = all[i];
                float y = 5.4f - i * 2.15f;
                Color accent = a.Unlocked ? NeonTheme.Energy : NeonTheme.Shadow;
                var card = UIKit.Card("Card", list, new Vector3(0f, y, 0f), new Vector2(7.8f, 1.9f), accent, 5, 0.95f);
                UIKit.Sprite("Badge", card, a.Unlocked ? NeonSprites.Star : NeonSprites.Ring, accent, new Vector3(-3.0f, 0f, 0f), Vector2.one * 1.1f, 12);
                UIKit.Text("Title", card, new Vector3(-2.2f, 0.45f, 0f), 0.42f, NeonTheme.Text, TextAnchor.MiddleLeft, 12).text = a.title;
                UIKit.Text("Desc", card, new Vector3(-2.2f, -0.05f, 0f), 0.3f, NeonTheme.WithAlpha(NeonTheme.Text, 0.7f), TextAnchor.MiddleLeft, 12).text = a.description;
                float w = 4.2f;
                float t = Mathf.Clamp01((float)a.progress / a.goal);
                UIKit.Sprite("Track", card, NeonSprites.Square, NeonTheme.WithAlpha(NeonTheme.Text, 0.12f), new Vector3(-2.2f + w * 0.5f, -0.55f, 0f), new Vector2(w, 0.16f), 12);
                if (t > 0f) UIKit.Sprite("Fill", card, NeonSprites.Square, accent, new Vector3(-2.2f + w * t * 0.5f, -0.55f, 0f), new Vector2(w * t, 0.16f), 13);
                UIKit.Text("Count", card, new Vector3(3.6f, -0.55f, 0f), 0.3f, NeonTheme.Text, TextAnchor.MiddleRight, 12).text = a.progress + " / " + a.goal;
            }
        }
    }
}
