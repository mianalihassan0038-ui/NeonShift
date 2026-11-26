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

- **Core loop:** two boards that scale to fill the phone screen, swipe input (one swipe is queued while tokens move), paired movement checked before anything moves, wall and edge collision with a red "bump", move counter, win detection.
- **Onboarding:** new players go straight into level 1. Levels 1-3 use one board and a ghost finger shows the swipe; level 4 slides in the Shadow board with a guided demo. Early levels take seconds.
- **Rewards loop:** XP bar and player levels (each level up pays 100 coins + a hint; milestones unlock Daily, Relax mode, Time Attack, skins and trails), a reward chest every 3 wins, win-streak coin multiplier (x2 at 3, x3 at 5), "So close! 1 move to win" nudge, auto-advance to the next level, sawtooth difficulty inside each world.
- **Five worlds, 63 levels, a new mechanic per world:** World 1 basics, World 2 Energy Cores (collect them all before the exit opens), World 3 Portals, World 4 Switches & Gates (one switch flips the gates on both boards), World 5 Crumble tiles plus every mechanic mixed. Each new mechanic gets an intro card. Every level is proven solvable by the solver and its 3-star score is the best possible.
- **Game feel:** particles, glowing trails, squash-and-stretch orbs with eyes, screen shake on a bump, combo sound pitch, core / portal / switch / crumble effects, confetti and stars popping in on a win, coins counting up, animated neon background.
- **Modes:** levels, Daily Challenge (mechanics rotate by day, same puzzle for everyone), Time Attack (90 s, every solve adds time, endless generated puzzles that get harder, best score saved).
- **Rewards:** coins, 7-day login reward calendar, Orb Shop with 8 colour skins, 11 achievements, hints (3 free, then 100 coins), profile with player level.
- **Polish:** synthesized music and sound effects with on/off and volume, haptics, settings screen, screen fades, Inter font.

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
