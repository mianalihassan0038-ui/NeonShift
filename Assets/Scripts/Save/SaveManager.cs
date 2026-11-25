using UnityEngine;

namespace NeonShift
{
    /// <summary>Local progress (PlayerPrefs). Settings come in Build 003.</summary>
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

        /// <summary>Best star rating for a level (0 = not completed).</summary>
        public static int GetStars(LevelData level)
        {
            return PlayerPrefs.GetInt(StarsKey(level), 0);
        }

        /// <summary>Saves the rating if it beats the stored one. Returns the best rating.</summary>
        public static int RecordStars(LevelData level, int stars)
        {
            int best = GetStars(level);
            if (stars > best)
            {
                PlayerPrefs.SetInt(StarsKey(level), stars);
                PlayerPrefs.Save();
                best = stars;
            }
            return best;
        }

        static string StarsKey(LevelData level) { return "NeonShift.Stars." + level.worldId + "-" + level.levelId; }

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
