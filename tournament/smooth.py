"""Centered 3-point SMA with outlier substitution (pure stdlib)."""

SKIP = 2  # 18k: interpolated away before smoothing
BANDS = ((0, 9), (10, 19), (20, 28))  # 20k-11k, 10k-1k, 1d-9d"

SKIP = 2  # 18k: interpolated away before smoothing


def substitute(ys, idx=SKIP):
    """Replace ys[idx] by the mean of its neighbours."""
    out = list(ys)
    out[idx] = (ys[idx - 1] + ys[idx + 1]) / 2.0
    return out


def sma(ys):
    """Centered 3-point mean; ends average what exists."""
    n = len(ys)
    out = []
    for i in range(n):
        pts = ys[max(0, i - 1):min(n, i + 2)]
        out.append(sum(pts) / len(pts))
    return out


def sma_fits(xs, ys):
    """LOOCV predictor: held-out rung from its fitted neighbours."""
    fitted = dict(zip(xs, ys))

    def predict(x):
        pts = [fitted[k] for k in (x - 1, x, x + 1) if k in fitted]
        return sum(pts) / len(pts)

    return predict


def _band_of(x):
    i = int(round(x)) - 1
    for b, (lo, hi) in enumerate(BANDS):
        if lo <= i <= hi:
            return b
    return len(BANDS) - 1


def seg3_fits(xs, ys):
    """Independent OLS line per band (joints free to disagree)."""
    from tournament import model_select as M

    lines = []
    for lo, hi in BANDS:
        pts = [(x, y) for x, y in zip(xs, ys) if lo <= int(round(x)) - 1 <= hi]
        coefs = M.ols([[1.0, x] for x, _ in pts], [y for _, y in pts])
        lines.append(coefs)

    def predict(x):
        c = lines[_band_of(x)]
        return c[0] + c[1] * x

    return predict
