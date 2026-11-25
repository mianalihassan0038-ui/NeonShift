using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>Loads every level JSON under Assets/Resources/Levels, sorted by world then level.</summary>
    public static class LevelLoader
    {
        public const string ResourcesFolder = "Levels";

        public static List<LevelData> LoadAll()
        {
            var levels = new List<LevelData>();
            foreach (TextAsset asset in Resources.LoadAll<TextAsset>(ResourcesFolder))
            {
                LevelData level = JsonUtility.FromJson<LevelData>(asset.text);
                if (level == null)
                {
                    Debug.LogError("NEON SHIFT: could not parse level " + asset.name);
                    continue;
                }
                List<string> errors = level.Validate();
                if (errors.Count > 0)
                {
                    Debug.LogError("NEON SHIFT: level " + asset.name + " is invalid: " + string.Join("; ", errors.ToArray()));
                    continue;
                }
                levels.Add(level);
            }
            levels.Sort((a, b) => a.worldId != b.worldId ? a.worldId.CompareTo(b.worldId) : a.levelId.CompareTo(b.levelId));
            return levels;
        }
    }
}
