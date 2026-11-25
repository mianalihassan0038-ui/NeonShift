namespace NeonShift
{
    /// <summary>Coin rewards and profile XP. Plain C# so the numbers can be tested.</summary>
    public static class Rewards
    {
        public const int FirstClearCoins = 50;
        public const int CoinsPerNewStar = 25;
        public const int DailyCoins = 200;
        public const int HintCost = 100;
        public const int StartingHints = 3;
        public const int XpPerLevel = 1000;

        /// <summary>Coins for finishing a level, given the best stars before and the stars earned now.</summary>
        public static int LevelCoins(int previousBestStars, int earnedStars)
        {
            int coins = previousBestStars == 0 ? FirstClearCoins : 0;
            if (earnedStars > previousBestStars) coins += (earnedStars - previousBestStars) * CoinsPerNewStar;
            return coins;
        }

        public static int Xp(int levelsCompleted, int totalStars) { return levelsCompleted * 60 + totalStars * 40; }
        public static int PlayerLevel(int xp) { return 1 + xp / XpPerLevel; }
        public static int XpIntoLevel(int xp) { return xp % XpPerLevel; }

        /// <summary>Daily streak after completing today's daily, given the last day it was completed.</summary>
        public static int NextStreak(int lastDay, int today, int currentStreak, System.Func<int, int> nextDay)
        {
            if (lastDay == today) return currentStreak;
            if (lastDay != 0 && nextDay(lastDay) == today) return currentStreak + 1;
            return 1;
        }
    }

    public struct ProgressSnapshot
    {
        public int levelsCompleted;
        public int totalLevels;
        public int world1Completed;
        public int world1Total;
        public int perfectLevels;
        public int noHintLevels;
        public int bestDailyStreak;
        public int dailiesCompleted;
    }

    public struct Achievement
    {
        public string title;
        public string description;
        public int progress;
        public int goal;
        public bool Unlocked { get { return progress >= goal; } }
    }

    public static class Achievements
    {
        public static Achievement[] Evaluate(ProgressSnapshot p)
        {
            return new[]
            {
                Make("First Contact", "Complete your first level", p.levelsCompleted, 1),
                Make("World Clear", "Complete every World 01 level", p.world1Completed, p.world1Total),
                Make("Puzzle Master", "Complete all " + p.totalLevels + " levels", p.levelsCompleted, p.totalLevels),
                Make("Perfect Solver", "Get 3 stars on 10 levels", p.perfectLevels, 10),
                Make("No Hint Challenge", "Finish 10 levels without hints", p.noHintLevels, 10),
                Make("Daily Player", "Reach a 7-day daily streak", p.bestDailyStreak, 7)
            };
        }

        static Achievement Make(string title, string description, int progress, int goal)
        {
            if (goal < 1) goal = 1;
            return new Achievement { title = title, description = description, progress = System.Math.Min(progress, goal), goal = goal };
        }
    }
}
