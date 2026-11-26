# NEON SHIFT

A portrait 2D neon puzzle game for Android, written in **Kotlin** (native Android, no game engine).

You control two linked characters at once: cyan **Light** and violet **Shadow**.
Every swipe moves Light in the swipe direction and Shadow in the **opposite** direction.
If either one would hit a wall or a board edge, **neither** moves. Get both onto their own exits to win.

## Download

**Android APK:** [latest release](https://github.com/mianalihassan0038-ui/NeonShift/releases/latest) → `NeonShift.apk` (Android 8.0 or newer).
On the phone, open the file and allow "Install unknown apps" when asked.
GitHub Actions builds, tests and publishes a new APK on every push to `main`. No licence or secret is needed.

## What's in the game

- **Core loop:** two 5×5 boards, swipe input (one gesture = one move, multitouch cancels, input locked while animating), paired movement checked before anything moves, wall and edge collision with a red "bump", move counter, win detection.
- **Puzzle tools:** undo, restart, per-level move limit with a Level Failed panel, 1–3 stars saved per level.
- **Screens:** splash with loading bar, main menu, world select, world map with level nodes and stars, pause panel, Level Complete / Level Failed panels, 20 levels in two worlds of 10 (every one proven solvable), first-time swipe tutorial.
- **Polish:** synthesized music and sound effects with on/off and volume, haptics, settings screen, screen fades, Inter font.
- **Extras from the UI designs:** Daily Challenge (a new solver-checked puzzle each day, same for everyone, with streaks and a coin reward), Hint button (3 free hints, then 100 coins each), coins, achievements and a profile with player level.

Not included, on purpose: the Shop, real-money purchases and ads, and Worlds 3–5 (shown as "Coming soon").

## Build it yourself

Install Android Studio (or JDK 17 + Android SDK), then:

```
./gradlew testReleaseUnitTest   # rule, level and daily-puzzle tests
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

Or open the folder in Android Studio and press Run.

The release APK is signed with the shared test key in `keystore/` so new builds install over old ones.
For the Play Store, add your own upload key as the secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.

## Project layout

```
app/src/main/java/com/neonshift/game/
  core/Core.kt      the rules: GridPos, Direction, BoardState, PuzzleState, PuzzleSolver (BFS), stars, daily puzzle, rewards, achievements
  core/Levels.kt    JSON level reader, save data (progress, coins, hints, streak, settings), LevelManager
  ui/Ui.kt          neon drawing helpers and widgets (buttons, toggles, sliders) on an Android Canvas
  ui/Board.kt       board drawing and the animated Light / Shadow tokens
  Game.kt           navigation and the menu screens
  Gameplay.kt       gameplay screen, HUD and the pause / win / fail / hint panels
  Sound.kt          synthesized sounds and music, haptics
  MainActivity.kt   activity, frame loop, touch to tap / swipe
app/src/main/assets/levels/   level files (format below)
app/src/main/assets/fonts/    Inter (SIL Open Font License, see OFL.txt)
app/src/test/                 unit tests
tools/generate_levels.py      helper that searches for new levels
tools/ci-screenshots.sh       CI emulator run that takes screenshots
legacy-unity/                 the earlier Unity version, kept for reference
```

The code in `core/` has no Android imports, so the tests run on a plain JVM.

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

`./gradlew testReleaseUnitTest` checks all four directions, blocked Light and blocked Shadow, board edges, the move counter, the win rule, undo, restart, the move limit, stars, rewards, the daily-challenge generator, and that every shipped level is valid and solvable with its 3-star target equal to the shortest solution.

## Make new levels

```
python3 tools/generate_levels.py <seed> <target_moves> [count] [min_density] [max_density] [--climb]
```

It prints random 5×5 levels whose shortest solution is exactly `target_moves` and needs a detour. Copy a good one into a JSON file, then run the tests.
