using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>Base class for a full-screen menu page. GameManager switches them on and off.</summary>
    public abstract class UIScreen : MonoBehaviour
    {
        protected GameManager game;
        protected readonly List<WorldButton> buttons = new List<WorldButton>();
        protected readonly List<UIToggle> toggles = new List<UIToggle>();
        protected readonly List<UISlider> sliders = new List<UISlider>();

        public void Init(GameManager owner)
        {
            game = owner;
            Build();
        }

        protected abstract void Build();
        public virtual void OnShow() { }
        public virtual void Tick() { }

        public virtual bool HandleTap(Vector3 world)
        {
            foreach (var t in toggles) if (t.HandleTap(world)) return true;
            foreach (var s in sliders) if (s.HandlePointer(world)) return true;
            return WorldButton.TapAny(buttons, world);
        }

        public virtual void HandleDrag(Vector3 world)
        {
            foreach (var s in sliders) if (s.HandlePointer(world)) return;
        }

        protected WorldButton Button(string text, Vector3 pos, Vector2 size, Color accent, System.Action onClick,
                                     ButtonStyle style = ButtonStyle.Outline, Transform parent = null, string caption = null)
        {
            var b = WorldButton.Create(parent ?? transform, text, pos, size, accent, 20, style, caption);
            b.Clicked = onClick;
            buttons.Add(b);
            return b;
        }

        /// <summary>Back arrow + centred title + coin counter, like the mockups.</summary>
        protected TextMesh Header(string title, System.Action onBack, out TextMesh coins)
        {
            Button("←", new Vector3(-3.75f, 7.35f, 0f), new Vector2(1.0f, 1.0f), NeonTheme.Light, onBack, ButtonStyle.Round);
            var t = UIKit.Text("Title", transform, new Vector3(0f, 7.35f, 0f), 0.62f, NeonTheme.Text);
            t.text = title;
            coins = UIKit.CoinCounter(transform, new Vector3(2.55f, 7.35f, 0f), 0.42f, 20);
            return t;
        }

        protected static void Background(Transform parent, Color a, Color b)
        {
            UIKit.Sprite("BgGlowA", parent, NeonSprites.Glow, NeonTheme.WithAlpha(a, 0.16f), new Vector3(-2.5f, 4.5f, 0f), new Vector2(12f, 10f), -5);
            UIKit.Sprite("BgGlowB", parent, NeonSprites.Glow, NeonTheme.WithAlpha(b, 0.16f), new Vector3(2.5f, -4.5f, 0f), new Vector2(12f, 10f), -5);
        }
    }
}
