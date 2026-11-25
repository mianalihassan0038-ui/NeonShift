# NEON SHIFT

A portrait 2D neon puzzle game for Android, built with **Unity 2D + C#**.

You control two linked characters at once: cyan **Light** and violet **Shadow**.
Every swipe moves Light in the swipe direction and Shadow in the **opposite** direction.
If either one would hit a wall or a board edge, **neither** moves. Get both onto their own exits to win.

<img src="docs/layout-preview.png" width="270" alt="Layout preview of the gameplay screen">

_The image is a layout preview rendered outside Unity, not an in-game screenshot._

## Download

**Android APK:** [latest release](https://github.com/mianalihassan0038-ui/NeonShift/releases/latest) → `NeonShift.apk`.
On the phone, open the file and allow "Install unknown apps" when asked. The APK is built automatically by GitHub Actions on every push to `main` (debug-signed, for testing).

## What's in the game

Following every build in the game plan:

- **Build 001, core loop:** two 5×5 boards, swipe input (one gesture = one move, multitouch cancels, input locked while animating), paired movement validated before anything moves, wall and edge collision with a red "bump", move counter, win detection.
- **Build 002, puzzle tools:** undo (full-state snapshots), restart, per-level move limit with a Level Failed panel, 1–3 star rating saved per level.
- **Build 003, UI and content:** splash with loading bar, main menu, world select, world map with level nodes and stars, pause panel, Level Complete / Level Failed panels, 20 levels in two worlds of 10 (every one proven solvable), first-time swipe tutorial and teaching hints.
- **Polish:** synthesized music and sound effects with on/off and volume, short haptics, settings screen, safe-area aware layout for notched phones, screen fades, Inter font.
- **Extras from the UI designs:** Daily Challenge (a new solver-checked puzzle each day, same for everyone, with streaks and a coin reward), Hint button (the solver shows the next correct swipe; 3 free hints, then 100 coins each), coins, achievements and a profile with player level.
- **Release prep:** app icon, store listing draft (`STORE_LISTING.md`), privacy policy (`PRIVACY.md`), feature graphic (`docs/store/`), Android build script with IL2CPP + ARM64/ARMv7 and an AAB option.

Not included, on purpose: the Shop, real-money purchases and ads (the plan says to add those only after the game is stable), the "Switch" button from the mockups (no matching mechanic in the plan), and Worlds 3–5 (their mechanics, portals, gates and one-way paths, are "later" in the plan; the world cards show "Coming soon").

## Open it in Unity

1. Install **Unity Hub** and a **Unity 6** editor with **Android Build Support**.
2. Clone this repo, then in Unity Hub choose **Add → Add project from disk** and pick the `NeonShift` folder. If Hub asks for an editor version, pick your Unity 6 version.
3. On first open, the project sets portrait Player Settings and creates `Assets/Scenes/Gameplay.unity` automatically. (You can also use the **NEON SHIFT** menu.)
4. Press **Play**. Swipe with the mouse or use the arrow keys. `Z` undoes, `R` restarts, `H` shows a hint, and in the Editor `N` skips a level.
5. After the first open, commit the `.meta` files Unity creates.

Everything (camera, boards, tokens, text) is created in code by `GameManager`, so the scene can stay empty and the prototype needs no art files.

## Build the APK yourself

- In the Editor: **NEON SHIFT → Build Android APK** (writes `Builds/Android/NeonShift.apk`).
- Command line: `Unity -batchmode -quit -projectPath . -executeMethod NeonShift.EditorTools.BuildScript.BuildAndroid` (add `-androidAab` for a Play Store bundle).
- CI: `.github/workflows/build.yml` runs the rule tests, builds with [GameCI](https://game.ci), uploads the APK as an artifact and publishes a GitHub Release. It needs three repository secrets: `UNITY_LICENSE` (contents of your `Unity_lic.ulf`), `UNITY_EMAIL` and `UNITY_PASSWORD`.

The package name is `com.neonshift.game` (change it in `Assets/Editor/BuildScript.cs`).

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
    Puzzle/    PuzzleState (the rules), PuzzleSolver (shortest solution), StarManager, DailyChallenge
    History/   UndoManager (state snapshots)
    UI/        GameplayUI, UIKit, WorldButton, Widgets (toggle, slider), TutorialHint, NeonTheme, NeonSprites
    UI/Screens Splash, MainMenu, WorldSelect, LevelMap, Daily, Achievements, Settings, Profile
    Audio/     AudioManager (synthesized sounds), Haptics
    Progress/  Rewards, achievements (plain C#)
    Save/      SaveManager (progress, coins, hints, daily streak), SettingsData
  Resources/Fonts/                  Inter (SIL Open Font License, see OFL.txt)
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

They check all four directions, the daily-challenge generator, hints from any position, coin rewards and achievements, Light-blocked and Shadow-blocked cases, board edges, the move counter, the win rule, undo, restart, the move limit, stars, and that every shipped level is valid and solvable.

## Make new levels

```
python3 tools/generate_levels.py <seed> <target_moves> [count] [min_density] [max_density] [--climb]
```

It prints random 5×5 levels whose shortest solution is exactly `target_moves` and needs a detour. Copy a good one into a JSON file, then run the tests.
