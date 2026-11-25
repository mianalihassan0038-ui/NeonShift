using UnityEngine;

namespace NeonShift
{
    /// <summary>Short vibration pulses on Android. Respects the Vibration setting.</summary>
    public static class Haptics
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        static AndroidJavaObject vibrator;
        static bool failed;
#endif

        public static void Pulse(long milliseconds)
        {
            if (!SettingsData.Vibration) return;
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!failed)
            {
                try
                {
                    if (vibrator == null)
                    {
                        using (var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                        using (var activity = player.GetStatic<AndroidJavaObject>("currentActivity"))
                            vibrator = activity.Call<AndroidJavaObject>("getSystemService", "vibrator");
                    }
                    vibrator.Call("vibrate", milliseconds);
                    return;
                }
                catch (System.Exception)
                {
                    failed = true;
                }
            }
            // Fallback (also makes Unity add the VIBRATE permission to the manifest).
            Handheld.Vibrate();
#endif
        }
    }
}
