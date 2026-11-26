using System;
using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// Gameplay HUD and its modal panels, styled after the NEON SHIFT mockups:
    /// top bar (back, level + live star preview, pause, moves), round Undo / Hint / Restart buttons,
    /// and Pause, Level Complete, Level Failed and Hint panels.
    /// Layout is designed for a 9 x 16 world-unit portrait area (see GameManager.FitCamera).
    /// </summary>
    public sealed class GameplayUI : MonoBehaviour
    {
        TextMesh levelLabel, starPreview, movesLabel, hintLabel, hintButtonCount;
        WorldButton undoButton, failureUndoButton;

        GameObject pausePanel, victoryPanel, failurePanel, hintPanel;
        TextMesh victoryTitle, victoryDetail, victoryCoins, failureDetail, hintText, hintCost;
        WorldButton nextButton;
        SpriteRenderer[] stars = new SpriteRenderer[3];
        UISlider musicSlider, soundSlider;

        readonly List<WorldButton> hudButtons = new List<WorldButton>();
        readonly List<WorldButton> pauseButtons = new List<WorldButton>();
        readonly List<WorldButton> victoryButtons = new List<WorldButton>();
        readonly List<WorldButton> failureButtons = new List<WorldButton>();
        readonly List<WorldButton> hintButtons = new List<WorldButton>();

        public Action BackPressed, PausePressed, UndoPressed, HintPressed, RestartPressed;
        public Action ResumePressed, LevelsPressed, MenuPressed, NextPressed, HintConfirmed;
        public Action<float> MusicVolumeChanged, SoundVolumeChanged;

        // Kept for older call sites.
        public static TextMesh MakeText(string name, Transform parent, Vector3 pos, float height, Color color, TextAnchor anchor, int order)
        {
            return UIKit.Text(name, parent, pos, height, color, anchor, order);
        }

        public static SpriteRenderer MakeSprite(string name, Transform parent, Sprite sprite, Color color, Vector3 pos, Vector2 scale, int order)
        {
            return UIKit.Sprite(name, parent, sprite, color, pos, scale, order);
        }

        static void Invoke(Action a) { if (a != null) a(); }

        WorldButton Add(List<WorldButton> list, Transform parent, string text, Vector3 pos, Vector2 size, Color accent, Action click,
                        ButtonStyle style = ButtonStyle.Outline, int order = 20, string caption = null)
        {
            var b = WorldButton.Create(parent, text, pos, size, accent, order, style, caption);
            b.Clicked = click;
            list.Add(b);
            return b;
        }

        public void Build()
        {
            // Top bar.
            Add(hudButtons, transform, "←", new Vector3(-3.75f, 7.45f, 0f), new Vector2(0.95f, 0.95f), NeonTheme.Light, () => Invoke(BackPressed), ButtonStyle.Round);
            Add(hudButtons, transform, "II", new Vector3(3.75f, 7.45f, 0f), new Vector2(0.95f, 0.95f), NeonTheme.Light, () => Invoke(PausePressed), ButtonStyle.Round);
            UIKit.Card("TitleCard", transform, new Vector3(0f, 7.25f, 0f), new Vector2(4.2f, 1.5f), NeonTheme.Light, 2, 0.9f);
            levelLabel = UIKit.Text("LevelLabel", transform, new Vector3(0f, 7.6f, 0f), 0.48f, NeonTheme.Text);
            starPreview = UIKit.Text("StarPreview", transform, new Vector3(0f, 7.0f, 0f), 0.42f, NeonTheme.Energy);
            movesLabel = UIKit.Text("MoveCounter", transform, new Vector3(0f, 6.2f, 0f), 0.4f, NeonTheme.Text);

            // Between the boards.
            hintLabel = UIKit.Text("Hint", transform, new Vector3(0f, -0.27f, 0f), 0.28f, NeonTheme.WithAlpha(NeonTheme.Text, 0.8f));

            // Bottom controls.
            undoButton = Add(hudButtons, transform, "↺", new Vector3(-2.6f, -7.25f, 0f), new Vector2(1.0f, 1.0f), NeonTheme.Light, () => Invoke(UndoPressed), ButtonStyle.Round, 20, "Undo");
            Add(hudButtons, transform, "?", new Vector3(0f, -7.25f, 0f), new Vector2(1.0f, 1.0f), NeonTheme.Energy, () => Invoke(HintPressed), ButtonStyle.Round, 20, "Hint");
            hintButtonCount = UIKit.Text("HintCount", transform, new Vector3(0.48f, -6.82f, 0f), 0.26f, NeonTheme.Background, TextAnchor.MiddleCenter, 31);
            UIKit.Sprite("HintBadge", transform, NeonSprites.Circle, NeonTheme.Energy, new Vector3(0.48f, -6.82f, 0f), Vector2.one * 0.4f, 30);
            Add(hudButtons, transform, "↻", new Vector3(2.6f, -7.25f, 0f), new Vector2(1.0f, 1.0f), NeonTheme.Shadow, () => Invoke(RestartPressed), ButtonStyle.Round, 20, "Restart");

            BuildPausePanel();
            BuildVictoryPanel();
            BuildFailurePanel();
            BuildHintPanel();
        }

        GameObject PanelRoot(string name)
        {
            var panel = new GameObject(name);
            panel.transform.SetParent(transform, false);
            UIKit.Dim(panel.transform, 50);
            panel.SetActive(false);
            return panel;
        }

        void BuildPausePanel()
        {
            pausePanel = PanelRoot("PausePanel");
            var t = pausePanel.transform;
            UIKit.Card("Frame", t, new Vector3(0f, 0.2f, 0f), new Vector2(7.4f, 10.6f), NeonTheme.Shadow, 51);
            UIKit.Card("Inner", t, new Vector3(0f, -0.1f, 0f), new Vector2(6.2f, 8.6f), NeonTheme.Light, 55, 0.95f);
            UIKit.Sprite("Tab", t, NeonSprites.RoundedFrame, NeonTheme.Shadow, new Vector3(-1.4f, 5.35f, 0f), new Vector2(3.8f, 1.1f), 60);
            UIKit.Text("Title", t, new Vector3(-1.4f, 5.35f, 0f), 0.7f, NeonTheme.Light, TextAnchor.MiddleCenter, 61).text = "PAUSE";
            Add(pauseButtons, t, "×", new Vector3(2.95f, 5.35f, 0f), new Vector2(1.1f, 1.1f), NeonTheme.Light, () => Invoke(ResumePressed), ButtonStyle.Outline, 60);

            string[] labels = { "CONTINUE", "REPLAY", "LEVEL SELECT", "MAIN MENU" };
            Action[] actions = { () => Invoke(ResumePressed), () => Invoke(RestartPressed), () => Invoke(LevelsPressed), () => Invoke(MenuPressed) };
            for (int i = 0; i < labels.Length; i++)
                Add(pauseButtons, t, labels[i], new Vector3(0f, 2.9f - i * 1.4f, 0f), new Vector2(4.6f, 1.05f), NeonTheme.Pink, actions[i], ButtonStyle.Outline, 60);

            UIKit.Sprite("Divider", t, NeonSprites.Glow, NeonTheme.Light, new Vector3(0f, -2.75f, 0f), new Vector2(4.5f, 0.15f), 60);
            musicSlider = UISlider.Create(t, "Music", new Vector3(0.8f, -3.5f, 0f), 3.4f, NeonTheme.Light, 60);
            soundSlider = UISlider.Create(t, "Sound", new Vector3(0.8f, -4.35f, 0f), 3.4f, NeonTheme.Light, 60);
            musicSlider.Changed = v => { if (MusicVolumeChanged != null) MusicVolumeChanged(v); };
            soundSlider.Changed = v => { if (SoundVolumeChanged != null) SoundVolumeChanged(v); };
        }

        void BuildVictoryPanel()
        {
            victoryPanel = PanelRoot("VictoryPanel");
            var t = victoryPanel.transform;
            UIKit.Card("Card", t, new Vector3(0f, -0.6f, 0f), new Vector2(7.2f, 9.0f), NeonTheme.Shadow, 51);
            for (int i = 0; i < 3; i++)
            {
                float x = (i - 1) * 1.7f;
                float y = i == 1 ? 4.6f : 4.2f;
                UIKit.Sprite("StarGlow", t, NeonSprites.Glow, NeonTheme.WithAlpha(NeonTheme.Energy, 0.5f), new Vector3(x, y, 0f), Vector2.one * 2.4f, 55);
                stars[i] = UIKit.Sprite("Star" + (i + 1), t, NeonSprites.Star, NeonTheme.Energy, new Vector3(x, y, 0f), Vector2.one * (i == 1 ? 1.9f : 1.5f), 56);
            }
            UIKit.Sprite("Ribbon", t, NeonSprites.RoundedFrame, NeonTheme.Hex(0xFF9F1C), new Vector3(0f, 2.7f, 0f), new Vector2(6.6f, 1.25f), 57);
            victoryTitle = UIKit.Text("Title", t, new Vector3(0f, 2.7f, 0f), 0.62f, NeonTheme.Text, TextAnchor.MiddleCenter, 58);
            victoryDetail = UIKit.Text("Detail", t, new Vector3(0f, 1.25f, 0f), 0.44f, NeonTheme.Text, TextAnchor.MiddleCenter, 58);
            UIKit.Card("CoinBox", t, new Vector3(0f, -0.35f, 0f), new Vector2(3.6f, 1.2f), NeonTheme.Blue, 55, 0.9f);
            victoryCoins = UIKit.CoinCounter(t, new Vector3(-0.9f, -0.35f, 0f), 0.5f, 59);

            nextButton = Add(victoryButtons, t, "Next Level", new Vector3(0f, -1.9f, 0f), new Vector2(5.0f, 1.15f), NeonTheme.Success, () => Invoke(NextPressed), ButtonStyle.Filled, 60);
            Add(victoryButtons, t, "Replay", new Vector3(-1.3f, -3.35f, 0f), new Vector2(2.4f, 1.0f), NeonTheme.Shadow, () => Invoke(RestartPressed), ButtonStyle.Outline, 60);
            Add(victoryButtons, t, "Home", new Vector3(1.3f, -3.35f, 0f), new Vector2(2.4f, 1.0f), NeonTheme.Blue, () => Invoke(MenuPressed), ButtonStyle.Filled, 60);
        }

        void BuildFailurePanel()
        {
            failurePanel = PanelRoot("FailurePanel");
            var t = failurePanel.transform;
            UIKit.Card("Card", t, new Vector3(0f, -0.6f, 0f), new Vector2(7.2f, 9.0f), NeonTheme.Blocked, 51);
            UIKit.Sprite("BrokenStar", t, NeonSprites.Star, NeonTheme.Blocked, new Vector3(0f, 4.3f, 0f), Vector2.one * 2.0f, 56);
            UIKit.Sprite("Ribbon", t, NeonSprites.RoundedFrame, NeonTheme.Hex(0xD7263D), new Vector3(0f, 2.7f, 0f), new Vector2(6.6f, 1.25f), 57);
            UIKit.Text("Title", t, new Vector3(0f, 2.7f, 0f), 0.62f, NeonTheme.Text, TextAnchor.MiddleCenter, 58).text = "Level Failed!";
            failureDetail = UIKit.Text("Detail", t, new Vector3(0f, 1.25f, 0f), 0.44f, NeonTheme.Text, TextAnchor.MiddleCenter, 58);

            Add(failureButtons, t, "↻  Retry", new Vector3(0f, -0.35f, 0f), new Vector2(5.0f, 1.15f), NeonTheme.Energy, () => Invoke(RestartPressed), ButtonStyle.Filled, 60);
            failureUndoButton = Add(failureButtons, t, "↺  Undo", new Vector3(0f, -1.75f, 0f), new Vector2(5.0f, 1.0f), NeonTheme.Light, () => Invoke(UndoPressed), ButtonStyle.Outline, 60);
            Add(failureButtons, t, "Level Select", new Vector3(0f, -3.0f, 0f), new Vector2(5.0f, 1.0f), NeonTheme.Light, () => Invoke(LevelsPressed), ButtonStyle.Outline, 60);
            Add(failureButtons, t, "Home", new Vector3(0f, -4.25f, 0f), new Vector2(5.0f, 1.0f), NeonTheme.Light, () => Invoke(MenuPressed), ButtonStyle.Outline, 60);
        }

        void BuildHintPanel()
        {
            hintPanel = PanelRoot("HintPanel");
            var t = hintPanel.transform;
            UIKit.Card("Card", t, new Vector3(0f, 0f, 0f), new Vector2(6.8f, 5.6f), NeonTheme.Light, 51);
            UIKit.Text("Title", t, new Vector3(0f, 1.9f, 0f), 0.62f, NeonTheme.Text, TextAnchor.MiddleCenter, 58).text = "Hint";
            hintText = UIKit.Text("Text", t, new Vector3(0f, 0.7f, 0f), 0.46f, NeonTheme.Light, TextAnchor.MiddleCenter, 58);
            Add(hintButtons, t, "OK", new Vector3(0f, -0.8f, 0f), new Vector2(4.0f, 1.15f), NeonTheme.Blue, () => Invoke(HintConfirmed), ButtonStyle.Filled, 60);
            hintCost = UIKit.Text("Cost", t, new Vector3(0f, -1.95f, 0f), 0.32f, NeonTheme.Energy, TextAnchor.MiddleCenter, 58);
        }

        // ---- State updates ----

        public void ShowLevel(LevelData level, bool daily)
        {
            levelLabel.text = daily ? "Daily Challenge" : "Level " + level.worldId + "-" + level.levelId;
            hintLabel.text = string.IsNullOrEmpty(level.hint) ? "←  Swipe to Move  →" : level.hint;
            HidePanels();
        }

        public void SetMoves(PuzzleState puzzle)
        {
            int moves = puzzle.MoveCount, max = puzzle.MaxMoves;
            movesLabel.text = max > 0 ? "Moves: " + moves + " / " + max : "Moves: " + moves;
            movesLabel.color = max > 0 && max - moves <= 2 ? NeonTheme.Energy : NeonTheme.Text;
            starPreview.text = UIKit.Stars(moves == 0 ? 3 : StarManager.Calculate(puzzle.Level, moves));
            undoButton.SetEnabled(puzzle.CanUndo);
            failureUndoButton.SetEnabled(puzzle.CanUndo);
            int free = SaveManager.Hints;
            hintButtonCount.text = free > 0 ? free.ToString() : "$";
        }

        public bool PanelVisible { get { return pausePanel.activeSelf || victoryPanel.activeSelf || failurePanel.activeSelf || hintPanel.activeSelf; } }
        public bool PauseVisible { get { return pausePanel.activeSelf; } }

        public void HidePanels()
        {
            pausePanel.SetActive(false);
            victoryPanel.SetActive(false);
            failurePanel.SetActive(false);
            hintPanel.SetActive(false);
        }

        public void ShowPause()
        {
            HidePanels();
            musicSlider.Value = SettingsData.MusicVolume;
            soundSlider.Value = SettingsData.SoundVolume;
            pausePanel.SetActive(true);
        }

        public void ShowVictory(int moves, int starCount, int bestMoves, int coins, bool last, bool daily)
        {
            HidePanels();
            victoryTitle.text = daily ? "Daily Complete!" : last ? "World Clear!" : "Level Complete!";
            for (int i = 0; i < 3; i++)
                stars[i].color = i < starCount ? NeonTheme.Energy : NeonTheme.WithAlpha(NeonTheme.Text, 0.15f);
            victoryDetail.text = "Moves:  " + moves + "\nBest:  " + bestMoves;
            victoryCoins.text = "+" + coins;
            nextButton.Label.text = daily || last ? "Continue" : "Next Level";
            victoryPanel.SetActive(true);
        }

        public void ShowFailure(int moves, int bestMoves)
        {
            HidePanels();
            failureDetail.text = "Moves:  " + moves + "\nBest:  " + bestMoves;
            failurePanel.SetActive(true);
        }

        public void ShowHint(Direction swipe, string costText)
        {
            HidePanels();
            hintText.text = "Try swiping " + swipe.ToString().ToUpperInvariant() + "!";
            hintCost.text = costText;
            hintPanel.SetActive(true);
        }

        public void ShowMessage(string message)
        {
            hintLabel.text = message;
        }

        /// <summary>Routes a tap to the buttons that are currently visible. Returns true if one was hit.</summary>
        public bool HandleTap(Vector3 worldPoint)
        {
            if (pausePanel.activeSelf)
            {
                if (musicSlider.HandlePointer(worldPoint) || soundSlider.HandlePointer(worldPoint)) return true;
                return WorldButton.TapAny(pauseButtons, worldPoint);
            }
            if (victoryPanel.activeSelf) return WorldButton.TapAny(victoryButtons, worldPoint);
            if (failurePanel.activeSelf) return WorldButton.TapAny(failureButtons, worldPoint);
            if (hintPanel.activeSelf) return WorldButton.TapAny(hintButtons, worldPoint);
            return WorldButton.TapAny(hudButtons, worldPoint);
        }

        public void HandleDrag(Vector3 worldPoint)
        {
            if (!pausePanel.activeSelf) return;
            if (!musicSlider.HandlePointer(worldPoint)) soundSlider.HandlePointer(worldPoint);
        }
    }
}
