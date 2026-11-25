using System.Collections.Generic;

namespace NeonShift
{
    /// <summary>Knows the level list and which level is current.</summary>
    public sealed class LevelManager
    {
        readonly List<LevelData> levels;

        public LevelManager(List<LevelData> levels)
        {
            this.levels = levels;
            CurrentIndex = levels.Count == 0 ? 0 : System.Math.Min(SaveManager.CurrentLevelIndex, levels.Count - 1);
        }

        public int Count { get { return levels.Count; } }
        public int CurrentIndex { get; private set; }
        public LevelData Current { get { return levels.Count == 0 ? null : levels[CurrentIndex]; } }
        public bool IsLastLevel { get { return CurrentIndex >= levels.Count - 1; } }

        /// <summary>Moves to the next level (wraps to the first after the last). Saves progress.</summary>
        public void Advance()
        {
            if (levels.Count == 0) return;
            CurrentIndex = (CurrentIndex + 1) % levels.Count;
            SaveManager.Unlock(CurrentIndex);
            SaveManager.CurrentLevelIndex = CurrentIndex;
        }
    }
}
