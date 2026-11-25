using UnityEngine;

namespace NeonShift
{
    /// <summary>Shared helpers for the world-space neon UI (text, sprites, glowing panels).</summary>
    public static class UIKit
    {
        static Font mainFont, logoFont;

        public static Font MainFont
        {
            get
            {
                if (mainFont == null) mainFont = Resources.Load<Font>("Fonts/InterDisplay-ExtraBold");
                if (mainFont == null) mainFont = BuiltinFont();
                return mainFont;
            }
        }

        public static Font LogoFont
        {
            get
            {
                if (logoFont == null) logoFont = Resources.Load<Font>("Fonts/InterDisplay-BlackItalic");
                if (logoFont == null) logoFont = MainFont;
                return logoFont;
            }
        }

        static Font BuiltinFont()
        {
#if UNITY_2022_2_OR_NEWER
            return Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
#else
            return Resources.GetBuiltinResource<Font>("Arial.ttf");
#endif
        }

        /// <summary>Crisp world-space label. height = approximate line height in world units.</summary>
        public static TextMesh Text(string name, Transform parent, Vector3 localPos, float height, Color color,
                                    TextAnchor anchor = TextAnchor.MiddleCenter, int order = 20, Font font = null)
        {
            font = font ?? MainFont;
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            var tm = go.AddComponent<TextMesh>();
            tm.font = font;
            tm.fontSize = 96;
            tm.characterSize = height * 10f / tm.fontSize;
            tm.anchor = anchor;
            tm.alignment = anchor == TextAnchor.MiddleLeft || anchor == TextAnchor.UpperLeft ? TextAlignment.Left
                         : anchor == TextAnchor.MiddleRight || anchor == TextAnchor.UpperRight ? TextAlignment.Right
                         : TextAlignment.Center;
            tm.color = color;
            var mr = go.GetComponent<MeshRenderer>();
            mr.sharedMaterial = font.material;
            mr.sortingOrder = order;
            return tm;
        }

        public static SpriteRenderer Sprite(string name, Transform parent, Sprite sprite, Color color, Vector3 pos, Vector2 scale, int order)
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

        /// <summary>Glowing card: soft halo, bright border, dark fill. Returns the card root.</summary>
        public static Transform Card(string name, Transform parent, Vector3 pos, Vector2 size, Color accent, int order, float fillAlpha = 1f)
        {
            var root = new GameObject(name).transform;
            root.SetParent(parent, false);
            root.localPosition = pos;
            Sprite("Halo", root, NeonSprites.Glow, NeonTheme.WithAlpha(accent, 0.22f), Vector3.zero, size * 1.35f, order);
            Sprite("Border", root, NeonSprites.RoundedFrame, accent, Vector3.zero, size, order + 1);
            Sprite("Fill", root, NeonSprites.RoundedFrame, NeonTheme.WithAlpha(NeonTheme.Panel, fillAlpha), Vector3.zero, size - new Vector2(0.12f, 0.12f), order + 2);
            Sprite("Sheen", root, NeonSprites.RoundedFrame, NeonTheme.WithAlpha(accent, 0.07f), new Vector3(0f, size.y * 0.25f, 0f), new Vector2(size.x - 0.2f, size.y * 0.45f), order + 3);
            return root;
        }

        /// <summary>Full-screen dim layer for modal panels.</summary>
        public static SpriteRenderer Dim(Transform parent, int order, float alpha = 0.85f)
        {
            return Sprite("Dim", parent, NeonSprites.Square, NeonTheme.WithAlpha(NeonTheme.Background, alpha), Vector3.zero, new Vector2(60f, 60f), order);
        }

        /// <summary>Coin icon + amount, anchored at its left edge.</summary>
        public static TextMesh CoinCounter(Transform parent, Vector3 pos, float height, int order)
        {
            Sprite("Coin", parent, NeonSprites.Circle, NeonTheme.Energy, pos, Vector2.one * height * 1.25f, order);
            Sprite("CoinInner", parent, NeonSprites.Ring, NeonTheme.WithAlpha(NeonTheme.Background, 0.35f), pos, Vector2.one * height * 0.85f, order + 1);
            return Text("Coins", parent, pos + new Vector3(height * 0.85f, 0f, 0f), height, NeonTheme.Text, TextAnchor.MiddleLeft, order);
        }

        public static string Stars(int filled, int total = 3)
        {
            return new string('★', Mathf.Clamp(filled, 0, total)) + new string('☆', Mathf.Max(0, total - filled));
        }
    }
}
