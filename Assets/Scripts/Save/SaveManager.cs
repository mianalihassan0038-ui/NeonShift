using UnityEngine;

namespace NeonShift
{
    /// <summary>Local progress (PlayerPrefs). Build 003 will add per-level stars and settings.</summary>
    public static class SaveManager
    {
        const string CurrentKey = "NeonShift.CurrentLevelIndex";
        const string HighestKey = "NeonShift.HighestUnlockedIndex";

        public static int CurrentLevelIndex
        {
            get { return PlayerPrefs.GetInt(CurrentKey, 0); }
            set { PlayerPrefs.SetInt(CurrentKey, value); PlayerPrefs.Save(); }
        }

        public static int HighestUnlockedIndex
        {
            get { return PlayerPrefs.GetInt(HighestKey, 0); }
        }

        public static void Unlock(int index)
        {
            if (index > HighestUnlockedIndex)
            {
                PlayerPrefs.SetInt(HighestKey, index);
                PlayerPrefs.Save();
            }
        }
    }
}
