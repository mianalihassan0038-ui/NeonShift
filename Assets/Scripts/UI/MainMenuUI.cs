using System;
using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>Title screen: PLAY (continue), LEVELS, and total stars.</summary>
    public sealed class MainMenuUI : MonoBehaviour
    {
        public Action PlayPressed;
        public Action LevelsPressed;

        TextMesh starsLabel, playSubLabel;
        SpriteRenderer lightGlow, shadowGlow;
        readonly List<WorldButton> buttons = new List<WorldButton>();

        public void Build()
        {
            lightGlow = GameplayUI.MakeSprite("LightGlow", transform, NeonSprites.Glow, NeonTheme.WithAlpha(NeonTheme.Light, 0.25f), new Vector3(-1.6f, 4.3f, 0f), new Vector2(7f, 5f), 0);
            shadowGlow = GameplayUI.MakeSprite("ShadowGlow", transform, NeonSprites.Glow, NeonTheme.WithAlpha(NeonTheme.Shadow, 0.25f), new Vector3(1.6f, 2.9f, 0f), new Vector2(7f, 5f), 0);

            GameplayUI.MakeText("TitleNeon", transform, new Vector3(0f, 4.4f, 0f), 1.5f, NeonTheme.Light, TextAnchor.MiddleCenter, 20).text = "NEON";
            GameplayUI.MakeText("TitleShift", transform, new Vector3(0f, 2.8f, 0f), 1.5f, NeonTheme.Shadow, TextAnchor.MiddleCenter, 20).text = "SHIFT";
            GameplayUI.MakeText("Tagline", transform, new Vector3(0f, 1.5f, 0f), 0.34f, NeonTheme.WithAlpha(NeonTheme.Text, 0.7f), TextAnchor.MiddleCenter, 20).text = "One swipe. Two worlds. Opposite moves.";

            var play = WorldButton.Create(transform, "PLAY", new Vector3(0f, -1.0f, 0f), new Vector2(5f, 1.4f), NeonTheme.Light, 20);
            play.Clicked = () => { if (PlayPressed != null) PlayPressed(); };
            playSubLabel = GameplayUI.MakeText("PlaySub", transform, new Vector3(0f, -2.05f, 0f), 0.3f, NeonTheme.WithAlpha(NeonTheme.Text, 0.6f), TextAnchor.MiddleCenter, 20);

            var levelsButton = WorldButton.Create(transform, "LEVELS", new Vector3(0f, -3.4f, 0f), new Vector2(5f, 1.2f), NeonTheme.Shadow, 20);
            levelsButton.Clicked = () => { if (LevelsPressed != null) LevelsPressed(); };
            buttons.Add(play);
            buttons.Add(levelsButton);

            GameplayUI.MakeSprite("StarIcon", transform, NeonSprites.Star, NeonTheme.Energy, new Vector3(-0.9f, -5.3f, 0f), Vector2.one * 0.7f, 20);
            starsLabel = GameplayUI.MakeText("Stars", transform, new Vector3(-0.4f, -5.3f, 0f), 0.45f, NeonTheme.Energy, TextAnchor.MiddleLeft, 20);
        }

        public void Show(int totalStars, int maxStars, LevelData current)
        {
            starsLabel.text = totalStars + " / " + maxStars;
            playSubLabel.text = current == null ? "" : "Level " + current.worldId + "-" + current.levelId;
        }

        void Update()
        {
            float t = Time.time;
            lightGlow.color = NeonTheme.WithAlpha(NeonTheme.Light, 0.2f + 0.08f * Mathf.Sin(t * 1.7f));
            shadowGlow.color = NeonTheme.WithAlpha(NeonTheme.Shadow, 0.2f + 0.08f * Mathf.Sin(t * 1.7f + Mathf.PI));
        }

        public bool HandleTap(Vector3 worldPoint) { return WorldButton.TapAny(buttons, worldPoint); }
    }
}
