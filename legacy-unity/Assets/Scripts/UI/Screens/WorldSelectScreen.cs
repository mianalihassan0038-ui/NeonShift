using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>Five world cards. Worlds without levels yet show "Coming soon".</summary>
    public sealed class WorldSelectScreen : UIScreen
    {
        public static readonly string[] WorldNames = { "First Contact", "Duality", "Distortion", "Collapse", "Singularity" };
        static readonly Color[] WorldColors = { NeonTheme.Light, NeonTheme.Shadow, NeonTheme.Blue, NeonTheme.Hex(0xFF8A3D), NeonTheme.Pink };

        TextMesh coins;
        readonly List<TextMesh> subtitles = new List<TextMesh>();
        readonly List<WorldButton> cards = new List<WorldButton>();

        public static string WorldName(int worldId)
        {
            return worldId >= 1 && worldId <= WorldNames.Length ? WorldNames[worldId - 1] : "Daily";
        }

        protected override void Build()
        {
            Background(transform, NeonTheme.Shadow, NeonTheme.Light);
            Header("Select World", () => game.ShowScreen(AppScreen.MainMenu), out coins);
            for (int i = 0; i < WorldNames.Length; i++)
            {
                int worldId = i + 1;
                float y = 5.0f - i * 2.75f;
                var card = Button("", new Vector3(0f, y, 0f), new Vector2(7.6f, 2.3f), WorldColors[i], () => game.OpenWorld(worldId));
                cards.Add(card);
                // Art placeholder: a glowing "planet" on the left of the card.
                UIKit.Sprite("Planet", card.transform, NeonSprites.Glow, NeonTheme.WithAlpha(WorldColors[i], 0.9f), new Vector3(-2.4f, 0f, 0f), new Vector2(2.4f, 2.4f), 25);
                UIKit.Sprite("PlanetCore", card.transform, NeonSprites.Circle, NeonTheme.WithAlpha(WorldColors[i], 0.9f), new Vector3(-2.4f, 0f, 0f), new Vector2(1.0f, 1.0f), 26);
                UIKit.Text("Name", card.transform, new Vector3(-0.9f, 0.35f, 0f), 0.5f, NeonTheme.Text, TextAnchor.MiddleLeft, 26).text = worldId + ". " + WorldNames[i];
                subtitles.Add(UIKit.Text("Sub", card.transform, new Vector3(-0.9f, -0.4f, 0f), 0.36f, NeonTheme.Energy, TextAnchor.MiddleLeft, 26));
            }
        }

        public override void OnShow()
        {
            coins.text = SaveManager.Coins.ToString();
            for (int i = 0; i < cards.Count; i++)
            {
                int worldId = i + 1;
                int count = game.Levels.CountInWorld(worldId);
                bool unlocked = game.Levels.IsWorldUnlocked(worldId);
                cards[i].SetEnabled(unlocked);
                if (count == 0) subtitles[i].text = "Coming soon";
                else if (!unlocked) subtitles[i].text = "Locked";
                else subtitles[i].text = "★ " + game.Levels.StarsInWorld(worldId) + "/" + count * 3;
                subtitles[i].color = unlocked ? NeonTheme.Energy : NeonTheme.WithAlpha(NeonTheme.Text, 0.5f);
            }
        }
    }
}
