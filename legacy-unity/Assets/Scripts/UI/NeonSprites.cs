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

        static Sprite roundedTile, roundedFrame, circle, ring, glow, square, star, arrow;

        public static Sprite RoundedTile { get { return roundedTile ?? (roundedTile = Make("RoundedTile", RoundedRect(0.22f, 0f))); } }
        public static Sprite RoundedFrame { get { return roundedFrame ?? (roundedFrame = Make("RoundedFrame", RoundedRect(0.08f, 0f))); } }
        public static Sprite Circle { get { return circle ?? (circle = Make("Circle", Disc(0.42f, 0f))); } }
        public static Sprite Ring { get { return ring ?? (ring = Make("Ring", Disc(0.40f, 0.29f))); } }
        public static Sprite Glow { get { return glow ?? (glow = Make("Glow", SoftGlow)); } }
        public static Sprite Star { get { return star ?? (star = Make("Star", StarShape)); } }
        public static Sprite Arrow { get { return arrow ?? (arrow = Make("Arrow", ArrowShape)); } }
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

        static readonly Vector2[] starPoints = BuildStar();

        static Vector2[] BuildStar()
        {
            var pts = new Vector2[10];
            for (int i = 0; i < 10; i++)
            {
                float r = i % 2 == 0 ? 0.47f : 0.2f;
                float a = Mathf.PI * 0.5f + i * Mathf.PI / 5f;
                pts[i] = new Vector2(Mathf.Cos(a) * r, Mathf.Sin(a) * r - 0.02f);
            }
            return pts;
        }

        static bool InPolygon(Vector2 p, Vector2[] poly)
        {
            bool inside = false;
            for (int i = 0, j = poly.Length - 1; i < poly.Length; j = i++)
            {
                if ((poly[i].y > p.y) != (poly[j].y > p.y) &&
                    p.x < (poly[j].x - poly[i].x) * (p.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x)
                    inside = !inside;
            }
            return inside;
        }

        static float StarShape(float u, float v)
        {
            // 4x4 supersampling for smooth edges.
            const int n = 4;
            float px = 1f / Size, hits = 0f;
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (InPolygon(new Vector2(u + ((i + 0.5f) / n - 0.5f) * px, v + ((j + 0.5f) / n - 0.5f) * px), starPoints)) hits++;
            return hits / (n * n);
        }

        static readonly Vector2[] arrowPoints =
        {
            new Vector2(-0.42f, 0.11f), new Vector2(0.06f, 0.11f), new Vector2(0.06f, 0.3f),
            new Vector2(0.44f, 0f), new Vector2(0.06f, -0.3f), new Vector2(0.06f, -0.11f), new Vector2(-0.42f, -0.11f)
        };

        /// <summary>Arrow pointing right (+x). Rotate the transform for other directions.</summary>
        static float ArrowShape(float u, float v)
        {
            const int n = 4;
            float px = 1f / Size, hits = 0f;
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (InPolygon(new Vector2(u + ((i + 0.5f) / n - 0.5f) * px, v + ((j + 0.5f) / n - 0.5f) * px), arrowPoints)) hits++;
            return hits / (n * n);
        }

        static float SoftGlow(float u, float v)
        {
            float r = Mathf.Sqrt(u * u + v * v) / 0.5f;
            float t = Mathf.Clamp01(1f - r);
            return t * t * t;
        }
    }
}
