using UnityEngine;

namespace NeonShift
{
    /// <summary>Today's puzzle preview, reward and streak.</summary>
    public sealed class DailyScreen : UIScreen
    {
        TextMesh coins, status, streak;
        BoardRenderer preview, previewShadow;
        WorldButton play;

        protected override void Build()
        {
            Background(transform, NeonTheme.Pink, NeonTheme.Light);
            Header("Daily Challenge", () => game.ShowScreen(AppScreen.MainMenu), out coins);
            UIKit.Text("Sub", transform, new Vector3(0f, 6.1f, 0f), 0.42f, NeonTheme.Text).text = "Solve today's puzzle!";

            preview = new GameObject("PreviewLight").AddComponent<BoardRenderer>();
            preview.transform.SetParent(transform, false);
            preview.transform.localPosition = new Vector3(-2.0f, 3.2f, 0f);
            preview.transform.localScale = Vector3.one * 0.62f;
            previewShadow = new GameObject("PreviewShadow").AddComponent<BoardRenderer>();
            previewShadow.transform.SetParent(transform, false);
            previewShadow.transform.localPosition = new Vector3(2.0f, 3.2f, 0f);
            previewShadow.transform.localScale = Vector3.one * 0.62f;

            UIKit.Text("RewardLabel", transform, new Vector3(0f, -0.4f, 0f), 0.42f, NeonTheme.Text).text = "Reward:";
            UIKit.CoinCounter(transform, new Vector3(-0.85f, -1.2f, 0f), 0.6f, 20).text = "+" + Rewards.DailyCoins;
            status = UIKit.Text("Status", transform, new Vector3(0f, -2.2f, 0f), 0.34f, NeonTheme.WithAlpha(NeonTheme.Text, 0.75f));
            play = Button("Play Now", new Vector3(0f, -3.7f, 0f), new Vector2(5.2f, 1.3f), NeonTheme.Success, () => game.StartDaily(), ButtonStyle.Filled);
            streak = UIKit.Text("Streak", transform, new Vector3(0f, -5.0f, 0f), 0.42f, NeonTheme.Text);
        }

        public override void OnShow()
        {
            coins.text = SaveManager.Coins.ToString();
            LevelData level = game.DailyLevel;
            var state = new PuzzleState(level);
            preview.Build(state.LightBoard, NeonTheme.Light);
            previewShadow.Build(state.ShadowBoard, NeonTheme.Shadow);
            SplashScreen.Character(preview.transform, NeonTheme.Light, preview.CellToLocal(level.light.start), 0.9f);
            SplashScreen.Character(previewShadow.transform, NeonTheme.Shadow, previewShadow.CellToLocal(level.shadow.start), 0.9f);

            bool done = game.DailyDoneToday;
            status.text = done ? "Done for today. Come back tomorrow!" : "Best solution: " + level.starThresholds.threeStarsMaxMoves + " moves";
            play.Label.text = done ? "Play Again" : "Play Now";
            streak.text = "Streak: " + SaveManager.ActiveDailyStreak(game.Today) + " days";
        }
    }
}
