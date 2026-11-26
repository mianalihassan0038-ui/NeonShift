using UnityEngine;

namespace NeonShift
{
    /// <summary>Player settings stored in PlayerPrefs.</summary>
    public static class SettingsData
    {
        const string SoundKey = "NeonShift.Sound";
        const string MusicKey = "NeonShift.Music";
        const string VibrationKey = "NeonShift.Vibration";
        const string TutorialKey = "NeonShift.TutorialDone";

        public static bool Sound { get { return Get(SoundKey, true); } set { Set(SoundKey, value); } }
        public static bool Music { get { return Get(MusicKey, true); } set { Set(MusicKey, value); } }
        public static bool Vibration { get { return Get(VibrationKey, true); } set { Set(VibrationKey, value); } }
        public static float MusicVolume { get { return PlayerPrefs.GetFloat("NeonShift.MusicVolume", 0.7f); } set { PlayerPrefs.SetFloat("NeonShift.MusicVolume", value); PlayerPrefs.Save(); } }
        public static float SoundVolume { get { return PlayerPrefs.GetFloat("NeonShift.SoundVolume", 0.8f); } set { PlayerPrefs.SetFloat("NeonShift.SoundVolume", value); PlayerPrefs.Save(); } }
        public static bool TutorialDone { get { return Get(TutorialKey, false); } set { Set(TutorialKey, value); } }

        static bool Get(string key, bool fallback) { return PlayerPrefs.GetInt(key, fallback ? 1 : 0) == 1; }
        static void Set(string key, bool value) { PlayerPrefs.SetInt(key, value ? 1 : 0); PlayerPrefs.Save(); }
    }
}
