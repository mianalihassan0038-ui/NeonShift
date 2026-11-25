using System;
using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>Settings: sound, music, vibration, privacy policy link.</summary>
    public sealed class SettingsUI : MonoBehaviour
    {
        public const string PrivacyPolicyUrl = "https://github.com/mianalihassan0038-ui/NeonShift/blob/main/PRIVACY.md";

        public Action BackPressed;
        public Action SettingsChanged;

        WorldButton sound, music, vibration;
        readonly List<WorldButton> buttons = new List<WorldButton>();

        public void Build()
        {
            GameplayUI.MakeText("Title", transform, new Vector3(0f, 6.2f, 0f), 0.75f, NeonTheme.Text, TextAnchor.MiddleCenter, 20).text = "SETTINGS";

            sound = Add("SOUND", 3.4f, NeonTheme.Light, () => { SettingsData.Sound = !SettingsData.Sound; });
            music = Add("MUSIC", 1.8f, NeonTheme.Shadow, () => { SettingsData.Music = !SettingsData.Music; });
            vibration = Add("VIBRATION", 0.2f, NeonTheme.Light, () => { SettingsData.Vibration = !SettingsData.Vibration; Haptics.Pulse(40); });

            var privacy = WorldButton.Create(transform, "PRIVACY POLICY", new Vector3(0f, -2.4f, 0f), new Vector2(5.6f, 1.0f), NeonTheme.WithAlpha(NeonTheme.Text, 0.7f), 20);
            privacy.Clicked = () => Application.OpenURL(PrivacyPolicyUrl);
            buttons.Add(privacy);

            GameplayUI.MakeText("Version", transform, new Vector3(0f, -4.0f, 0f), 0.28f, NeonTheme.WithAlpha(NeonTheme.Text, 0.45f), TextAnchor.MiddleCenter, 20).text = "Version " + Application.version;

            var back = WorldButton.Create(transform, "BACK", new Vector3(0f, -7.4f, 0f), new Vector2(3.4f, 1.0f), NeonTheme.Shadow, 20);
            back.Clicked = () => { if (BackPressed != null) BackPressed(); };
            buttons.Add(back);
        }

        WorldButton Add(string name, float y, Color accent, Action toggle)
        {
            var b = WorldButton.Create(transform, name, new Vector3(0f, y, 0f), new Vector2(5.6f, 1.2f), accent, 20);
            b.Clicked = () =>
            {
                toggle();
                Refresh();
                if (SettingsChanged != null) SettingsChanged();
            };
            buttons.Add(b);
            return b;
        }

        public void Refresh()
        {
            sound.Label.text = "SOUND: " + (SettingsData.Sound ? "ON" : "OFF");
            music.Label.text = "MUSIC: " + (SettingsData.Music ? "ON" : "OFF");
            vibration.Label.text = "VIBRATION: " + (SettingsData.Vibration ? "ON" : "OFF");
        }

        public bool HandleTap(Vector3 worldPoint) { return WorldButton.TapAny(buttons, worldPoint); }
    }
}
