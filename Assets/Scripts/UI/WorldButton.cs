using System;
using UnityEngine;

namespace NeonShift
{
    /// <summary>A simple tappable button made of a sprite and a label (no uGUI needed).</summary>
    public sealed class WorldButton : MonoBehaviour
    {
        public Action Clicked;
        Vector2 size;
        bool interactable = true;
        SpriteRenderer border;
        TextMesh label;
        Color accent;

        public static WorldButton Create(Transform parent, string label, Vector3 localPos, Vector2 size, Color accent, int order)
        {
            var go = new GameObject("Button " + label);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            var button = go.AddComponent<WorldButton>();
            button.size = size;

            button.accent = accent;
            var border = new GameObject("Border").AddComponent<SpriteRenderer>();
            button.border = border;
            border.transform.SetParent(go.transform, false);
            border.transform.localScale = new Vector3(size.x, size.y, 1f);
            border.sprite = NeonSprites.RoundedFrame;
            border.color = accent;
            border.sortingOrder = order;

            var fill = new GameObject("Fill").AddComponent<SpriteRenderer>();
            fill.transform.SetParent(go.transform, false);
            fill.transform.localScale = new Vector3(size.x - 0.1f, size.y - 0.1f, 1f);
            fill.sprite = NeonSprites.RoundedFrame;
            fill.color = NeonTheme.Panel;
            fill.sortingOrder = order + 1;

            button.label = GameplayUI.MakeText("Label", go.transform, Vector3.zero, size.y * 0.42f, accent, TextAnchor.MiddleCenter, order + 2);
            button.label.text = label;
            return button;
        }

        /// <summary>Disabled buttons are dimmed and ignore taps.</summary>
        public void SetEnabled(bool enabled)
        {
            interactable = enabled;
            Color c = enabled ? accent : NeonTheme.WithAlpha(accent, 0.25f);
            border.color = c;
            label.color = c;
        }

        public bool Contains(Vector3 worldPoint)
        {
            if (!interactable || !gameObject.activeInHierarchy) return false;
            Vector3 local = worldPoint - transform.position;
            return Mathf.Abs(local.x) <= size.x * 0.5f && Mathf.Abs(local.y) <= size.y * 0.5f;
        }
    }
}
