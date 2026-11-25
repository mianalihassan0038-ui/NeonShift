using UnityEngine;

namespace NeonShift
{
    /// <summary>Main menu: profile, coins, settings, logo, big PLAY, Daily Challenge, Worlds, Achievements, Settings.</summary>
    public sealed class MainMenuScreen : UIScreen
    {
        TextMesh coins, playSub, dailyBadge;
        Transform playGlow;

        protected override void Build()
        {
            Background(transform, NeonTheme.Light, NeonTheme.Shadow);
            var profile = Button("", new Vector3(-3.7f, 7.3f, 0f), new Vector2(1.0f, 1.0f), NeonTheme.Energy, () => game.ShowScreen(AppScreen.Profile), ButtonStyle.Round);
            SplashScreen.Character(profile.transform, NeonTheme.Shadow, Vector3.zero, 0.7f);
            foreach (var sr in profile.GetComponentsInChildren<SpriteRenderer>()) if (sr.sortingOrder >= 12) sr.sortingOrder += 12;
            coins = UIKit.CoinCounter(transform, new Vector3(-1.2f, 7.3f, 0f), 0.42f, 20);
            var gear = Button("", new Vector3(3.7f, 7.3f, 0f), new Vector2(1.0f, 1.0f), NeonTheme.Light, () => game.ShowScreen(AppScreen.Settings), ButtonStyle.Round);
            UIKit.Sprite("GearRing", gear.transform, NeonSprites.Ring, NeonTheme.Light, Vector3.zero, Vector2.one * 0.62f, 26);
            UIKit.Sprite("GearHub", gear.transform, NeonSprites.Circle, NeonTheme.Light, Vector3.zero, Vector2.one * 0.22f, 26);

            SplashScreen.BuildLogo(transform, 5.2f, 1.5f);

            playGlow = UIKit.Sprite("PlayGlow", transform, NeonSprites.Glow, NeonTheme.WithAlpha(NeonTheme.Light, 0.45f), new Vector3(0f, 0.7f, 0f), new Vector2(5.5f, 4.5f), 8).transform;
            Button("▶  PLAY", new Vector3(0f, 0.7f, 0f), new Vector2(4.6f, 1.9f), NeonTheme.Light, () => game.Continue(), ButtonStyle.Outline);
            playSub = UIKit.Text("PlaySub", transform, new Vector3(0f, -0.55f, 0f), 0.3f, NeonTheme.WithAlpha(NeonTheme.Text, 0.6f));

            Button("Daily Challenge", new Vector3(0f, -1.85f, 0f), new Vector2(5.6f, 1.05f), NeonTheme.Pink, () => game.ShowScreen(AppScreen.Daily));
            dailyBadge = UIKit.Text("DailyBadge", transform, new Vector3(2.3f, -1.4f, 0f), 0.3f, NeonTheme.Energy, TextAnchor.MiddleCenter, 30);
            Button("Worlds", new Vector3(0f, -3.15f, 0f), new Vector2(5.6f, 1.05f), NeonTheme.Light, () => game.ShowScreen(AppScreen.WorldSelect));
            Button("Achievements", new Vector3(0f, -4.45f, 0f), new Vector2(5.6f, 1.05f), NeonTheme.Shadow, () => game.ShowScreen(AppScreen.Achievements));
            Button("Settings", new Vector3(0f, -5.75f, 0f), new Vector2(5.6f, 1.05f), NeonTheme.Blue, () => game.ShowScreen(AppScreen.Settings));

            UIKit.Text("Footer", transform, new Vector3(0f, -7.3f, 0f), 0.28f, NeonTheme.WithAlpha(NeonTheme.Text, 0.4f)).text = "Think. Solve. Conquer.";
        }

        public override void OnShow()
        {
            coins.text = SaveManager.Coins.ToString();
            LevelData current = game.Levels.Current;
            playSub.text = current == null ? "" : "World " + current.worldId + "  ·  Level " + current.levelId;
            dailyBadge.text = game.DailyDoneToday ? "✓" : "NEW";
        }

        public override void Tick()
        {
            float k = 1f + 0.06f * Mathf.Sin(Time.time * 2.5f);
            playGlow.localScale = new Vector3(5.5f * k, 4.5f * k, 1f);
        }
    }
}
