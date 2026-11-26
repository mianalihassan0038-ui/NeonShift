using System;
using UnityEngine;

namespace NeonShift
{
    /// <summary>On/off pill switch with a label on the left.</summary>
    public sealed class UIToggle : MonoBehaviour
    {
        public Action<bool> Changed;
        bool value;
        SpriteRenderer track, knob;
        Vector2 hitSize;

        public static UIToggle Create(Transform parent, string label, Vector3 pos, float width, int order)
        {
            var go = new GameObject("Toggle " + label);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            var t = go.AddComponent<UIToggle>();
            t.hitSize = new Vector2(width, 1f);
            UIKit.Text("Label", go.transform, new Vector3(-width * 0.5f, 0f, 0f), 0.42f, NeonTheme.Text, TextAnchor.MiddleLeft, order);
            go.transform.Find("Label").GetComponent<TextMesh>().text = label;
            t.track = UIKit.Sprite("Track", go.transform, NeonSprites.RoundedTile, NeonTheme.Panel, new Vector3(width * 0.5f - 0.6f, 0f, 0f), new Vector2(1.2f, 0.62f), order);
            t.knob = UIKit.Sprite("Knob", go.transform, NeonSprites.Circle, NeonTheme.Text, Vector3.zero, Vector2.one * 0.62f, order + 1);
            return t;
        }

        public bool Value
        {
            get { return value; }
            set
            {
                this.value = value;
                Vector3 c = track.transform.localPosition;
                knob.transform.localPosition = c + new Vector3(value ? 0.3f : -0.3f, 0f, 0f);
                track.color = value ? NeonTheme.Success : NeonTheme.WithAlpha(NeonTheme.Text, 0.2f);
            }
        }

        public bool HandleTap(Vector3 worldPoint)
        {
            if (!gameObject.activeInHierarchy) return false;
            Vector3 local = worldPoint - transform.position;
            if (Mathf.Abs(local.x) > hitSize.x * 0.5f || Mathf.Abs(local.y) > hitSize.y * 0.5f) return false;
            Value = !value;
            if (AudioManager.Instance != null) AudioManager.Instance.Play(Sfx.Click);
            if (Changed != null) Changed(value);
            return true;
        }
    }

    /// <summary>Horizontal 0..1 slider. Tap or drag on the track to set it.</summary>
    public sealed class UISlider : MonoBehaviour
    {
        public Action<float> Changed;
        float value;
        float trackWidth;
        Transform fill;
        Transform knob;

        public static UISlider Create(Transform parent, string label, Vector3 pos, float trackWidth, Color accent, int order)
        {
            var go = new GameObject("Slider " + label);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            var s = go.AddComponent<UISlider>();
            s.trackWidth = trackWidth;
            UIKit.Text("Label", go.transform, new Vector3(-trackWidth * 0.5f - 0.3f, 0f, 0f), 0.4f, NeonTheme.Text, TextAnchor.MiddleRight, order).text = label;
            UIKit.Sprite("Track", go.transform, NeonSprites.RoundedTile, NeonTheme.WithAlpha(NeonTheme.Text, 0.15f), Vector3.zero, new Vector2(trackWidth, 0.3f), order);
            s.fill = UIKit.Sprite("Fill", go.transform, NeonSprites.Square, accent, Vector3.zero, new Vector2(trackWidth, 0.2f), order + 1).transform;
            s.knob = UIKit.Sprite("Knob", go.transform, NeonSprites.Circle, NeonTheme.Text, Vector3.zero, Vector2.one * 0.42f, order + 2).transform;
            return s;
        }

        public float Value
        {
            get { return value; }
            set
            {
                this.value = Mathf.Clamp01(value);
                float w = trackWidth * this.value;
                fill.localScale = new Vector3(Mathf.Max(0.001f, w), 0.2f, 1f);
                fill.localPosition = new Vector3(-trackWidth * 0.5f + w * 0.5f, 0f, 0f);
                knob.localPosition = new Vector3(-trackWidth * 0.5f + w, 0f, 0f);
            }
        }

        /// <summary>Returns true if the point is on the slider (and updates it).</summary>
        public bool HandlePointer(Vector3 worldPoint)
        {
            if (!gameObject.activeInHierarchy) return false;
            Vector3 local = worldPoint - transform.position;
            if (Mathf.Abs(local.y) > 0.5f || local.x < -trackWidth * 0.5f - 0.3f || local.x > trackWidth * 0.5f + 0.3f) return false;
            Value = (local.x + trackWidth * 0.5f) / trackWidth;
            if (Changed != null) Changed(value);
            return true;
        }
    }
}
