using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// First-time hint on level 1: animated arrows show that Light goes with the swipe
    /// and Shadow goes the opposite way. Disappears after the first accepted move.
    /// </summary>
    public sealed class TutorialHint : MonoBehaviour
    {
        Transform lightArrow, shadowArrow;
        Vector3 lightHome, shadowHome;
        Direction direction;

        public static TutorialHint Create(Transform parent, Vector3 lightCenter, Vector3 shadowCenter)
        {
            var go = new GameObject("TutorialHint");
            go.transform.SetParent(parent, false);
            var hint = go.AddComponent<TutorialHint>();
            hint.lightArrow = MakeArrow(go.transform, NeonTheme.Light).transform;
            hint.shadowArrow = MakeArrow(go.transform, NeonTheme.Shadow).transform;
            hint.lightHome = lightCenter;
            hint.shadowHome = shadowCenter;
            go.SetActive(false);
            return hint;
        }

        static SpriteRenderer MakeArrow(Transform parent, Color color)
        {
            return GameplayUI.MakeSprite("Arrow", parent, NeonSprites.Arrow, NeonTheme.WithAlpha(color, 0.9f), Vector3.zero, Vector2.one * 1.6f, 30);
        }

        public void Show(Direction swipe)
        {
            direction = swipe;
            lightArrow.localRotation = Quaternion.Euler(0f, 0f, Angle(swipe));
            shadowArrow.localRotation = Quaternion.Euler(0f, 0f, Angle(swipe.Opposite()));
            gameObject.SetActive(true);
        }

        public void Hide() { gameObject.SetActive(false); }

        static float Angle(Direction d)
        {
            switch (d)
            {
                case Direction.Up: return 90f;
                case Direction.Left: return 180f;
                case Direction.Down: return -90f;
                default: return 0f;
            }
        }

        void Update()
        {
            float k = Mathf.Repeat(Time.time * 0.8f, 1f);
            float offset = Mathf.SmoothStep(-0.8f, 0.8f, k);
            float alpha = Mathf.Sin(k * Mathf.PI);
            GridPos d = direction.Delta();
            Vector3 dir = new Vector3(d.x, -d.y, 0f);
            lightArrow.localPosition = lightHome + dir * offset;
            shadowArrow.localPosition = shadowHome - dir * offset;
            SetAlpha(lightArrow, alpha);
            SetAlpha(shadowArrow, alpha);
        }

        static void SetAlpha(Transform t, float a)
        {
            var sr = t.GetComponent<SpriteRenderer>();
            sr.color = NeonTheme.WithAlpha(sr.color, a * 0.9f);
        }
    }
}
