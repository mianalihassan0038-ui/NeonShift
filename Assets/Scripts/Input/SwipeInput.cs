using System;
using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// Turns touch / mouse drags into one swipe direction, and short presses into taps.
    /// One gesture = at most one swipe. A second finger cancels the gesture (no multitouch moves).
    /// Arrow keys / WASD also swipe, for testing in the Editor.
    /// Works with either the new Input System or the old Input Manager.
    /// </summary>
    public sealed class SwipeInput : MonoBehaviour
    {
        public event Action<Direction> Swiped;
        public event Action<Vector2> Tapped;
        /// <summary>Pointer position every frame while pressed (used by sliders).</summary>
        public event Action<Vector2> Dragging;

        bool tracking;
        bool consumed;      // swipe already fired for this gesture
        bool cancelled;     // multitouch detected: ignore until all fingers lift
        Vector2 startPos;

        float Threshold { get { return Mathf.Max(30f, Mathf.Min(Screen.width, Screen.height) * 0.06f); } }

        void Awake()
        {
#if !ENABLE_INPUT_SYSTEM && ENABLE_LEGACY_INPUT_MANAGER
            Input.simulateMouseWithTouches = false;
            Input.multiTouchEnabled = true; // needed to notice a second finger and cancel
#endif
        }

        void Update()
        {
            ReadKeyboard();
            ReadPointer();
        }

        void Begin(Vector2 pos)
        {
            tracking = true;
            consumed = false;
            startPos = pos;
        }

        void Drag(Vector2 pos)
        {
            if (tracking && !cancelled && Dragging != null) Dragging(pos);
            if (!tracking || consumed || cancelled) return;
            Vector2 delta = pos - startPos;
            if (delta.magnitude < Threshold) return;
            consumed = true;
            Fire(Mathf.Abs(delta.x) > Mathf.Abs(delta.y)
                ? (delta.x > 0 ? Direction.Right : Direction.Left)
                : (delta.y > 0 ? Direction.Up : Direction.Down)); // screen y grows upwards
        }

        void End(Vector2 pos)
        {
            if (tracking && !consumed && !cancelled)
            {
                Drag(pos);
                if (!consumed && Tapped != null) Tapped(pos);
            }
            tracking = false;
        }

        void Fire(Direction direction)
        {
            if (Swiped != null) Swiped(direction);
        }

#if ENABLE_INPUT_SYSTEM
        void ReadKeyboard()
        {
            var kb = UnityEngine.InputSystem.Keyboard.current;
            if (kb == null) return;
            if (kb.upArrowKey.wasPressedThisFrame || kb.wKey.wasPressedThisFrame) Fire(Direction.Up);
            else if (kb.downArrowKey.wasPressedThisFrame || kb.sKey.wasPressedThisFrame) Fire(Direction.Down);
            else if (kb.leftArrowKey.wasPressedThisFrame || kb.aKey.wasPressedThisFrame) Fire(Direction.Left);
            else if (kb.rightArrowKey.wasPressedThisFrame || kb.dKey.wasPressedThisFrame) Fire(Direction.Right);
        }

        void ReadPointer()
        {
            var ts = UnityEngine.InputSystem.Touchscreen.current;
            if (ts != null)
            {
                int down = 0;
                foreach (var t in ts.touches)
                    if (t.press.isPressed) down++;

                if (down > 1) { cancelled = true; tracking = false; return; }
                var primary = ts.primaryTouch;
                Vector2 pos = primary.position.ReadValue();
                if (down == 0)
                {
                    if (tracking) End(pos);
                    cancelled = false;
                    return;
                }
                if (cancelled) return;
                if (!tracking) Begin(pos); else Drag(pos);
                return;
            }

            var mouse = UnityEngine.InputSystem.Mouse.current;
            if (mouse == null) return;
            Vector2 mp = mouse.position.ReadValue();
            if (mouse.leftButton.wasPressedThisFrame) Begin(mp);
            else if (mouse.leftButton.wasReleasedThisFrame) End(mp);
            else if (mouse.leftButton.isPressed) Drag(mp);
        }
#elif ENABLE_LEGACY_INPUT_MANAGER
        void ReadKeyboard()
        {
            if (Input.GetKeyDown(KeyCode.UpArrow) || Input.GetKeyDown(KeyCode.W)) Fire(Direction.Up);
            else if (Input.GetKeyDown(KeyCode.DownArrow) || Input.GetKeyDown(KeyCode.S)) Fire(Direction.Down);
            else if (Input.GetKeyDown(KeyCode.LeftArrow) || Input.GetKeyDown(KeyCode.A)) Fire(Direction.Left);
            else if (Input.GetKeyDown(KeyCode.RightArrow) || Input.GetKeyDown(KeyCode.D)) Fire(Direction.Right);
        }

        void ReadPointer()
        {
            if (Input.touchSupported && Input.touchCount > 0)
            {
                if (Input.touchCount > 1) { cancelled = true; tracking = false; return; }
                Touch t = Input.GetTouch(0);
                if (cancelled)
                {
                    if (t.phase == TouchPhase.Ended || t.phase == TouchPhase.Canceled) cancelled = false;
                    return;
                }
                switch (t.phase)
                {
                    case TouchPhase.Began: Begin(t.position); break;
                    case TouchPhase.Moved:
                    case TouchPhase.Stationary: Drag(t.position); break;
                    case TouchPhase.Ended: End(t.position); break;
                    case TouchPhase.Canceled: tracking = false; break;
                }
                return;
            }
            cancelled = false;

            Vector2 mp = Input.mousePosition;
            if (Input.GetMouseButtonDown(0)) Begin(mp);
            else if (Input.GetMouseButtonUp(0)) End(mp);
            else if (Input.GetMouseButton(0)) Drag(mp);
        }
#else
        void ReadKeyboard() { }
        void ReadPointer() { }
#endif
    }
}
