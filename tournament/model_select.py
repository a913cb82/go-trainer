"""Ladder-shape model selection by nested LOOCV (pure, stdlib only).

Every choice — including FP2's powers — is made inside each fold on the
28 training rungs, then predicts the held-out rung. Families compete on
mean squared held-out error. x must be positive (caller passes rung+1):
FP/log/sqrt need it, and the shift only reparameterizes polynomials.
"""

import math

from tournament import fit as _fit

POWERS = (-2, -1, -0.5, 0, 0.5, 1, 2, 3)


def _pow(x, p):
    return math.log(x) if p == 0 else x ** p


def fp_terms(x, powers):
    """Design-row terms for one FP power set (Royston–Altman forms)."""
    if len(powers) == 1:
        return [_pow(x, powers[0])]
    p1, p2 = powers
    if p1 == p2:
        return [_pow(x, p1), x ** p1 * math.log(x)]
    return [_pow(x, p1), _pow(x, p2)]


def ols(design, ys):
    """Least-squares coefficients via normal equations."""
    p = len(design[0])
    xtx = [[0.0] * p for _ in range(p)]
    xty = [0.0] * p
    for row, y in zip(design, ys):
        for a in range(p):
            xty[a] += row[a] * y
            for b in range(p):
                xtx[a][b] += row[a] * row[b]
    return _fit._solve(xtx, xty)


def _mse(coefs, design, ys):
    n = len(ys)
    return sum((sum(c * v for c, v in zip(coefs, row)) - y) ** 2
                 for row, y in zip(design, ys)) / n


def _ols_predictor(terms_fn):
    def fit_predictor(xs, ys):
        design = [[1.0] + terms_fn(x) for x in xs]
        coefs = ols(design, ys)
        return lambda x: coefs[0] + sum(
            c * v for c, v in zip(coefs[1:], terms_fn(x)))
    return fit_predictor


def _select_predictor(candidate_fns):
    """In-fold selection: best training MSE wins (fixed order breaks ties)."""
    def fit_predictor(xs, ys):
        best, best_err = None, None
        for fn in candidate_fns:
            pred = fn(xs, ys)
            err = sum((pred(x) - y) ** 2 for x, y in zip(xs, ys))
            if best_err is None or err < best_err:
                best, best_err = pred, err
        return best
    return fit_predictor


linear_fits = _ols_predictor(lambda x: [x])
quadratic_fits = _ols_predictor(lambda x: [x, x * x])
cubic_fits = _ols_predictor(lambda x: [x, x * x, x * x * x])
sqrt_fits = _ols_predictor(lambda x: [math.sqrt(x)])
log_fits = _ols_predictor(lambda x: [math.log(x)])
fp1_fits = _select_predictor(
    [_ols_predictor(lambda x, p=p: fp_terms(x, (p,))) for p in POWERS])
fp2_fits = _select_predictor(
    [_ols_predictor(lambda x, ps=ps: fp_terms(x, ps)) for ps in
     [(p1, p2) for i, p1 in enumerate(POWERS) for p2 in POWERS[i:]]])


def isotonic_predict(fitted, x):
    """Held-out prediction: exact block value, else linear interpolation
    between the nearest fitted neighbours (nearest at the ends)."""
    if x in fitted:
        return fitted[x]
    below = [k for k in fitted if k < x]
    above = [k for k in fitted if k > x]
    if not below:
        return fitted[min(above)]
    if not above:
        return fitted[max(below)]
    lo, hi = max(below), min(above)
    w = (x - lo) / (hi - lo)
    return fitted[lo] * (1 - w) + fitted[hi] * w


def isotonic_fits(xs, ys):
    from tournament import calibrate

    order = sorted(range(len(xs)), key=lambda i: xs[i])
    sx = [xs[i] for i in order]
    sv = calibrate.isotonic([ys[i] for i in order],
                            [1.0] * len(xs))
    fitted = dict(zip(sx, sv))
    return lambda x: isotonic_predict(fitted, x)


def loocv(families, xs, ys):
    """{name: mean squared held-out error}, selection inside every fold."""
    errs = {name: 0.0 for name in families}
    n = len(xs)
    for i in range(n):
        tx = xs[:i] + xs[i + 1:]
        ty = ys[:i] + ys[i + 1:]
        for name, fn in families.items():
            pred = fn(tx, ty)
            errs[name] += (pred(xs[i]) - ys[i]) ** 2
    return {name: e / n for name, e in errs.items()}
