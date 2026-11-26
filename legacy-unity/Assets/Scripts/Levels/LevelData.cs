using System;
using System.Collections.Generic;

namespace NeonShift
{
    /// <summary>
    /// Serializable level model. Matches the JSON format in the game plan (section 9).
    /// Field names are lower camel case so Unity's JsonUtility can read the files directly.
    /// </summary>
    [Serializable]
    public class LevelData
    {
        public int levelId;
        public int worldId = 1;
        public int gridWidth = 5;
        public int gridHeight = 5;
        public int maxMoves;            // Build 002: move limit (0 = no limit). Not enforced in Build 001.
        public string hint = "";        // Short teaching text shown under the boards.
        public BoardData light = new BoardData();
        public BoardData shadow = new BoardData();
        public List<GridPos> energyNodes = new List<GridPos>(); // Build 002, kept empty for now.
        public StarThresholds starThresholds = new StarThresholds();

        /// <summary>Returns a list of problems. An empty list means the level is structurally valid.</summary>
        public List<string> Validate()
        {
            var errors = new List<string>();
            if (gridWidth < 2 || gridHeight < 2) errors.Add("grid must be at least 2x2");
            ValidateBoard("light", light, errors);
            ValidateBoard("shadow", shadow, errors);
            return errors;
        }

        void ValidateBoard(string name, BoardData board, List<string> errors)
        {
            if (board == null) { errors.Add(name + ": missing board"); return; }
            if (!InBounds(board.start)) errors.Add(name + ": start " + board.start + " is outside the grid");
            if (!InBounds(board.exit)) errors.Add(name + ": exit " + board.exit + " is outside the grid");
            if (board.start == board.exit) errors.Add(name + ": start and exit are the same cell");
            if (board.walls == null) return;
            foreach (var w in board.walls)
            {
                if (!InBounds(w)) errors.Add(name + ": wall " + w + " is outside the grid");
                if (w == board.start) errors.Add(name + ": wall on start " + w);
                if (w == board.exit) errors.Add(name + ": wall on exit " + w);
            }
        }

        bool InBounds(GridPos p) { return p.x >= 0 && p.y >= 0 && p.x < gridWidth && p.y < gridHeight; }
    }

    [Serializable]
    public class BoardData
    {
        public GridPos start;
        public GridPos exit;
        public List<GridPos> walls = new List<GridPos>();
    }

    [Serializable]
    public class StarThresholds
    {
        public int threeStarsMaxMoves;
        public int twoStarsMaxMoves;
        public int oneStarMaxMoves;
    }
}
