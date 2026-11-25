#!/usr/bin/env bash
# Compiles the Unity-independent game rules with Mono and runs Tests/CoreTests.cs.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/neonshift-core-tests.exe"
mcs -nologo -langversion:7 -r:System.Runtime.Serialization -out:"$OUT" \
  Assets/Scripts/Core/GridPos.cs \
  Assets/Scripts/Core/Direction.cs \
  Assets/Scripts/Core/GameState.cs \
  Assets/Scripts/Levels/LevelData.cs \
  Assets/Scripts/Grid/BoardState.cs \
  Assets/Scripts/Movement/CollisionChecker.cs \
  Assets/Scripts/Puzzle/PuzzleState.cs \
  Assets/Scripts/Puzzle/PuzzleSolver.cs \
  Assets/Scripts/Puzzle/StarManager.cs \
  Assets/Scripts/History/UndoManager.cs \
  Tests/CoreTests.cs
mono "$OUT" Assets/Resources/Levels
