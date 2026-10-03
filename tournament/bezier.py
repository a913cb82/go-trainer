"""Fixed-knot Bezier fits via OLS on the Bernstein basis (pure stdlib)."""

import math


def basis(n, t):
    """Degree-n Bernstein basis at t in [0,1]."""
    return [math.comb(n, i) * t ** i * (1.0 - t) ** (n - i)
            for i in range(n + 1)]


def bezier_fits(degree):
    """Fit control heights by OLS; knots evenly spaced over data range."""
    from tournament import model_select as M

    def fit_predictor(xs, ys):
        lo, hi = min(xs), max(xs)
        span = hi - lo if hi > lo else 1.0
        design = [basis(degree, (x - lo) / span) for x in xs]
        coefs = M.ols(design, ys)
        return lambda x: sum(c * b for c, b in
                             zip(coefs, basis(degree, (x - lo) / span)))
    fit_predictor.degree = degree
    return fit_predictor
