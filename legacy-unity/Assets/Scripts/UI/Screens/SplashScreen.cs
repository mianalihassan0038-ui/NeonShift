using UnityEngine;

namespace NeonShift
{
    /// <summary>Logo, two glowing characters on a portal ring, and a loading bar.</summary>
    public sealed class SplashScreen : UIScreen
    {
        public const float Duration = 1.8f;
        Transform barFill;
        TextMesh percent;
        float shownAt;
        Transform lightOrb, shadowOrb;

        protected override void Build()
        {
            Background(transform, NeonTheme.Light, NeonTheme.Shadow);
            BuildLogo(transform, 4.6f, 1.6f);
            UIKit.Text("Tagline", transform, new Vector3(0f, 2.1f, 0f), 0.42f, NeonTheme.Text).text = "One Move. Two Worlds.";

            UIKit.Sprite("Portal", transform, NeonSprites.Ring, NeonTheme.Light, new Vector3(0f, -2.4f, 0f), new Vector2(5.2f, 1.6f), 10);
            UIKit.Sprite("PortalGlow", transform, NeonSprites.Glow, NeonTheme.WithAlpha(NeonTheme.Shadow, 0.5f), new Vector3(0f, -2.4f, 0f), new Vector2(7f, 2.6f), 9);
            lightOrb = Character(transform, NeonTheme.Light, new Vector3(-1.0f, -1.2f, 0f));
            shadowOrb = Character(transform, NeonTheme.Shadow, new Vector3(1.0f, -1.2f, 0f));

            UIKit.Sprite("BarTrack", transform, NeonSprites.RoundedTile, NeonTheme.WithAlpha(NeonTheme.Text, 0.15f), new Vector3(0f, -5.6f, 0f), new Vector2(5.5f, 0.32f), 10);
            barFill = UIKit.Sprite("BarFill", transform, NeonSprites.Square, NeonTheme.Light, new Vector3(0f, -5.6f, 0f), new Vector2(0.01f, 0.18f), 11).transform;
            percent = UIKit.Text("Loading", transform, new Vector3(0f, -6.3f, 0f), 0.34f, NeonTheme.Text);
            UIKit.Text("Version", transform, new Vector3(0f, -7.1f, 0f), 0.28f, NeonTheme.WithAlpha(NeonTheme.Text, 0.5f)).text = "v" + Application.version;
        }

        /// <summary>The two-colour NEON / SHIFT logo, shared with the main menu.</summary>
        public static void BuildLogo(Transform parent, float y, float height)
        {
            UIKit.Sprite("LogoGlow", parent, NeonSprites.Glow, NeonTheme.WithAlpha(NeonTheme.Shadow, 0.35f), new Vector3(0f, y - height * 0.5f, 0f), new Vector2(9f, height * 3f), 5);
            UIKit.Text("Neon", parent, new Vector3(-0.2f, y, 0f), height, NeonTheme.Light, TextAnchor.MiddleCenter, 20, UIKit.LogoFont).text = "NEON";
            UIKit.Text("Shift", parent, new Vector3(0.25f, y - height * 0.95f, 0f), height, NeonTheme.Pink, TextAnchor.MiddleCenter, 20, UIKit.LogoFont).text = "SHIFT";
        }

        /// <summary>Simple glowing character: body orb with two eyes.</summary>
        public static Transform Character(Transform parent, Color color, Vector3 pos, float scale = 1.6f)
        {
            var root = new GameObject("Character").transform;
            root.SetParent(parent, false);
            root.localPosition = pos;
            root.localScale = Vector3.one * scale;
            UIKit.Sprite("Glow", root, NeonSprites.Glow, NeonTheme.WithAlpha(color, 0.8f), Vector3.zero, Vector2.one * 1.6f, 12);
            UIKit.Sprite("Body", root, NeonSprites.Circle, color, Vector3.zero, Vector2.one * 0.9f, 13);
            UIKit.Sprite("EyeL", root, NeonSprites.Circle, NeonTheme.Background, new Vector3(-0.13f, 0.06f, 0f), new Vector2(0.13f, 0.18f), 14);
            UIKit.Sprite("EyeR", root, NeonSprites.Circle, NeonTheme.Background, new Vector3(0.13f, 0.06f, 0f), new Vector2(0.13f, 0.18f), 14);
            return root;
        }

        public override void OnShow() { shownAt = Time.time; }

        public override void Tick()
        {
            float t = Mathf.Clamp01((Time.time - shownAt) / Duration);
            float w = 5.3f * t;
            barFill.localScale = new Vector3(Mathf.Max(0.01f, w), 0.18f, 1f);
            barFill.localPosition = new Vector3(-2.65f + w * 0.5f, -5.6f, 0f);
            percent.text = "Loading... " + Mathf.RoundToInt(t * 100f) + "%";
            float bob = Mathf.Sin(Time.time * 2.2f) * 0.12f;
            lightOrb.localPosition = new Vector3(-1.0f, -1.2f + bob, 0f);
            shadowOrb.localPosition = new Vector3(1.0f, -1.2f - bob, 0f);
            if (t >= 1f) game.ShowScreen(AppScreen.MainMenu);
        }

        public override bool HandleTap(Vector3 world) { return false; }
    }
}
