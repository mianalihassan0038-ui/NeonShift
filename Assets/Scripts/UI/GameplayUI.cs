using System;
using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// Top bar (level + move counter), board labels, hint line, Undo / Restart buttons,
    /// and the victory and failure panels.
    /// Uses TextMesh + sprites in world space so the prototype has no uGUI dependency.
    /// Layout is designed for a 9 x 16 world-unit portrait area (see GameManager.FitCamera).
    /// </summary>
    public sealed class GameplayUI : MonoBehaviour
    {
        static Font font;

        TextMesh levelLabel, movesLabel, hintLabel;
        WorldButton undoButton, restartButton;

        GameObject victoryPanel, failurePanel, pausePanel;
        TextMesh victoryTitle, victoryDetail, nextLabel;
        SpriteRenderer[] stars = new SpriteRenderer[3];
        WorldButton failureUndoButton;

        readonly List<WorldButton> hudButtons = new List<WorldButton>();
        readonly List<WorldButton> victoryButtons = new List<WorldButton>();
        readonly List<WorldButton> failureButtons = new List<WorldButton>();
        readonly List<WorldButton> pauseButtons = new List<WorldButton>();

        public Action NextPressed;
        public Action ReplayPressed;
        public Action UndoPressed;
        public Action RestartPressed;
        public Action PausePressed;
        public Action ResumePressed;
        public Action LevelsPressed;
        public Action MenuPressed;

        public static Font DefaultFont
        {
            get
            {
                if (font != null) return font;
#if UNITY_2022_2_OR_NEWER
                font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
#else
                font = Resources.GetBuiltinResource<Font>("Arial.ttf");
#endif
                return font;
            }
        }

        /// <summary>Creates a crisp world-space label. height = approximate line height in world units.</summary>
        public static TextMesh MakeText(string name, Transform parent, Vector3 localPos, float height, Color color, TextAnchor anchor, int order)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            var tm = go.AddComponent<TextMesh>();
            tm.font = DefaultFont;
            tm.fontSize = 96;
            tm.characterSize = height * 10f / tm.fontSize;
            tm.anchor = anchor;
            tm.alignment = TextAlignment.Center;
            tm.color = color;
            var mr = go.GetComponent<MeshRenderer>();
            mr.sharedMaterial = DefaultFont.material;
            mr.sortingOrder = order;
            return tm;
        }

        public static SpriteRenderer MakeSprite(string name, Transform parent, Sprite sprite, Color color, Vector3 pos, Vector2 scale, int order)
        {
            var sr = new GameObject(name).AddComponent<SpriteRenderer>();
            sr.transform.SetParent(parent, false);
            sr.transform.localPosition = pos;
            sr.transform.localScale = new Vector3(scale.x, scale.y, 1f);
            sr.sprite = sprite;
            sr.color = color;
            sr.sortingOrder = order;
            return sr;
        }

        public void Build(float lightLabelY, float shadowLabelY)
        {
            var pause = WorldButton.Create(transform, "II", new Vector3(-3.85f, 7.35f, 0f), new Vector2(1.0f, 0.9f), NeonTheme.Text, 20);
            pause.Clicked = () => Invoke(PausePressed);
            hudButtons.Add(pause);
            levelLabel = MakeText("LevelLabel", transform, new Vector3(-3.1f, 7.35f, 0f), 0.5f, NeonTheme.Text, TextAnchor.MiddleLeft, 20);
            movesLabel = MakeText("MoveCounter", transform, new Vector3(4.1f, 7.35f, 0f), 0.5f, NeonTheme.Text, TextAnchor.MiddleRight, 20);

            MakeText("LightWorldLabel", transform, new Vector3(0f, lightLabelY, 0f), 0.42f, NeonTheme.Light, TextAnchor.MiddleCenter, 20).text = "L I G H T";
            MakeText("ShadowWorldLabel", transform, new Vector3(0f, shadowLabelY, 0f), 0.42f, NeonTheme.Shadow, TextAnchor.MiddleCenter, 20).text = "S H A D O W";

            hintLabel = MakeText("Hint", transform, new Vector3(0f, -6.95f, 0f), 0.28f, NeonTheme.WithAlpha(NeonTheme.Text, 0.75f), TextAnchor.MiddleCenter, 20);

            // Bottom controls.
            undoButton = WorldButton.Create(transform, "UNDO", new Vector3(-1.6f, -7.75f, 0f), new Vector2(2.8f, 0.8f), NeonTheme.Light, 20);
            restartButton = WorldButton.Create(transform, "RESTART", new Vector3(1.6f, -7.75f, 0f), new Vector2(2.8f, 0.8f), NeonTheme.Shadow, 20);
            undoButton.Clicked = () => Invoke(UndoPressed);
            restartButton.Clicked = () => Invoke(RestartPressed);
            hudButtons.Add(undoButton);
            hudButtons.Add(restartButton);

            BuildVictoryPanel();
            BuildFailurePanel();
            BuildPausePanel();
        }

        void BuildPausePanel()
        {
            pausePanel = BuildPanelBase("PausePanel", NeonTheme.Text);
            pausePanel.transform.Find("Card").localScale = new Vector3(6.4f, 8.4f, 1f);
            pausePanel.transform.Find("CardFill").localScale = new Vector3(6.25f, 8.25f, 1f);
            MakeText("Title", pausePanel.transform, new Vector3(0f, 3.0f, 0f), 0.65f, NeonTheme.Text, TextAnchor.MiddleCenter, 53).text = "PAUSED";
            string[] labels = { "RESUME", "RESTART", "LEVELS", "MENU" };
            Color[] colors = { NeonTheme.Light, NeonTheme.Shadow, NeonTheme.Light, NeonTheme.Shadow };
            Action[] actions = { () => Invoke(ResumePressed), () => Invoke(RestartPressed), () => Invoke(LevelsPressed), () => Invoke(MenuPressed) };
            for (int i = 0; i < labels.Length; i++)
            {
                var b = WorldButton.Create(pausePanel.transform, labels[i], new Vector3(0f, 1.5f - i * 1.45f, 0f), new Vector2(4.4f, 1.1f), colors[i], 54);
                b.Clicked = actions[i];
                pauseButtons.Add(b);
            }
        }

        public void ShowPause() { HidePanels(); pausePanel.SetActive(true); }
        public bool PauseVisible { get { return pausePanel.activeSelf; } }

        static void Invoke(Action a) { if (a != null) a(); }

        GameObject BuildPanelBase(string name, Color accent)
        {
            var panel = new GameObject(name);
            panel.transform.SetParent(transform, false);
            MakeSprite("Dim", panel.transform, NeonSprites.Square, NeonTheme.WithAlpha(NeonTheme.Background, 0.82f), Vector3.zero, new Vector2(40f, 40f), 50);
            MakeSprite("Card", panel.transform, NeonSprites.RoundedFrame, NeonTheme.WithAlpha(accent, 0.6f), Vector3.zero, new Vector2(7.6f, 6.2f), 51);
            MakeSprite("CardFill", panel.transform, NeonSprites.RoundedFrame, NeonTheme.Panel, Vector3.zero, new Vector2(7.45f, 6.05f), 52);
            panel.SetActive(false);
            return panel;
        }

        void BuildVictoryPanel()
        {
            victoryPanel = BuildPanelBase("VictoryPanel", NeonTheme.Light);
            victoryTitle = MakeText("Title", victoryPanel.transform, new Vector3(0f, 2.15f, 0f), 0.65f, NeonTheme.Light, TextAnchor.MiddleCenter, 53);
            for (int i = 0; i < 3; i++)
            {
                float x = (i - 1) * 1.35f;
                float y = i == 1 ? 0.95f : 0.8f;
                stars[i] = MakeSprite("Star" + (i + 1), victoryPanel.transform, NeonSprites.Star, NeonTheme.Energy, new Vector3(x, y, 0f), Vector2.one * (i == 1 ? 1.35f : 1.1f), 53);
            }
            victoryDetail = MakeText("Detail", victoryPanel.transform, new Vector3(0f, -0.35f, 0f), 0.38f, NeonTheme.Text, TextAnchor.MiddleCenter, 53);

            var next = WorldButton.Create(victoryPanel.transform, "NEXT", new Vector3(1.75f, -1.7f, 0f), new Vector2(3f, 1.05f), NeonTheme.Light, 54);
            var replay = WorldButton.Create(victoryPanel.transform, "REPLAY", new Vector3(-1.75f, -1.7f, 0f), new Vector2(3f, 1.05f), NeonTheme.Shadow, 54);
            next.Clicked = () => Invoke(NextPressed);
            nextLabel = next.Label;
            replay.Clicked = () => Invoke(ReplayPressed);
            victoryButtons.Add(next);
            victoryButtons.Add(replay);
        }

        void BuildFailurePanel()
        {
            failurePanel = BuildPanelBase("FailurePanel", NeonTheme.Blocked);
            MakeText("Title", failurePanel.transform, new Vector3(0f, 1.6f, 0f), 0.65f, NeonTheme.Blocked, TextAnchor.MiddleCenter, 53).text = "OUT OF MOVES";
            MakeText("Detail", failurePanel.transform, new Vector3(0f, 0.4f, 0f), 0.36f, NeonTheme.Text, TextAnchor.MiddleCenter, 53).text = "Undo a step or try again.";

            failureUndoButton = WorldButton.Create(failurePanel.transform, "UNDO", new Vector3(-1.75f, -1.3f, 0f), new Vector2(3f, 1.05f), NeonTheme.Light, 54);
            var retry = WorldButton.Create(failurePanel.transform, "RETRY", new Vector3(1.75f, -1.3f, 0f), new Vector2(3f, 1.05f), NeonTheme.Shadow, 54);
            failureUndoButton.Clicked = () => Invoke(UndoPressed);
            retry.Clicked = () => Invoke(RestartPressed);
            failureButtons.Add(failureUndoButton);
            failureButtons.Add(retry);
        }

        public void ShowLevel(LevelData level)
        {
            levelLabel.text = "LEVEL " + level.worldId + "-" + level.levelId;
            hintLabel.text = string.IsNullOrEmpty(level.hint) ? "Swipe to move both" : level.hint;
            HidePanels();
        }

        public void SetMoves(int moves, int maxMoves, bool canUndo)
        {
            movesLabel.text = maxMoves > 0 ? "MOVES " + moves + "/" + maxMoves : "MOVES " + moves;
            movesLabel.color = maxMoves > 0 && maxMoves - moves <= 2 ? NeonTheme.Energy : NeonTheme.Text;
            undoButton.SetEnabled(canUndo);
            failureUndoButton.SetEnabled(canUndo);
        }

        public bool PanelVisible { get { return victoryPanel.activeSelf || failurePanel.activeSelf || pausePanel.activeSelf; } }

        public void HidePanels()
        {
            victoryPanel.SetActive(false);
            failurePanel.SetActive(false);
            pausePanel.SetActive(false);
        }

        public void ShowVictory(int moves, int starCount, int bestMoves, bool worldComplete)
        {
            victoryTitle.text = worldComplete ? "WORLD CLEAR" : "LEVEL COMPLETE";
            nextLabel.text = worldComplete ? "LEVELS" : "NEXT";
            for (int i = 0; i < 3; i++)
                stars[i].color = i < starCount ? NeonTheme.Energy : NeonTheme.WithAlpha(NeonTheme.Text, 0.15f);
            string detail = "Moves: " + moves;
            if (bestMoves > 0) detail += moves <= bestMoves ? "   Perfect!" : "   Best: " + bestMoves;
            victoryDetail.text = detail;
            failurePanel.SetActive(false);
            victoryPanel.SetActive(true);
        }

        public void ShowFailure()
        {
            victoryPanel.SetActive(false);
            failurePanel.SetActive(true);
        }

        /// <summary>Routes a tap to the buttons that are currently visible. Returns true if one was hit.</summary>
        public bool HandleTap(Vector3 worldPoint)
        {
            List<WorldButton> active = pausePanel.activeSelf ? pauseButtons
                : victoryPanel.activeSelf ? victoryButtons
                : failurePanel.activeSelf ? failureButtons
                : hudButtons;
            return WorldButton.TapAny(active, worldPoint);
        }
    }
}
