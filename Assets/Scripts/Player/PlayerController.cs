using System.Collections;
using UnityEngine;

namespace NeonShift
{
    /// <summary>Visual token for Light or Shadow. Grid position lives in PuzzleState; this only animates.</summary>
    public sealed class PlayerController : MonoBehaviour
    {
        SpriteRenderer core;
        SpriteRenderer glow;
        Color accent;

        public static PlayerController Create(string name, Transform board, Color accentColor)
        {
            var go = new GameObject(name);
            go.transform.SetParent(board, false);
            var pc = go.AddComponent<PlayerController>();
            pc.accent = accentColor;

            pc.glow = AddSprite(go.transform, "Glow", NeonSprites.Glow, NeonTheme.WithAlpha(accentColor, 0.8f), 10, BoardRenderer.CellSize * 1.5f);
            pc.core = AddSprite(go.transform, "Core", NeonSprites.Circle, accentColor, 11, BoardRenderer.CellSize * 0.8f);
            // Two eyes make the orbs read as characters, like the mockups.
            var eyeL = AddSprite(go.transform, "EyeL", NeonSprites.Circle, NeonTheme.Background, 12, BoardRenderer.CellSize * 0.12f);
            eyeL.transform.localPosition = new Vector3(-0.11f, 0.05f, 0f);
            eyeL.transform.localScale = new Vector3(0.11f, 0.16f, 1f);
            var eyeR = AddSprite(go.transform, "EyeR", NeonSprites.Circle, NeonTheme.Background, 12, BoardRenderer.CellSize * 0.12f);
            eyeR.transform.localPosition = new Vector3(0.11f, 0.05f, 0f);
            eyeR.transform.localScale = new Vector3(0.11f, 0.16f, 1f);
            return pc;
        }

        static SpriteRenderer AddSprite(Transform parent, string name, Sprite sprite, Color color, int order, float scale)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localScale = Vector3.one * scale;
            var sr = go.AddComponent<SpriteRenderer>();
            sr.sprite = sprite;
            sr.color = color;
            sr.sortingOrder = order;
            return sr;
        }

        public void SnapTo(Vector3 localPosition)
        {
            StopAllCoroutines();
            transform.localPosition = localPosition;
            core.color = accent;
            transform.localScale = Vector3.one;
        }

        public IEnumerator MoveTo(Vector3 target, float duration)
        {
            Vector3 from = transform.localPosition;
            core.color = accent;
            transform.localScale = Vector3.one;
            for (float t = 0f; t < duration; t += Time.deltaTime)
            {
                float k = 1f - Mathf.Pow(1f - t / duration, 3f); // ease-out cubic
                transform.localPosition = Vector3.LerpUnclamped(from, target, k);
                yield return null;
            }
            transform.localPosition = target;
        }

        /// <summary>Short nudge toward a blocked cell with a red flash.</summary>
        public IEnumerator Bump(Direction direction, float duration)
        {
            Vector3 home = transform.localPosition;
            GridPos d = direction.Delta();
            Vector3 offset = new Vector3(d.x, -d.y, 0f) * (BoardRenderer.CellSize * 0.18f);
            for (float t = 0f; t < duration; t += Time.deltaTime)
            {
                float k = Mathf.Sin(t / duration * Mathf.PI);
                transform.localPosition = home + offset * k;
                core.color = Color.Lerp(accent, NeonTheme.Blocked, k);
                yield return null;
            }
            transform.localPosition = home;
            core.color = accent;
        }

        public IEnumerator Celebrate(float duration)
        {
            for (float t = 0f; t < duration; t += Time.deltaTime)
            {
                float k = Mathf.Sin(t / duration * Mathf.PI);
                transform.localScale = Vector3.one * (1f + 0.25f * k);
                glow.color = NeonTheme.WithAlpha(accent, 0.8f + 0.2f * k);
                yield return null;
            }
            transform.localScale = Vector3.one;
        }
    }
}
