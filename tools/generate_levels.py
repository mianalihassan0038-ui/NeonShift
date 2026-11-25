"""Random level search for NEON SHIFT (design helper, not shipped in the game).

Finds 5x5 levels whose shortest solution has a target length and requires
detours (shortest path longer than the straight-line distance).
Usage: python3 tools/generate_levels.py <seed> <target_moves> [count] [min_density] [max_density] [--climb]
--climb uses hill climbing, which is much faster for long solutions.
"""
import json, random, sys
from collections import deque

W = H = 5
DIRS = {"U": (0, -1), "D": (0, 1), "L": (-1, 0), "R": (1, 0)}

def ok(p, walls):
    return 0 <= p[0] < W and 0 <= p[1] < H and p not in walls

def solve(ls, le, lw, ss, se, sw):
    start = (ls, ss)
    prev = {start: None}
    q = deque([start])
    while q:
        st = q.popleft()
        if st == (le, se):
            path = []
            while prev[st]:
                st, d = prev[st]
                path.append(d)
            return path[::-1]
        (l, s) = st
        for d, (dx, dy) in DIRS.items():
            nl, ns = (l[0] + dx, l[1] + dy), (s[0] - dx, s[1] - dy)
            if ok(nl, lw) and ok(ns, sw) and (nl, ns) not in prev:
                prev[(nl, ns)] = (st, d)
                q.append((nl, ns))
    return None

def rand_cell():
    return (random.randrange(W), random.randrange(H))

def attempt(target, dmin=0.2, dmax=0.4):
    ls, ss = rand_cell(), rand_cell()
    dx, dy = random.randint(-4, 4), random.randint(-4, 4)
    le, se = (ls[0] + dx, ls[1] + dy), (ss[0] - dx, ss[1] - dy)
    if not (ok(le, set()) and ok(se, set())) or (dx, dy) == (0, 0):
        return None
    density = random.uniform(dmin, dmax)
    lw = {c for c in ((x, y) for x in range(W) for y in range(H)) if random.random() < density} - {ls, le}
    sw = {c for c in ((x, y) for x in range(W) for y in range(H)) if random.random() < density} - {ss, se}
    path = solve(ls, le, lw, ss, se, sw)
    if not path or len(path) != target or len(path) <= abs(dx) + abs(dy):
        return None
    return dict(ls=ls, le=le, lw=sorted(lw), ss=ss, se=se, sw=sorted(sw), path="".join(path))

def main():
    random.seed(int(sys.argv[1]))
    target = int(sys.argv[2])
    args = [a for a in sys.argv if a != "--climb"]
    count = int(args[3]) if len(args) > 3 else 3
    dmin = float(args[4]) if len(args) > 4 else 0.2
    dmax = float(args[5]) if len(args) > 5 else 0.4
    found = 0
    while found < count:
        r = climb(target, dmin, dmax) if "--climb" in sys.argv else attempt(target, dmin, dmax)
        if r:
            found += 1
            print(json.dumps(r))
            for y in range(H):
                row_l = "".join("L" if (x, y) == r["ls"] else "E" if (x, y) == r["le"] else "#" if (x, y) in r["lw"] else "." for x in range(W))
                row_s = "".join("S" if (x, y) == r["ss"] else "E" if (x, y) == r["se"] else "#" if (x, y) in r["sw"] else "." for x in range(W))
                print("   ", row_l, "   ", row_s)



def climb(target, dmin=0.2, dmax=0.4, steps=4000):
    """Hill-climb: start from any solvable level, toggle walls to lengthen the shortest solution."""
    while True:
        ls, ss = rand_cell(), rand_cell()
        dx, dy = random.randint(-4, 4), random.randint(-4, 4)
        le, se = (ls[0] + dx, ls[1] + dy), (ss[0] - dx, ss[1] - dy)
        if ok(le, set()) and ok(se, set()) and (dx, dy) != (0, 0):
            break
    cells = [(x, y) for x in range(W) for y in range(H)]
    lw, sw = set(), set()
    best = solve(ls, le, lw, ss, se, sw)
    for _ in range(steps):
        board = random.choice((lw, sw))
        fixed = {ls, le} if board is lw else {ss, se}
        c = random.choice(cells)
        if c in fixed:
            continue
        board.symmetric_difference_update({c})
        density = (len(lw) + len(sw)) / (2 * W * H)
        path = solve(ls, le, lw, ss, se, sw)
        if path and len(path) >= len(best) and len(path) <= target and density <= dmax:
            best = path
        else:
            board.symmetric_difference_update({c})
        if len(best) == target and (len(lw) + len(sw)) / (2 * W * H) >= dmin:
            break
    if len(best) != target or len(best) <= abs(dx) + abs(dy):
        return None
    return dict(ls=ls, le=le, lw=sorted(lw), ss=ss, se=se, sw=sorted(sw), path="".join(best))


if __name__ == "__main__":
    main()
