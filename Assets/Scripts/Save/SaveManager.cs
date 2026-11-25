using System;
using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>Local progress and wallet (PlayerPrefs). Offline only, no accounts.</summary>
    public static class SaveManager
    {
        const string CurrentKey = "NeonShift.CurrentLevelIndex";
        const string HighestKey = "NeonShift.HighestUnlockedIndex";
        const string CoinsKey = "NeonShift.Coins";
        const string HintsKey = "NeonShift.Hints";
        const string DailyLastKey = "NeonShift.DailyLastDay";
        const string DailyStreakKey = "NeonShift.DailyStreak";
        const string DailyBestKey = "NeonShift.DailyBestStreak";
        const string DailyCountKey = "NeonShift.DailyCount";

        public static int CurrentLevelIndex
        {
            get { return PlayerPrefs.GetInt(CurrentKey, 0); }
            set { PlayerPrefs.SetInt(CurrentKey, value); PlayerPrefs.Save(); }
        }

        public static int HighestUnlockedIndex { get { return PlayerPrefs.GetInt(HighestKey, 0); } }

        public static void Unlock(int index)
        {
            if (index > HighestUnlockedIndex) { PlayerPrefs.SetInt(HighestKey, index); PlayerPrefs.Save(); }
        }

        // ---- Stars per level ----
        public static int GetStars(LevelData level) { return PlayerPrefs.GetInt(Key("Stars", level), 0); }

        /// <summary>Saves the rating if it beats the stored one. Returns the best rating.</summary>
        public static int RecordStars(LevelData level, int stars)
        {
            int best = GetStars(level);
            if (stars > best) { PlayerPrefs.SetInt(Key("Stars", level), stars); PlayerPrefs.Save(); best = stars; }
            return best;
        }

        public static bool CompletedWithoutHint(LevelData level) { return PlayerPrefs.GetInt(Key("NoHint", level), 0) == 1; }
        public static void MarkNoHint(LevelData level) { PlayerPrefs.SetInt(Key("NoHint", level), 1); PlayerPrefs.Save(); }

        static string Key(string kind, LevelData level) { return "NeonShift." + kind + "." + level.worldId + "-" + level.levelId; }

        // ---- Wallet ----
        public static int Coins { get { return PlayerPrefs.GetInt(CoinsKey, 0); } }
        public static void AddCoins(int amount) { PlayerPrefs.SetInt(CoinsKey, Math.Max(0, Coins + amount)); PlayerPrefs.Save(); }

        public static int Hints { get { return PlayerPrefs.GetInt(HintsKey, Rewards.StartingHints); } }

        /// <summary>Uses a free hint, or buys one with coins. Returns false if neither is possible.</summary>
        public static bool TrySpendHint()
        {
            if (Hints > 0) { PlayerPrefs.SetInt(HintsKey, Hints - 1); PlayerPrefs.Save(); return true; }
            if (Coins >= Rewards.HintCost) { AddCoins(-Rewards.HintCost); return true; }
            return false;
        }

        // ---- Daily challenge ----
        public static int DailyLastDay { get { return PlayerPrefs.GetInt(DailyLastKey, 0); } }
        public static int DailyStreak { get { return PlayerPrefs.GetInt(DailyStreakKey, 0); } }
        public static int DailyBestStreak { get { return PlayerPrefs.GetInt(DailyBestKey, 0); } }
        public static int DailiesCompleted { get { return PlayerPrefs.GetInt(DailyCountKey, 0); } }

        /// <summary>Records today's daily. Returns coins awarded (0 if already done today).</summary>
        public static int CompleteDaily(int today)
        {
            if (DailyLastDay == today) return 0;
            int streak = Rewards.NextStreak(DailyLastDay, today, DailyStreak, NextDay);
            PlayerPrefs.SetInt(DailyLastKey, today);
            PlayerPrefs.SetInt(DailyStreakKey, streak);
            PlayerPrefs.SetInt(DailyBestKey, Math.Max(streak, DailyBestStreak));
            PlayerPrefs.SetInt(DailyCountKey, DailiesCompleted + 1);
            AddCoins(Rewards.DailyCoins);
            return Rewards.DailyCoins;
        }

        /// <summary>Current streak, or 0 if the player missed a day.</summary>
        public static int ActiveDailyStreak(int today)
        {
            int last = DailyLastDay;
            if (last == today || (last != 0 && NextDay(last) == today)) return DailyStreak;
            return 0;
        }

        public static int NextDay(int day)
        {
            var d = new DateTime(day / 10000, day / 100 % 100, day % 100).AddDays(1);
            return DailyChallenge.DayNumber(d);
        }

        public static ProgressSnapshot Snapshot(IList<LevelData> levels)
        {
            var p = new ProgressSnapshot { totalLevels = levels.Count, bestDailyStreak = DailyBestStreak, dailiesCompleted = DailiesCompleted };
            foreach (var l in levels)
            {
                int stars = GetStars(l);
                if (l.worldId == 1) p.world1Total++;
                if (stars <= 0) continue;
                p.levelsCompleted++;
                if (l.worldId == 1) p.world1Completed++;
                if (stars >= 3) p.perfectLevels++;
                if (CompletedWithoutHint(l)) p.noHintLevels++;
            }
            return p;
        }
    }
}
