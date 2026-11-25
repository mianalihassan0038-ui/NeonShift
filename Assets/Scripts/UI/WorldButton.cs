using System;
using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    public enum ButtonStyle
    {
        Outline,   // dark fill, glowing border and text
        Filled,    // bright fill, dark text (primary actions)
        Round      // circular icon button with a caption underneath
    }

    /// <summary>A tappable neon button made of sprites and a label (no uGUI needed).</summary>
    public sealed class WorldButton : MonoBehaviour
    {
        public Action Clicked;

        Vector2 size;
        bool interactable = true;
        Color accent;
        ButtonStyle style;
        SpriteRenderer border, fill, halo;
        TextMesh label;
        float pressedUntil;

        public TextMesh Label { get { return label; } }
        public bool Interactable { get { return interactable; } }

        public static WorldButton Create(Transform parent, string text, Vector3 localPos, Vector2 size, Color accent, int order,
                                         ButtonStyle style = ButtonStyle.Outline, string caption = null)
        {
            var go = new GameObject("Button " + text);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            var b = go.AddComponent<WorldButton>();
            b.size = size;
            b.accent = accent;
            b.style = style;

            Sprite shape = style == ButtonStyle.Round ? NeonSprites.Circle : NeonSprites.RoundedFrame;
            b.halo = UIKit.Sprite("Halo", go.transform, NeonSprites.Glow, NeonTheme.WithAlpha(accent, 0.28f), Vector3.zero, size * 1.5f, order);
            b.border = UIKit.Sprite("Border", go.transform, shape, accent, Vector3.zero, size, order + 1);
            b.fill = UIKit.Sprite("Fill", go.transform, shape, style == ButtonStyle.Filled ? accent : NeonTheme.Panel, Vector3.zero, size - new Vector2(0.1f, 0.1f), order + 2);
            if (style == ButtonStyle.Filled)
                UIKit.Sprite("Shine", go.transform, NeonSprites.RoundedFrame, NeonTheme.WithAlpha(Color.white, 0.18f), new Vector3(0f, size.y * 0.2f, 0f), new Vector2(size.x - 0.25f, size.y * 0.35f), order + 3);

            float textHeight = style == ButtonStyle.Round ? size.y * 0.5f : Mathf.Min(size.y * 0.42f, 0.6f);
            Color textColor = style == ButtonStyle.Filled ? NeonTheme.Background : accent;
            b.label = UIKit.Text("Label", go.transform, Vector3.zero, textHeight, textColor, TextAnchor.MiddleCenter, order + 4);
            b.label.text = text;

            if (caption != null)
                UIKit.Text("Caption", go.transform, new Vector3(0f, -size.y * 0.5f - 0.3f, 0f), 0.3f, NeonTheme.Text, TextAnchor.MiddleCenter, order + 4).text = caption;
            return b;
        }

        /// <summary>Disabled buttons are dimmed and ignore taps.</summary>
        public void SetEnabled(bool enabled)
        {
            interactable = enabled;
            float a = enabled ? 1f : 0.25f;
            border.color = NeonTheme.WithAlpha(accent, a);
            halo.color = NeonTheme.WithAlpha(accent, enabled ? 0.28f : 0f);
            if (style == ButtonStyle.Filled) fill.color = NeonTheme.WithAlpha(accent, a);
            Color textColor = style == ButtonStyle.Filled ? NeonTheme.Background : accent;
            label.color = NeonTheme.WithAlpha(textColor, style == ButtonStyle.Filled ? 1f : a);
        }

        public void SetAccent(Color newAccent)
        {
            accent = newAccent;
            SetEnabled(interactable);
        }

        void Update()
        {
            float target = Time.unscaledTime < pressedUntil ? 0.92f : 1f;
            float s = Mathf.MoveTowards(transform.localScale.x, target, Time.unscaledDeltaTime * 3f);
            transform.localScale = new Vector3(s, s, 1f);
        }

        public bool Contains(Vector3 worldPoint)
        {
            if (!interactable || !gameObject.activeInHierarchy) return false;
            Vector3 local = worldPoint - transform.position;
            return Mathf.Abs(local.x) <= size.x * 0.5f + 0.05f && Mathf.Abs(local.y) <= size.y * 0.5f + 0.05f;
        }

        /// <summary>Clicks the first enabled, visible button under the point. Returns true if one was hit.</summary>
        public static bool TapAny(IList<WorldButton> buttons, Vector3 worldPoint)
        {
            foreach (var b in buttons)
            {
                if (b != null && b.Contains(worldPoint))
                {
                    b.pressedUntil = Time.unscaledTime + 0.08f;
                    if (AudioManager.Instance != null) AudioManager.Instance.Play(Sfx.Click);
                    if (b.Clicked != null) b.Clicked();
                    return true;
                }
            }
            return false;
        }
    }
}
