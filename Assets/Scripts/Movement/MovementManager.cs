using System;
using System.Collections;
using UnityEngine;

namespace NeonShift
{
    /// <summary>
    /// Applies paired moves from PuzzleState to the two tokens and animates them.
    /// Input is locked while an animation runs, so fast swipes never queue extra moves.
    /// </summary>
    public sealed class MovementManager : MonoBehaviour
    {
        public const float MoveDuration = 0.13f;
        public const float BumpDuration = 0.16f;

        PuzzleState puzzle;
        BoardRenderer lightBoard, shadowBoard;
        PlayerController lightToken, shadowToken;

        public bool IsBusy { get; private set; }
        public event Action<MoveOutcome> MoveFinished;

        public void Bind(PuzzleState state, BoardRenderer lightRenderer, BoardRenderer shadowRenderer,
                         PlayerController light, PlayerController shadow)
        {
            StopAllCoroutines();
            IsBusy = false;
            puzzle = state;
            lightBoard = lightRenderer;
            shadowBoard = shadowRenderer;
            lightToken = light;
            shadowToken = shadow;
            lightToken.SnapTo(lightBoard.CellToLocal(puzzle.LightPos));
            shadowToken.SnapTo(shadowBoard.CellToLocal(puzzle.ShadowPos));
        }

        public void RequestMove(Direction swipe)
        {
            if (IsBusy || puzzle == null) return;
            MoveOutcome outcome = puzzle.TryMove(swipe);
            if (outcome.result == MoveResult.AlreadySolved) return;
            StartCoroutine(Animate(outcome, swipe));
        }

        IEnumerator Animate(MoveOutcome outcome, Direction swipe)
        {
            IsBusy = true;
            if (outcome.result == MoveResult.Moved)
            {
                StartCoroutine(lightToken.MoveTo(lightBoard.CellToLocal(outcome.lightTo), MoveDuration));
                yield return StartCoroutine(shadowToken.MoveTo(shadowBoard.CellToLocal(outcome.shadowTo), MoveDuration));
            }
            else
            {
                // Show which character was blocked. Neither one actually changes cell.
                if (outcome.lightBlocked) StartCoroutine(lightToken.Bump(swipe, BumpDuration));
                if (outcome.shadowBlocked) StartCoroutine(shadowToken.Bump(swipe.Opposite(), BumpDuration));
                yield return new WaitForSeconds(BumpDuration);
            }
            IsBusy = false;
            if (MoveFinished != null) MoveFinished(outcome);
        }
    }
}
