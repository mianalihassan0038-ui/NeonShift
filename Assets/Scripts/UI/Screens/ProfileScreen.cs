using UnityEngine;

namespace NeonShift
{
    /// <summary>Player level (XP from stars and completions) and stats.</summary>
    public sealed class ProfileScreen : UIScreen
    {
        TextMesh coins, levelText, xpText, completed, stars, streak;
        Transform xpFill;

        protected override void Build()
        {
            Background(transform, NeonTheme.Shadow, NeonTheme.Light);
            Header("My Profile", () => game.ShowScreen(AppScreen.MainMenu), out coins);

            UIKit.Sprite("AvatarRing", transform, NeonSprites.Ring, NeonTheme.Shadow, new Vector3(0f, 4.4f, 0f), Vector2.one * 3.0f, 10);
            SplashScreen.Character(transform, NeonTheme.Shadow, new Vector3(0f, 4.4f, 0f), 2.4f);
            UIKit.Text("Name", transform, new Vector3(0f, 2.3f, 0f), 0.6f, NeonTheme.Text).text = "NeonPlayer";
            levelText = UIKit.Text("Level", transform, new Vector3(0f, 1.55f, 0f), 0.42f, NeonTheme.Light);
            UIKit.Sprite("XpTrack", transform, NeonSprites.RoundedTile, NeonTheme.WithAlpha(NeonTheme.Text, 0.15f), new Vector3(0f, 0.85f, 0f), new Vector2(5.5f, 0.32f), 10);
            xpFill = UIKit.Sprite("XpFill", transform, NeonSprites.Square, NeonTheme.Light, new Vector3(0f, 0.85f, 0f), new Vector2(0.01f, 0.18f), 11).transform;
            xpText = UIKit.Text("Xp", transform, new Vector3(0f, 0.3f, 0f), 0.28f, NeonTheme.WithAlpha(NeonTheme.Text, 0.7f));

            completed = Stat("Completed", -2.65f);
            stars = Stat("Stars", 0f);
            streak = Stat("Best Streak", 2.65f);

            UIKit.Card("Motto", transform, new Vector3(0f, -5.2f, 0f), new Vector2(7f, 1.2f), NeonTheme.Pink, 5);
            UIKit.Text("MottoText", transform, new Vector3(0f, -5.2f, 0f), 0.45f, NeonTheme.Text).text = "Think. Solve. Conquer.";
        }

        TextMesh Stat(string label, float x)
        {
            UIKit.Card("Stat", transform, new Vector3(x, -1.8f, 0f), new Vector2(2.45f, 2.2f), NeonTheme.Blue, 5);
            UIKit.Text("Label", transform, new Vector3(x, -1.25f, 0f), 0.3f, NeonTheme.WithAlpha(NeonTheme.Text, 0.75f)).text = label;
            return UIKit.Text("Value", transform, new Vector3(x, -2.1f, 0f), 0.8f, NeonTheme.Light);
        }

        public override void OnShow()
        {
            coins.text = SaveManager.Coins.ToString();
            ProgressSnapshot p = SaveManager.Snapshot(game.Levels.All);
            int totalStars = game.Levels.TotalStars;
            int xp = Rewards.Xp(p.levelsCompleted, totalStars);
            int into = Rewards.XpIntoLevel(xp);
            levelText.text = "Level " + Rewards.PlayerLevel(xp);
            xpText.text = into + " / " + Rewards.XpPerLevel + " XP";
            float w = 5.3f * into / Rewards.XpPerLevel;
            xpFill.localScale = new Vector3(Mathf.Max(0.01f, w), 0.18f, 1f);
            xpFill.localPosition = new Vector3(-2.65f + w * 0.5f, 0.85f, 0f);
            completed.text = p.levelsCompleted.ToString();
            stars.text = totalStars.ToString();
            streak.text = p.bestDailyStreak.ToString();
        }
    }
}
