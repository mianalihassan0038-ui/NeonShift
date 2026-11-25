using UnityEngine;

namespace NeonShift
{
    /// <summary>Colour palette from the plan (section 5).</summary>
    public static class NeonTheme
    {
        public static readonly Color Background = Hex(0x090D1B);
        public static readonly Color Light = Hex(0x00E5FF);
        public static readonly Color Shadow = Hex(0xA855F7);
        public static readonly Color Energy = Hex(0xFFD54A);
        public static readonly Color Text = Hex(0xF4F7FF);
        public static readonly Color Panel = Hex(0x121A33);
        public static readonly Color EmptyCell = Hex(0x18223F);
        public static readonly Color Blocked = Hex(0xFF4D6D);
        public static readonly Color Success = Hex(0x3EE06B);
        public static readonly Color Pink = Hex(0xFF4FD8);
        public static readonly Color Blue = Hex(0x2F7BFF);

        public static Color Hex(int rgb, float alpha = 1f)
        {
            return new Color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
        }

        public static Color WithAlpha(Color c, float a) { c.a = a; return c; }

        /// <summary>Wall colour: the world's accent mixed into the dark panel.</summary>
        public static Color WallFor(Color accent) { return Color.Lerp(Panel, accent, 0.4f); }
    }
}
