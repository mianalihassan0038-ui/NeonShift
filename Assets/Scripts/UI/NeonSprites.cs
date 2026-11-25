using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// Generates simple placeholder sprites in code (rounded tiles, rings, glows) so the
    /// prototype needs no art files. Every sprite is exactly 1 x 1 world unit; scale the transform.
    /// Replace with real art later in Assets/Art.
    /// </summary>
    public static class NeonSprites
    {
        const int Size = 128;

        static Sprite roundedTile, roundedFrame, circle, ring, glow, square;

        public static Sprite RoundedTile { get { return roundedTile ?? (roundedTile = Make("RoundedTile", RoundedRect(0.22f, 0f))); } }
        public static Sprite RoundedFrame { get { return roundedFrame ?? (roundedFrame = Make("RoundedFrame", RoundedRect(0.08f, 0f))); } }
        public static Sprite Circle { get { return circle ?? (circle = Make("Circle", Disc(0.42f, 0f))); } }
        public static Sprite Ring { get { return ring ?? (ring = Make("Ring", Disc(0.40f, 0.29f))); } }
        public static Sprite Glow { get { return glow ?? (glow = Make("Glow", SoftGlow)); } }
        public static Sprite Square { get { return square ?? (square = Make("Square", (u, v) => 1f)); } }

        delegate float AlphaFn(float u, float v); // u, v in [-0.5, 0.5]

        static Sprite Make(string name, AlphaFn alpha)
        {
            var tex = new Texture2D(Size, Size, TextureFormat.RGBA32, false);
            tex.name = name;
            tex.wrapMode = TextureWrapMode.Clamp;
            tex.filterMode = FilterMode.Bilinear;
            var pixels = new Color32[Size * Size];
            for (int y = 0; y < Size; y++)
            {
                for (int x = 0; x < Size; x++)
                {
                    float u = (x + 0.5f) / Size - 0.5f;
                    float v = (y + 0.5f) / Size - 0.5f;
                    byte a = (byte)Mathf.RoundToInt(Mathf.Clamp01(alpha(u, v)) * 255f);
                    pixels[y * Size + x] = new Color32(255, 255, 255, a);
                }
            }
            tex.SetPixels32(pixels);
            tex.Apply(false, true);
            var sprite = Sprite.Create(tex, new Rect(0, 0, Size, Size), new Vector2(0.5f, 0.5f), Size);
            sprite.name = name;
            return sprite;
        }

        static float Edge(float distance)
        {
            // distance < 0 inside. One-pixel anti-aliased edge.
            float px = 1f / Size;
            return Mathf.Clamp01(0.5f - distance / px);
        }

        static AlphaFn RoundedRect(float radius, float unused)
        {
            return (u, v) =>
            {
                float half = 0.5f - 1.5f / Size;
                float qx = Mathf.Abs(u) - (half - radius);
                float qy = Mathf.Abs(v) - (half - radius);
                float outside = new Vector2(Mathf.Max(qx, 0f), Mathf.Max(qy, 0f)).magnitude;
                float inside = Mathf.Min(Mathf.Max(qx, qy), 0f);
                return Edge(outside + inside - radius);
            };
        }

        static AlphaFn Disc(float outerRadius, float innerRadius)
        {
            return (u, v) =>
            {
                float r = Mathf.Sqrt(u * u + v * v);
                float a = Edge(r - outerRadius);
                if (innerRadius > 0f) a *= 1f - Edge(r - innerRadius);
                return a;
            };
        }

        static float SoftGlow(float u, float v)
        {
            float r = Mathf.Sqrt(u * u + v * v) / 0.5f;
            float t = Mathf.Clamp01(1f - r);
            return t * t * t;
        }
    }
}
