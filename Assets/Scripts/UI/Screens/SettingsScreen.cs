using UnityEngine;

namespace NeonShift
{
    /// <summary>Music / sound / vibration toggles, volume sliders, privacy policy and about.</summary>
    public sealed class SettingsScreen : UIScreen
    {
        public const string PrivacyPolicyUrl = "https://github.com/mianalihassan0038-ui/NeonShift/blob/main/PRIVACY.md";
        public const string AboutUrl = "https://github.com/mianalihassan0038-ui/NeonShift";

        UIToggle music, sound, vibration;
        UISlider musicVolume, soundVolume;
        TextMesh coins;

        protected override void Build()
        {
            Background(transform, NeonTheme.Blue, NeonTheme.Shadow);
            Header("Settings", () => game.ShowScreen(AppScreen.MainMenu), out coins);

            UIKit.Card("Card", transform, new Vector3(0f, 1.6f, 0f), new Vector2(7.8f, 9.0f), NeonTheme.Blue, 5);

            music = UIToggle.Create(transform, "Music", new Vector3(0f, 5.0f, 0f), 6.4f, 20);
            musicVolume = UISlider.Create(transform, "", new Vector3(0.5f, 4.0f, 0f), 5.0f, NeonTheme.Light, 20);
            sound = UIToggle.Create(transform, "Sound Effects", new Vector3(0f, 2.8f, 0f), 6.4f, 20);
            soundVolume = UISlider.Create(transform, "", new Vector3(0.5f, 1.8f, 0f), 5.0f, NeonTheme.Light, 20);
            vibration = UIToggle.Create(transform, "Vibration", new Vector3(0f, 0.6f, 0f), 6.4f, 20);
            toggles.Add(music); toggles.Add(sound); toggles.Add(vibration);
            sliders.Add(musicVolume); sliders.Add(soundVolume);

            music.Changed = v => { SettingsData.Music = v; game.Audio.ApplySettings(); };
            sound.Changed = v => { SettingsData.Sound = v; };
            vibration.Changed = v => { SettingsData.Vibration = v; Haptics.Pulse(40); };
            musicVolume.Changed = v => { SettingsData.MusicVolume = v; game.Audio.ApplySettings(); };
            soundVolume.Changed = v => { SettingsData.SoundVolume = v; game.Audio.ApplySettings(); };

            Button("Privacy Policy", new Vector3(0f, -1.0f, 0f), new Vector2(6.2f, 0.95f), NeonTheme.Blue, () => Application.OpenURL(PrivacyPolicyUrl));
            Button("About Us", new Vector3(0f, -2.2f, 0f), new Vector2(6.2f, 0.95f), NeonTheme.Blue, () => Application.OpenURL(AboutUrl));

            UIKit.Text("Version", transform, new Vector3(0f, -4.3f, 0f), 0.28f, NeonTheme.WithAlpha(NeonTheme.Text, 0.45f)).text = "NEON SHIFT  v" + Application.version;
            UIKit.Text("Offline", transform, new Vector3(0f, -4.9f, 0f), 0.26f, NeonTheme.WithAlpha(NeonTheme.Text, 0.45f)).text = "Works offline. No account, no ads, no tracking.";
        }

        public override void OnShow()
        {
            coins.text = SaveManager.Coins.ToString();
            music.Value = SettingsData.Music;
            sound.Value = SettingsData.Sound;
            vibration.Value = SettingsData.Vibration;
            musicVolume.Value = SettingsData.MusicVolume;
            soundVolume.Value = SettingsData.SoundVolume;
        }
    }
}
