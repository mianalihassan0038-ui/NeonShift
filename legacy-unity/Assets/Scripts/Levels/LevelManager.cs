using System.Collections.Generic;

namespace NeonShift
{
    /// <summary>Knows the level list, which level is current, and which levels are unlocked.</summary>
    public sealed class LevelManager
    {
        readonly List<LevelData> levels;

        public LevelManager(List<LevelData> levels)
        {
            this.levels = levels;
            CurrentIndex = levels.Count == 0 ? 0 : System.Math.Min(SaveManager.CurrentLevelIndex, levels.Count - 1);
        }

        public int Count { get { return levels.Count; } }
        public IList<LevelData> All { get { return levels.AsReadOnly(); } }
        public int CurrentIndex { get; private set; }
        public LevelData Current { get { return levels.Count == 0 ? null : levels[CurrentIndex]; } }
        public bool IsLastLevel { get { return CurrentIndex >= levels.Count - 1; } }

        public bool IsUnlocked(int index) { return index >= 0 && index < levels.Count && index <= SaveManager.HighestUnlockedIndex; }

        public int TotalStars
        {
            get
            {
                int total = 0;
                foreach (var l in levels) total += SaveManager.GetStars(l);
                return total;
            }
        }

        public List<int> IndicesInWorld(int worldId)
        {
            var result = new List<int>();
            for (int i = 0; i < levels.Count; i++) if (levels[i].worldId == worldId) result.Add(i);
            return result;
        }

        public int CountInWorld(int worldId) { return IndicesInWorld(worldId).Count; }

        public int StarsInWorld(int worldId)
        {
            int total = 0;
            foreach (int i in IndicesInWorld(worldId)) total += SaveManager.GetStars(levels[i]);
            return total;
        }

        /// <summary>A world is open once its first level is unlocked.</summary>
        public bool IsWorldUnlocked(int worldId)
        {
            List<int> idx = IndicesInWorld(worldId);
            return idx.Count > 0 && IsUnlocked(idx[0]);
        }

        public void Select(int index)
        {
            if (!IsUnlocked(index)) return;
            CurrentIndex = index;
            SaveManager.CurrentLevelIndex = CurrentIndex;
        }

        /// <summary>Call when the current level is completed: unlocks the next one.</summary>
        public void MarkCurrentCompleted()
        {
            if (CurrentIndex + 1 < levels.Count) SaveManager.Unlock(CurrentIndex + 1);
        }

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
