"""Centered 3-point SMA with outlier substitution (pure stdlib)."""

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
