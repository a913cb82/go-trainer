"""S-shape and regime models, Nelder-Mead fitted (pure, stdlib only)."""

import math


def logistic(lo, hi, k, x0):
    """Symmetric S from lo to hi, steepest at x0, rate k."""
    span = hi - lo
    return lambda x: lo + span / (1.0 + math.exp(-k * (x - x0)))


def gompertz(lo, hi, b, c):
    """Asymmetric S: fast rise early, slow approach to hi."""
    span = hi - lo
    return lambda x: lo + span * math.exp(-b * math.exp(-c * x))


def broken_stick(b0, s1, s2, c):
    """Two linear regimes, continuous at joint c."""
    return lambda x: b0 + s1 * x + (s2 - s1) * (x - c if x > c else 0.0)


def nelder_mead(f, start, step=1.0, iters=2000, tol=1e-9):
    """Minimize f(list) -> (best_point, best_value). Compact NM simplex."""
    n = len(start)
    simplex = [list(start)]
    for i in range(n):
        p = list(start)
        p[i] += step if start[i] == 0 else abs(start[i]) * 0.5 + 0.5
        simplex.append(p)
    vals = [f(p) for p in simplex]
    for _ in range(iters):
        order = sorted(range(n + 1), key=lambda i: vals[i])
        simplex = [simplex[i] for i in order]
        vals = [vals[i] for i in order]
        if max(abs(v - vals[0]) for v in vals) < tol:
            break
        centroid = [sum(p[d] for p in simplex[:-1]) / n for d in range(n)]
        worst = simplex[-1]

        def at(a):
            return [centroid[d] + a * (centroid[d] - worst[d])
                    for d in range(n)]

        refl = at(1.0)
        vr = f(refl)
        if vr < vals[0]:
            exp = at(2.0)
            ve = f(exp)
            simplex[-1], vals[-1] = (exp, ve) if ve < vr else (refl, vr)
        elif vr < vals[-2]:
            simplex[-1], vals[-1] = refl, vr
        else:
            if vr < vals[-1]:
                simplex[-1], vals[-1] = refl, vr
            cont = at(0.5)
            vc = f(cont)
            if vc < vals[-1]:
                simplex[-1], vals[-1] = cont, vc
            else:
                simplex = [simplex[0]] + [
                    [simplex[0][d] + 0.5 * (p[d] - simplex[0][d])
                     for d in range(n)] for p in simplex[1:]]
                vals = [f(p) for p in simplex]
    return simplex[0], vals[0]


def _nm_predictor(build, starts):
    """In-fold fit: best of several starts (nonlinear needs them)."""
    def fit_predictor(xs, ys):
        def err(v):
            pred = build(*v)
            return sum((pred(x) - y) ** 2 for x, y in zip(xs, ys))
        best, be = None, None
        for s in starts:
            try:
                v, e = nelder_mead(err, list(s))
            except (ValueError, OverflowError):
                continue
            if be is None or e < be:
                best, be = v, e
        if best is None:
            m = sum(ys) / len(ys)
            return lambda x: m
        return build(*best)
    return fit_predictor


sigmoid_fits = _nm_predictor(
    lambda lo, hi, k, x0: logistic(lo, hi, k, x0),
    [(0, 500, 0.2, 10), (0, 1000, 0.1, 15), (-500, 1500, 0.3, 8)])
gompertz_fits = _nm_predictor(
    lambda lo, hi, b, c: gompertz(lo, hi, b, c),
    [(0, 500, 5, 0.2), (0, 1000, 5, 0.1), (-500, 1500, 5, 0.3)])


def broken_stick_fits(xs, ys):
    """Joint c is discrete (rung gaps): try each, OLS the rest, keep best."""
    from tournament import model_select as M

    n = len(xs)
    order = sorted(range(n), key=lambda i: xs[i])
    sx = [xs[i] for i in order]
    sy = [ys[i] for i in order]
    best, be = None, None
    for j in range(2, n - 1):
        c = (sx[j - 1] + sx[j]) / 2.0
        design = [[1.0, x, (x - c if x > c else 0.0)] for x in sx]
        coefs = M.ols(design, sy)
        err = sum((coefs[0] + coefs[1] * x + coefs[2] *
                       (x - c if x > c else 0.0) - y) ** 2
                  for x, y in zip(sx, sy))
        if be is None or err < be:
            best, be = (coefs, c), err
    (coefs, c) = best
    return lambda x: (coefs[0] + coefs[1] * x + coefs[2] *
                      (x - c if x > c else 0.0))
