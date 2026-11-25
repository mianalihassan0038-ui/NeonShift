# NEON SHIFT

A portrait 2D neon puzzle game for Android, built with **Unity 2D + C#**.

You control two linked characters at once: cyan **Light** and violet **Shadow**.
Every swipe moves Light in the swipe direction and Shadow in the **opposite** direction.
If either one would hit a wall or a board edge, **neither** moves. Get both onto their own exits to win.

<img src="docs/layout-preview.png" width="270" alt="Layout preview of the gameplay screen">

_The image is a layout preview rendered outside Unity, not an in-game screenshot._

## Status: Build 003 (menus and flow)

Build 001, the core loop:

- Two synchronized 5×5 boards, stacked for portrait.
- Swipe input (touch and mouse) plus arrow keys / WASD in the Editor. One gesture is one move; a second finger cancels it; input is locked while tokens animate.
- Paired movement: both destinations are validated first, then both move together.
- Wall and edge collision, with a red "bump" showing which character was blocked.
- Move counter (rejected swipes don't count) and win detection (both on their exits).
- 8 World 01 levels (4 to 14 moves), each proven solvable by an automated solver.

Build 002, the puzzle tools:

- **Undo**: restores both characters and the move count, one step at a time (button, `Z`, `Backspace`, or Android back).
- **Restart**: resets the whole level (button or `R`).
- **Move limit**: each level has `maxMoves`. The counter shows `MOVES 3/11` and turns yellow near the end. Running out shows an **Out of moves** panel with Undo and Retry.
- **Stars**: 1 to 3 stars from each level's thresholds, shown on the level-complete panel. The best rating per level is saved.

Not added yet: energy nodes. The plan says to compare the game with and without them first, so that is still an open decision.

Build 003, menus and flow:

- **Main menu**: NEON / SHIFT title, **PLAY** (continues your current level), **LEVELS**, and your total stars.
- **Level select**: a grid of World 01 levels. Locked levels are dimmed, completed ones show their best stars, and the current one is highlighted. Finishing a level unlocks the next.
- **Pause**: the `II` button (or Android back / Escape) opens Resume, Restart, Levels and Menu.
- Teaching hints under the boards on levels 1 to 4.
- After the last level, **LEVELS** takes you back to level select.

All screens live in the one Gameplay scene and are switched on and off in code, instead of the separate Boot / MainMenu / LevelSelect scenes in the plan. This keeps the setup to a single empty scene.

Next up (polish): sound and music with a settings screen, haptics, more levels and worlds, and testing on real phones.

## Open it in Unity

1. Install **Unity Hub** and a **Unity 6** editor with **Android Build Support**.
2. Clone this repo, then in Unity Hub choose **Add → Add project from disk** and pick the `NeonShift` folder. If Hub asks for an editor version, pick your Unity 6 version.
3. On first open, the project sets portrait Player Settings and creates `Assets/Scenes/Gameplay.unity` automatically. (You can also use the **NEON SHIFT** menu.)
4. Press **Play**. Swipe with the mouse or use the arrow keys. `Z` undoes, `R` restarts, and in the Editor `N` skips a level.
5. After the first open, commit the `.meta` files Unity creates.

Everything (camera, boards, tokens, text) is created in code by `GameManager`, so the scene can stay empty and the prototype needs no art files.

## Play it on an Android phone

File → Build Profiles (Build Settings) → Android → Switch Platform. Set a package name in Player Settings (for example `com.yourname.neonshift`), connect the phone with USB debugging on, and choose **Build And Run**. Use APK for testing, AAB for the Play Store.

## Project layout

```
Assets/
  Editor/NeonShiftProjectSetup.cs   portrait settings + scene creation
  Resources/Levels/World01/*.json   level files (format below)
  Scripts/
    Core/      GameManager, GameState, GridPos, Direction
    Levels/    LevelData, LevelLoader, LevelManager
    Grid/      BoardState, BoardRenderer
    Player/    PlayerController (token visuals and animation)
    Input/     SwipeInput
    Movement/  MovementManager, CollisionChecker
    Puzzle/    PuzzleState (the rules), PuzzleSolver (shortest solution), StarManager
    History/   UndoManager (state snapshots)
    UI/        GameplayUI, MainMenuUI, LevelSelectUI, WorldButton, NeonTheme, NeonSprites
    Save/      SaveManager
Tests/CoreTests.cs        rule tests that run without Unity
tools/run-core-tests.sh   compiles and runs the tests with Mono
tools/generate_levels.py  helper that searches for new levels
```

The rule code (`GridPos`, `Direction`, `LevelData`, `BoardState`, `CollisionChecker`, `PuzzleState`, `PuzzleSolver`, `StarManager`, `UndoManager`) does not use `UnityEngine`, so it can be tested anywhere.

## Level format

Example (level 3, wall lists shortened):

Coordinates are `x` = column (0 = left) and `y` = row (**0 = top**).

```jsonc
{
  "levelId": 3, "worldId": 1, "gridWidth": 5, "gridHeight": 5,
  "maxMoves": 11,
  "hint": "Plan a route that works on BOTH boards.",
  "light":  { "start": {"x":3,"y":1}, "exit": {"x":3,"y":3}, "walls": [{"x":0,"y":0}] },
  "shadow": { "start": {"x":2,"y":2}, "exit": {"x":2,"y":0}, "walls": [{"x":1,"y":1}] },
  "energyNodes": [],
  "starThresholds": { "threeStarsMaxMoves": 6, "twoStarsMaxMoves": 8, "oneStarMaxMoves": 11 },
  "_solution": "LLDDRR"
}
```

`maxMoves` is the move limit (0 = no limit). `starThresholds` sets the 3/2/1 star cut-offs. `_solution` is a known shortest solution kept for QA; the game ignores it.

**Design note:** with the "both or neither" rule, Light's total movement is always the exact mirror of Shadow's. So in every level, `light.exit − light.start` must equal `shadow.start − shadow.exit`, otherwise it can't be solved. The tests check every level.

## Tests

```
sudo apt install mono-devel   # once
tools/run-core-tests.sh
```

They check all four directions, Light-blocked and Shadow-blocked cases, board edges, the move counter, the win rule, undo, restart, the move limit, stars, and that every shipped level is valid and solvable.

## Make new levels

```
python3 tools/generate_levels.py <seed> <target_moves> [count]
```

It prints random 5×5 levels whose shortest solution is exactly `target_moves` and needs a detour. Copy a good one into a JSON file, then run the tests.
