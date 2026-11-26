using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// Entry point. Creates the camera, audio, every screen, both boards, both tokens and the UI in code,
    /// so it runs from any scene (including an empty one). Screens are child objects switched on and off.
    /// </summary>
    public sealed class GameManager : MonoBehaviour
    {
        // Layout in world units inside a 9 x 16 portrait area centred on the origin.
        const float LightBoardY = 3.0f;
        const float ShadowBoardY = -3.55f;

        public static GameManager Instance { get; private set; }

        public GameState State { get; private set; }
        public AppScreen CurrentScreen { get; private set; }
        public LevelManager Levels { get; private set; }
        public AudioManager Audio { get; private set; }
        public int Today { get { return DailyChallenge.DayNumber(DateTime.Now); } }
        public bool DailyDoneToday { get { return SaveManager.DailyLastDay == Today; } }

        LevelData dailyLevel;
        int dailyDay;
        public LevelData DailyLevel
        {
            get
            {
                if (dailyLevel == null || dailyDay != Today) { dailyDay = Today; dailyLevel = DailyChallenge.Generate(dailyDay); }
                return dailyLevel;
            }
        }

        Camera cam;
        Vector2Int lastScreen;
        Rect lastSafeArea;
        SpriteRenderer fader;
        float fadeAlpha;

        readonly Dictionary<AppScreen, UIScreen> screens = new Dictionary<AppScreen, UIScreen>();
        GameObject gameplayRoot;
        PuzzleState puzzle;
        bool playingDaily;
        bool usedHintThisLevel;
        BoardRenderer lightBoard, shadowBoard;
        PlayerController lightToken, shadowToken;
        MovementManager movement;
        SwipeInput swipe;
        GameplayUI ui;
        TutorialHint arrows;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        static void AutoStart()
        {
            if (Instance == null) new GameObject("NEON SHIFT").AddComponent<GameManager>();
        }

        void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            State = GameState.Loading;

            Application.targetFrameRate = 60;
            Screen.orientation = ScreenOrientation.Portrait;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;

            SetupCamera();
            Audio = AudioManager.Create(transform);
            Levels = new LevelManager(LevelLoader.LoadAll());
            if (Levels.Count == 0) Debug.LogError("NEON SHIFT: no levels found in Assets/Resources/Levels");

            BuildGameplay();
            AddScreen<SplashScreen>(AppScreen.Splash);
            AddScreen<MainMenuScreen>(AppScreen.MainMenu);
            AddScreen<WorldSelectScreen>(AppScreen.WorldSelect);
            AddScreen<LevelMapScreen>(AppScreen.LevelSelect);
            AddScreen<DailyScreen>(AppScreen.Daily);
            AddScreen<AchievementsScreen>(AppScreen.Achievements);
            AddScreen<SettingsScreen>(AppScreen.Settings);
            AddScreen<ProfileScreen>(AppScreen.Profile);

            fader = UIKit.Sprite("Fader", transform, NeonSprites.Square, NeonTheme.Background, Vector3.zero, new Vector2(60f, 60f), 200);

            swipe = gameObject.AddComponent<SwipeInput>();
            swipe.Swiped += OnSwipe;
            swipe.Tapped += OnTap;
            swipe.Dragging += OnDrag;

            ShowScreen(AppScreen.Splash);
        }

        void AddScreen<T>(AppScreen id) where T : UIScreen
        {
            var screen = new GameObject(id.ToString()).AddComponent<T>();
            screen.transform.SetParent(transform, false);
            screen.Init(this);
            screen.gameObject.SetActive(false);
            screens[id] = screen;
        }

        void BuildGameplay()
        {
            gameplayRoot = new GameObject("Gameplay");
            gameplayRoot.transform.SetParent(transform, false);

            lightBoard = new GameObject("LightBoard").AddComponent<BoardRenderer>();
            lightBoard.transform.SetParent(gameplayRoot.transform, false);
            lightBoard.transform.localPosition = new Vector3(0f, LightBoardY, 0f);
            shadowBoard = new GameObject("ShadowBoard").AddComponent<BoardRenderer>();
            shadowBoard.transform.SetParent(gameplayRoot.transform, false);
            shadowBoard.transform.localPosition = new Vector3(0f, ShadowBoardY, 0f);
            arrows = TutorialHint.Create(gameplayRoot.transform, lightBoard.transform.localPosition, shadowBoard.transform.localPosition);

            ui = new GameObject("GameplayUI").AddComponent<GameplayUI>();
            ui.transform.SetParent(gameplayRoot.transform, false);
            ui.Build();
            ui.BackPressed = () => ShowScreen(playingDaily ? AppScreen.Daily : AppScreen.LevelSelect);
            ui.PausePressed = Pause;
            ui.ResumePressed = Resume;
            ui.UndoPressed = UndoMove;
            ui.HintPressed = RequestHint;
            ui.HintConfirmed = ConfirmHint;
            ui.RestartPressed = RestartLevel;
            ui.LevelsPressed = () => ShowScreen(playingDaily ? AppScreen.Daily : AppScreen.LevelSelect);
            ui.MenuPressed = () => ShowScreen(AppScreen.MainMenu);
            ui.NextPressed = NextLevel;
            ui.MusicVolumeChanged = v => { SettingsData.MusicVolume = v; SettingsData.Music = v > 0.01f; Audio.ApplySettings(); };
            ui.SoundVolumeChanged = v => { SettingsData.SoundVolume = v; SettingsData.Sound = v > 0.01f; Audio.ApplySettings(); };

            movement = gameObject.AddComponent<MovementManager>();
            movement.MoveFinished += OnMoveFinished;
        }

        void OnDestroy()
        {
            if (Instance == this) Instance = null;
        }

        // ---------------- Camera ----------------

        void SetupCamera()
        {
            cam = Camera.main;
            if (cam == null)
            {
                var go = new GameObject("Main Camera");
                go.tag = "MainCamera";
                cam = go.AddComponent<Camera>();
            }
            cam.orthographic = true;
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = NeonTheme.Background;
            cam.transform.rotation = Quaternion.identity;
            FitCamera();
        }

        /// <summary>Fits the 9 x 17 layout inside the screen's safe area (notches, gesture bars) on any aspect ratio.</summary>
        void FitCamera()
        {
            lastScreen = new Vector2Int(Screen.width, Screen.height);
            lastSafeArea = Screen.safeArea;
            float sw = Mathf.Max(1f, Screen.width), sh = Mathf.Max(1f, Screen.height);
            Rect safe = lastSafeArea.width > 0 && lastSafeArea.height > 0 ? lastSafeArea : new Rect(0, 0, sw, sh);
            const float halfWidth = 4.75f, halfHeight = 8.6f;
            float safeAspect = safe.width / safe.height;
            float sizeInSafe = Mathf.Max(halfHeight, halfWidth / safeAspect);
            cam.orthographicSize = sizeInSafe * sh / safe.height;
            float unitsPerPixel = 2f * cam.orthographicSize / sh;
            float offsetY = (sh * 0.5f - safe.center.y) * unitsPerPixel;
            float offsetX = (sw * 0.5f - safe.center.x) * unitsPerPixel;
            cam.transform.position = new Vector3(offsetX, offsetY, -10f);
        }

        // ---------------- Frame loop ----------------

        void Update()
        {
            if (Screen.width != lastScreen.x || Screen.height != lastScreen.y || Screen.safeArea != lastSafeArea) FitCamera();

            if (fadeAlpha > 0f)
            {
                fadeAlpha = Mathf.MoveTowards(fadeAlpha, 0f, Time.unscaledDeltaTime * 4f);
                fader.color = NeonTheme.WithAlpha(NeonTheme.Background, fadeAlpha);
                fader.gameObject.SetActive(fadeAlpha > 0f);
            }

            UIScreen active;
            if (screens.TryGetValue(CurrentScreen, out active)) active.Tick();

            if (KeyDown(KeyCode.Escape)) Back();
            if (CurrentScreen != AppScreen.Gameplay) return;
            // Keyboard shortcuts: Z / Backspace = undo, R = restart, H = hint. N = skip level (Editor only).
            if (KeyDown(KeyCode.Z) || KeyDown(KeyCode.Backspace)) UndoMove();
            if (KeyDown(KeyCode.R)) RestartLevel();
            if (KeyDown(KeyCode.H)) RequestHint();
#if UNITY_EDITOR
            if (KeyDown(KeyCode.N) && !playingDaily) { Levels.MarkCurrentCompleted(); NextLevel(); }
#endif
        }

        static bool KeyDown(KeyCode key)
        {
#if ENABLE_INPUT_SYSTEM
            var kb = UnityEngine.InputSystem.Keyboard.current;
            if (kb == null) return false;
            switch (key)
            {
                case KeyCode.Z: return kb.zKey.wasPressedThisFrame;
                case KeyCode.Backspace: return kb.backspaceKey.wasPressedThisFrame;
                case KeyCode.R: return kb.rKey.wasPressedThisFrame;
                case KeyCode.H: return kb.hKey.wasPressedThisFrame;
                case KeyCode.N: return kb.nKey.wasPressedThisFrame;
                case KeyCode.Escape: return kb.escapeKey.wasPressedThisFrame;
                default: return false;
            }
#elif ENABLE_LEGACY_INPUT_MANAGER
            return Input.GetKeyDown(key);
#else
            return false;
#endif
        }

        // ---------------- Navigation ----------------

        void Back()
        {
            switch (CurrentScreen)
            {
                case AppScreen.Splash:
                case AppScreen.MainMenu: break;
                case AppScreen.LevelSelect: ShowScreen(AppScreen.WorldSelect); break;
                case AppScreen.Gameplay:
                    if (State == GameState.Paused) Resume();
                    else if (State == GameState.Playing) Pause();
                    break;
                default: ShowScreen(AppScreen.MainMenu); break;
            }
        }

        public void ShowScreen(AppScreen screen)
        {
            StopAllCoroutines();
            if (puzzle != null) movement.Bind(puzzle, lightBoard, shadowBoard, lightToken, shadowToken); // cancel animations
            CurrentScreen = screen;
            foreach (var pair in screens) pair.Value.gameObject.SetActive(pair.Key == screen);
            gameplayRoot.SetActive(screen == AppScreen.Gameplay);
            UIScreen s;
            if (screens.TryGetValue(screen, out s)) s.OnShow();
            if (screen != AppScreen.Gameplay) State = GameState.Menu;
            if (screen != AppScreen.Splash) { fadeAlpha = 0.6f; fader.gameObject.SetActive(true); }
        }

        public void OpenWorld(int worldId)
        {
            if (!Levels.IsWorldUnlocked(worldId)) return;
            ((LevelMapScreen)screens[AppScreen.LevelSelect]).SetWorld(worldId);
            ShowScreen(AppScreen.LevelSelect);
        }

        /// <summary>PLAY on the main menu: continue the current level.</summary>
        public void Continue()
        {
            if (Levels.Count == 0) return;
            StartLevel(Levels.CurrentIndex);
        }

        public void StartLevel(int index)
        {
            if (!Levels.IsUnlocked(index)) return;
            Levels.Select(index);
            playingDaily = false;
            ShowScreen(AppScreen.Gameplay);
            LoadPuzzle(Levels.Current);
        }

        public void StartDaily()
        {
            playingDaily = true;
            ShowScreen(AppScreen.Gameplay);
            LoadPuzzle(DailyLevel);
        }

        // ---------------- Gameplay ----------------

        void LoadPuzzle(LevelData level)
        {
            puzzle = new PuzzleState(level);
            usedHintThisLevel = false;

            lightBoard.Build(puzzle.LightBoard, NeonTheme.Light);
            shadowBoard.Build(puzzle.ShadowBoard, NeonTheme.Shadow);

            // Tokens are rebuilt with the boards (Build destroys old children).
            lightToken = PlayerController.Create("Light", lightBoard.transform, NeonTheme.Light);
            shadowToken = PlayerController.Create("Shadow", shadowBoard.transform, NeonTheme.Shadow);

            movement.Bind(puzzle, lightBoard, shadowBoard, lightToken, shadowToken);
            ui.ShowLevel(level, playingDaily);
            RefreshHud();
            arrows.Hide();
            if (!playingDaily && !SettingsData.TutorialDone && Levels.CurrentIndex == 0)
            {
                List<Direction> path = PuzzleSolver.Solve(level);
                if (path != null && path.Count > 0) arrows.Show(path[0]);
            }
            State = GameState.Playing;
        }

        void RefreshHud() { ui.SetMoves(puzzle); }

        void OnSwipe(Direction direction)
        {
            if (CurrentScreen != AppScreen.Gameplay || State != GameState.Playing || movement.IsBusy) return;
            State = GameState.Animating;
            movement.RequestMove(direction);
            if (!movement.IsBusy) State = GameState.Playing; // move ignored
        }

        void OnMoveFinished(MoveOutcome outcome)
        {
            RefreshHud();
            if (outcome.result == MoveResult.Moved)
            {
                Audio.Play(Sfx.Move);
                arrows.Hide();
                if (!SettingsData.TutorialDone) SettingsData.TutorialDone = true;
            }
            else
            {
                Audio.Play(Sfx.Blocked);
                Haptics.Pulse(25);
            }

            if (outcome.solvedNow) StartCoroutine(Win());
            else if (outcome.failedNow)
            {
                State = GameState.Failed;
                Audio.Play(Sfx.Fail);
                Haptics.Pulse(60);
                ui.ShowFailure(puzzle.MoveCount, puzzle.Level.starThresholds.threeStarsMaxMoves);
            }
            else State = GameState.Playing;
        }

        IEnumerator Win()
        {
            State = GameState.Victory;
            Audio.Play(Sfx.Win);
            Haptics.Pulse(50);
            StartCoroutine(lightToken.Celebrate(0.45f));
            yield return StartCoroutine(shadowToken.Celebrate(0.45f));

            LevelData level = puzzle.Level;
            int stars = StarManager.Calculate(level, puzzle.MoveCount);
            int coins;
            if (playingDaily)
            {
                coins = SaveManager.CompleteDaily(Today);
            }
            else
            {
                int previous = SaveManager.GetStars(level);
                coins = Rewards.LevelCoins(previous, stars);
                SaveManager.RecordStars(level, stars);
                if (!usedHintThisLevel) SaveManager.MarkNoHint(level);
                SaveManager.AddCoins(coins);
                Levels.MarkCurrentCompleted();
            }
            ui.ShowVictory(puzzle.MoveCount, stars, level.starThresholds.threeStarsMaxMoves, coins, !playingDaily && Levels.IsLastLevel, playingDaily);
        }

        /// <summary>Steps back one move. Also works from the failure panel.</summary>
        public void UndoMove()
        {
            if (puzzle == null || movement.IsBusy) return;
            if (State != GameState.Playing && State != GameState.Failed) return;
            if (!puzzle.Undo()) return;
            Audio.Play(Sfx.Undo);
            ui.HidePanels();
            movement.SyncTokens();
            RefreshHud();
            State = GameState.Playing;
        }

        Direction pendingHint;

        void RequestHint()
        {
            if (puzzle == null || State != GameState.Playing || movement.IsBusy) return;
            List<Direction> path = PuzzleSolver.Solve(puzzle.Level, puzzle.LightPos, puzzle.ShadowPos);
            if (path == null || path.Count == 0) { ui.ShowMessage("No route from here. Try Undo or Restart."); return; }
            if (puzzle.HasMoveLimit && path.Count > puzzle.MovesLeft) { ui.ShowMessage("Too few moves left. Try Undo or Restart."); return; }
            if (SaveManager.Hints <= 0 && SaveManager.Coins < Rewards.HintCost)
            {
                ui.ShowMessage("No hints left. Earn " + Rewards.HintCost + " coins for one more.");
                return;
            }
            if (!SaveManager.TrySpendHint()) return;
            usedHintThisLevel = true;
            pendingHint = path[0];
            string cost = SaveManager.Hints > 0 ? "(" + SaveManager.Hints + " free hints left)" : "(Next hint costs " + Rewards.HintCost + " coins)";
            State = GameState.Paused;
            ui.ShowHint(pendingHint, cost);
            RefreshHud();
        }

        void ConfirmHint()
        {
            ui.HidePanels();
            arrows.Show(pendingHint);
            State = GameState.Playing;
        }

        public void Pause()
        {
            if (CurrentScreen != AppScreen.Gameplay || State != GameState.Playing || movement.IsBusy) return;
            State = GameState.Paused;
            ui.ShowPause();
        }

        public void Resume()
        {
            if (State != GameState.Paused) return;
            ui.HidePanels();
            State = GameState.Playing;
        }

        void OnTap(Vector2 screenPos)
        {
            if (State == GameState.Loading || movement.IsBusy) return;
            Vector3 world = ScreenToWorld(screenPos);
            UIScreen s;
            if (CurrentScreen == AppScreen.Gameplay) ui.HandleTap(world);
            else if (screens.TryGetValue(CurrentScreen, out s)) s.HandleTap(world);
        }

        void OnDrag(Vector2 screenPos)
        {
            Vector3 world = ScreenToWorld(screenPos);
            UIScreen s;
            if (CurrentScreen == AppScreen.Gameplay) ui.HandleDrag(world);
            else if (screens.TryGetValue(CurrentScreen, out s)) s.HandleDrag(world);
        }

        Vector3 ScreenToWorld(Vector2 screenPos)
        {
            Vector3 world = cam.ScreenToWorldPoint(new Vector3(screenPos.x, screenPos.y, -cam.transform.position.z));
            world.z = 0f;
            return world;
        }

        public void NextLevel()
        {
            StopAllCoroutines();
            if (playingDaily) { ShowScreen(AppScreen.MainMenu); return; }
            if (Levels.IsLastLevel) { ShowScreen(AppScreen.WorldSelect); return; }
            Levels.Advance();
            LoadPuzzle(Levels.Current);
        }

        public void RestartLevel()
        {
            if (CurrentScreen != AppScreen.Gameplay || puzzle == null) return;
            StopAllCoroutines();
            LoadPuzzle(puzzle.Level);
        }
    }
}
