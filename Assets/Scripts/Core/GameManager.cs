using System.Collections;
using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// Entry point for Build 001. Creates the camera, both boards, both tokens and the UI in code,
    /// so it runs from any scene (including an empty one). Press Play and swipe / use arrow keys.
    /// </summary>
    public sealed class GameManager : MonoBehaviour
    {
        // Layout in world units inside a 9 x 16 portrait area centred on the origin.
        const float LightBoardY = 3.0f;
        const float ShadowBoardY = -3.55f;
        const float LabelGap = 3.3f; // label distance above a board centre

        public static GameManager Instance { get; private set; }

        public GameState State { get; private set; }

        Camera cam;
        Vector2Int lastScreen;
        LevelManager levels;
        PuzzleState puzzle;
        BoardRenderer lightBoard, shadowBoard;
        PlayerController lightToken, shadowToken;
        MovementManager movement;
        SwipeInput swipe;
        GameplayUI ui;

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

            lightBoard = new GameObject("LightBoard").AddComponent<BoardRenderer>();
            lightBoard.transform.SetParent(transform, false);
            lightBoard.transform.localPosition = new Vector3(0f, LightBoardY, 0f);
            shadowBoard = new GameObject("ShadowBoard").AddComponent<BoardRenderer>();
            shadowBoard.transform.SetParent(transform, false);
            shadowBoard.transform.localPosition = new Vector3(0f, ShadowBoardY, 0f);

            ui = new GameObject("GameplayUI").AddComponent<GameplayUI>();
            ui.transform.SetParent(transform, false);
            ui.Build(LightBoardY + LabelGap, ShadowBoardY + LabelGap);
            ui.NextPressed = NextLevel;
            ui.ReplayPressed = RestartLevel;
            ui.RestartPressed = RestartLevel;
            ui.UndoPressed = UndoMove;

            movement = gameObject.AddComponent<MovementManager>();
            movement.MoveFinished += OnMoveFinished;

            swipe = gameObject.AddComponent<SwipeInput>();
            swipe.Swiped += OnSwipe;
            swipe.Tapped += OnTap;

            levels = new LevelManager(LevelLoader.LoadAll());
            if (levels.Count == 0)
            {
                Debug.LogError("NEON SHIFT: no levels found in Assets/Resources/Levels");
                return;
            }
            LoadCurrentLevel();
        }

        void OnDestroy()
        {
            if (Instance == this) Instance = null;
        }

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
            cam.transform.position = new Vector3(0f, 0f, -10f);
            cam.transform.rotation = Quaternion.identity;
            FitCamera();
        }

        /// <summary>Keeps the whole 9 x 16 layout (plus margin) visible on any portrait aspect ratio.</summary>
        void FitCamera()
        {
            lastScreen = new Vector2Int(Screen.width, Screen.height);
            float aspect = Screen.height > 0 ? (float)Screen.width / Screen.height : 9f / 16f;
            const float halfWidth = 4.75f, halfHeight = 8.5f;
            cam.orthographicSize = Mathf.Max(halfHeight, halfWidth / aspect);
        }

        void Update()
        {
            if (Screen.width != lastScreen.x || Screen.height != lastScreen.y) FitCamera();

            // Keyboard shortcuts: Z / Backspace = undo, R = restart. N = skip level (Editor only).
            if (KeyDown(KeyCode.Z) || KeyDown(KeyCode.Backspace)) UndoMove();
            if (KeyDown(KeyCode.R)) RestartLevel();
#if UNITY_EDITOR
            if (KeyDown(KeyCode.N)) NextLevel();
#endif
            // Android back button / Escape also undoes.
            if (KeyDown(KeyCode.Escape)) UndoMove();
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

        void LoadCurrentLevel()
        {
            LevelData level = levels.Current;
            puzzle = new PuzzleState(level);

            lightBoard.Build(puzzle.LightBoard, NeonTheme.Light);
            shadowBoard.Build(puzzle.ShadowBoard, NeonTheme.Shadow);

            // Tokens are rebuilt with the boards (Build destroys old children).
            lightToken = PlayerController.Create("Light", lightBoard.transform, NeonTheme.Light);
            shadowToken = PlayerController.Create("Shadow", shadowBoard.transform, NeonTheme.Shadow);

            movement.Bind(puzzle, lightBoard, shadowBoard, lightToken, shadowToken);
            ui.ShowLevel(level);
            RefreshMoves();
            State = GameState.Playing;
        }

        void OnSwipe(Direction direction)
        {
            if (State != GameState.Playing || movement.IsBusy) return;
            State = GameState.Animating;
            movement.RequestMove(direction);
            if (!movement.IsBusy) State = GameState.Playing; // move ignored
        }

        void RefreshMoves()
        {
            ui.SetMoves(puzzle.MoveCount, puzzle.MaxMoves, puzzle.CanUndo);
        }

        void OnMoveFinished(MoveOutcome outcome)
        {
            RefreshMoves();
            if (outcome.solvedNow) StartCoroutine(Win());
            else if (outcome.failedNow) { State = GameState.Failed; ui.ShowFailure(); }
            else State = GameState.Playing;
        }

        IEnumerator Win()
        {
            State = GameState.Victory;
            StartCoroutine(lightToken.Celebrate(0.4f));
            yield return StartCoroutine(shadowToken.Celebrate(0.4f));
            int stars = StarManager.Calculate(puzzle.Level, puzzle.MoveCount);
            SaveManager.RecordStars(puzzle.Level, stars);
            ui.ShowVictory(puzzle.MoveCount, stars, puzzle.Level.starThresholds.threeStarsMaxMoves, levels.IsLastLevel);
        }

        /// <summary>Steps back one move. Also works from the failure panel.</summary>
        public void UndoMove()
        {
            if (puzzle == null || movement.IsBusy || State == GameState.Victory) return;
            if (!puzzle.Undo()) return;
            ui.HidePanels();
            movement.SyncTokens();
            RefreshMoves();
            State = GameState.Playing;
        }

        void OnTap(Vector2 screenPos)
        {
            if (State == GameState.Loading || movement.IsBusy) return;
            Vector3 world = cam.ScreenToWorldPoint(new Vector3(screenPos.x, screenPos.y, -cam.transform.position.z));
            world.z = 0f;
            ui.HandleTap(world);
        }

        public void NextLevel()
        {
            StopAllCoroutines();
            levels.Advance();
            LoadCurrentLevel();
        }

        public void RestartLevel()
        {
            StopAllCoroutines();
            LoadCurrentLevel();
        }
    }
}
