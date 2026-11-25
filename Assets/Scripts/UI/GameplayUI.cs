using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// Top bar (level + move counter), board labels, hint line and the victory panel.
    /// Uses TextMesh + sprites in world space so the prototype has no uGUI dependency.
    /// Layout is designed for a 9 x 16 world-unit portrait area (see GameManager.FitCamera).
    /// </summary>
    public sealed class GameplayUI : MonoBehaviour
    {
        static Font font;

        TextMesh levelLabel, movesLabel, hintLabel;
        GameObject victoryPanel;
        TextMesh victoryTitle, victoryDetail;
        WorldButton nextButton, replayButton;
        readonly List<WorldButton> buttons = new List<WorldButton>();

        public System.Action NextPressed;
        public System.Action ReplayPressed;

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

        public void Build(float lightLabelY, float shadowLabelY)
        {
            levelLabel = MakeText("LevelLabel", transform, new Vector3(-4.1f, 7.25f, 0f), 0.5f, NeonTheme.Text, TextAnchor.MiddleLeft, 20);
            movesLabel = MakeText("MoveCounter", transform, new Vector3(4.1f, 7.25f, 0f), 0.5f, NeonTheme.Text, TextAnchor.MiddleRight, 20);

            MakeText("LightWorldLabel", transform, new Vector3(0f, lightLabelY, 0f), 0.42f, NeonTheme.Light, TextAnchor.MiddleCenter, 20).text = "L I G H T";
            MakeText("ShadowWorldLabel", transform, new Vector3(0f, shadowLabelY, 0f), 0.42f, NeonTheme.Shadow, TextAnchor.MiddleCenter, 20).text = "S H A D O W";

            hintLabel = MakeText("Hint", transform, new Vector3(0f, -7.15f, 0f), 0.30f, NeonTheme.WithAlpha(NeonTheme.Text, 0.75f), TextAnchor.MiddleCenter, 20);

            BuildVictoryPanel();
        }

        void BuildVictoryPanel()
        {
            victoryPanel = new GameObject("VictoryPanel");
            victoryPanel.transform.SetParent(transform, false);

            var dim = new GameObject("Dim").AddComponent<SpriteRenderer>();
            dim.transform.SetParent(victoryPanel.transform, false);
            dim.transform.localScale = new Vector3(40f, 40f, 1f);
            dim.sprite = NeonSprites.Square;
            dim.color = NeonTheme.WithAlpha(NeonTheme.Background, 0.82f);
            dim.sortingOrder = 50;

            var card = new GameObject("Card").AddComponent<SpriteRenderer>();
            card.transform.SetParent(victoryPanel.transform, false);
            card.transform.localScale = new Vector3(7.6f, 5.6f, 1f);
            card.sprite = NeonSprites.RoundedFrame;
            card.color = NeonTheme.WithAlpha(NeonTheme.Light, 0.6f);
            card.sortingOrder = 51;
            var cardFill = new GameObject("CardFill").AddComponent<SpriteRenderer>();
            cardFill.transform.SetParent(victoryPanel.transform, false);
            cardFill.transform.localScale = new Vector3(7.45f, 5.45f, 1f);
            cardFill.sprite = NeonSprites.RoundedFrame;
            cardFill.color = NeonTheme.Panel;
            cardFill.sortingOrder = 52;

            victoryTitle = MakeText("Title", victoryPanel.transform, new Vector3(0f, 1.7f, 0f), 0.7f, NeonTheme.Light, TextAnchor.MiddleCenter, 53);
            victoryDetail = MakeText("Detail", victoryPanel.transform, new Vector3(0f, 0.5f, 0f), 0.4f, NeonTheme.Text, TextAnchor.MiddleCenter, 53);

            nextButton = WorldButton.Create(victoryPanel.transform, "NEXT", new Vector3(1.75f, -1.4f, 0f), new Vector2(3f, 1.1f), NeonTheme.Light, 54);
            replayButton = WorldButton.Create(victoryPanel.transform, "REPLAY", new Vector3(-1.75f, -1.4f, 0f), new Vector2(3f, 1.1f), NeonTheme.Shadow, 54);
            nextButton.Clicked = () => { if (NextPressed != null) NextPressed(); };
            replayButton.Clicked = () => { if (ReplayPressed != null) ReplayPressed(); };
            buttons.Add(nextButton);
            buttons.Add(replayButton);

            victoryPanel.SetActive(false);
        }

        public void ShowLevel(LevelData level, int moves)
        {
            levelLabel.text = "LEVEL " + level.worldId + "-" + level.levelId;
            SetMoves(moves);
            hintLabel.text = string.IsNullOrEmpty(level.hint) ? "Swipe to move both" : level.hint;
            victoryPanel.SetActive(false);
        }

        public void SetMoves(int moves)
        {
            movesLabel.text = "MOVES " + moves;
        }

        public bool VictoryVisible { get { return victoryPanel.activeSelf; } }

        public void ShowVictory(int moves, int best, bool worldComplete)
        {
            victoryTitle.text = worldComplete ? "WORLD CLEAR" : "LEVEL COMPLETE";
            string detail = "Moves: " + moves;
            if (best > 0) detail += (moves <= best ? "   Perfect!" : "   Best: " + best);
            victoryDetail.text = detail;
            nextButton.gameObject.SetActive(true);
            victoryPanel.SetActive(true);
        }

        /// <summary>Returns true if the tap hit a button.</summary>
        public bool HandleTap(Vector3 worldPoint)
        {
            foreach (var b in buttons)
            {
                if (b.Contains(worldPoint))
                {
                    if (b.Clicked != null) b.Clicked();
                    return true;
                }
            }
            return false;
        }
    }
}
