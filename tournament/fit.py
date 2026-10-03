"""Logistic Elo MLE over tournament outcomes (pure, stdlib only).

Bots don't drift, so there is no time dimension: each game is one row
(black_rung, white_rung, black_score). Scale is the app's ELO_SCALE=400.
Rung 0 pins at 0.0 (relative gaps); the 20k=800 absolute pin applies when
writing the app table, never here.
"""

import math

from tournament.schedule import RUNGS

SCALE = 400.0
_L = math.log(10.0) / SCALE


def check_row(row, n_rungs=RUNGS):
    """Validate one games.jsonl row; raises ValueError on garbage."""
    try:
        b, w, s = row["black"], row["white"], row["score"]
    except (KeyError, TypeError):
        raise ValueError("row needs black/white/score: %r" % (row,))
    if not (isinstance(b, int) and isinstance(w, int)):
        raise ValueError("rungs must be ints: %r" % (row,))
    if not (0 <= b < n_rungs and 0 <= w < n_rungs):
        raise ValueError("rung outside ladder: %r" % (row,))
    if b == w:
        raise ValueError("self-play is not a measurement: %r" % (row,))
    if s not in (0.0, 0.5, 1.0):
        raise ValueError("score must be 0/0.5/1: %r" % (row,))
    return True


def _p_black(xb, xw):
    d = xb - xw
    if d >= 0:
        return 1.0 / (1.0 + math.exp(-d * _L))
    e = math.exp(d * _L)
    return e / (1.0 + e)


def fit_rungs(rows, n_rungs=RUNGS):
    """Newton MLE. Returns (xs with xs[0]=0.0, standard errors)."""
    for r in rows:
        check_row(r, n_rungs)
    m = n_rungs - 1  # free params x[1..]; x[0] pinned
    xs = [0.0] * m
    for _ in range(100):
        g = [0.0] * m
        h = [[0.0] * m for _ in range(m)]
        for r in rows:
            full = [0.0] + xs
            p = _p_black(full[r["black"]], full[r["white"]])
            s = r["score"]
            wgt = p * (1.0 - p) * _L * _L
            grad = (p - s) * _L
            involved = [i for i in (r["black"], r["white"]) if i > 0]
            sgn = {r["black"]: 1.0, r["white"]: -1.0}
            for i in involved:
                g[i - 1] += sgn[i] * grad
            for a in involved:
                for b in involved:
                    h[a - 1][b - 1] += sgn[a] * sgn[b] * wgt
        step = _solve(h, [-v for v in g])
        xs = [x + dx for x, dx in zip(xs, step)]
        if max(abs(v) for v in step) < 1e-9:
            break
    cov = _invert(h)
    ses = [0.0] + [math.sqrt(max(cov[i][i], 0.0)) for i in range(m)]
    return ([0.0] + xs, ses)


def p_inverted(xs, ses, a, b):
    """Posterior P(rung a is actually weaker than rung b), a < b.

    Normal approximation on the fitted values; drives wave-two targeting.
    """
    gap = xs[b] - xs[a]
    sd = math.hypot(ses[a], ses[b])
    if sd == 0:
        return 0.0 if gap > 0 else 1.0
    return 0.5 * math.erfc(gap / (sd * math.sqrt(2.0)))


def triangle_violations(rows, n_rungs=RUNGS, z=3.0):
    """Triples where the direct gap disagrees with the chained fit.

    Compares each distant pairing's empirical logit gap against the fitted
    gap; returns [(a, c, emp, fit, se)] beyond z standard errors. Empty on
    consistent (transitive, logistic) data.
    """
    for r in rows:
        check_row(r, n_rungs)
    xs, _ = fit_rungs(rows, n_rungs)
    by_pair = {}
    for r in rows:
        key = (min(r["black"], r["white"]), max(r["black"], r["white"]))
        by_pair.setdefault(key, []).append(r)
    bad = []
    for (a, c), rs in sorted(by_pair.items()):
        if c - a < 2:
            continue
        n = len(rs)
        wins = sum(
            r["score"] if r["black"] == c else 1.0 - r["score"] for r in rs
        )
        p = min(max(wins / n, 1e-6), 1.0 - 1e-6)
        emp = math.log10(p / (1.0 - p)) * SCALE
        se = SCALE / (math.log(10) * math.sqrt(n * p * (1.0 - p)))
        gap = xs[c] - xs[a]
        if abs(emp - gap) > z * se:
            bad.append((a, c, emp, gap, se))
    return bad


def _solve(a, b):
    """Gaussian elimination with partial pivot (small, dense)."""
    n = len(b)
    m = [row[:] + [v] for row, v in zip(a, b)]
    for col in range(n):
        piv = max(range(col, n), key=lambda r: abs(m[r][col]))
        m[col], m[piv] = m[piv], m[col]
        if abs(m[col][col]) < 1e-12:
            m[col][col] = 1e-12
        for r in range(col + 1, n):
            f = m[r][col] / m[col][col]
            for c in range(col, n + 1):
                m[r][c] -= f * m[col][c]
    x = [0.0] * n
    for r in range(n - 1, -1, -1):
        x[r] = (m[r][n] - sum(m[r][c] * x[c] for c in range(r + 1, n))) / m[r][r]
    return x


def _invert(a):
    """Dense inverse via Gauss-Jordan (n <= 28, runs once per fit)."""
    n = len(a)
    m = [row[:] + [1.0 if i == j else 0.0 for j in range(n)] for i, row in enumerate(a)]
    for col in range(n):
        piv = max(range(col, n), key=lambda r: abs(m[r][col]))
        m[col], m[piv] = m[piv], m[col]
        if abs(m[col][col]) < 1e-12:
            m[col][col] = 1e-12
        div = m[col][col]
        m[col] = [v / div for v in m[col]]
        for r in range(n):
            if r != col and m[r][col] != 0.0:
                f = m[r][col]
                m[r] = [rv - f * cv for rv, cv in zip(m[r], m[col])]
    return [row[n:] for row in m]
