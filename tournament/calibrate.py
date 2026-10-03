"""Calibration assembly: isotonic monotonicity, table pinning, wave-two targeting.

Implements the pre-committed analysis policy. Pure; pinned in
test_calibrate.py.
"""

PIN_20K = 800.0
# Posterior P(genuine inversion) above this earns wave-two games.
DISPUTE_THRESHOLD = 0.25


def isotonic(xs, weights):
    """Weighted pool-adjacent-violators: non-decreasing output.

    Each pooled block takes its inverse-variance-weighted mean, so
    certain points anchor and noisy ones yield — never the reverse.
    """
    n = len(xs)
    blocks = [[xs[i], weights[i], 1] for i in range(n)]  # [sum, wsum, count]
    out = [0.0] * n
    i = 0
    stack = []  # (mean, weight, start, end)
    for i in range(n):
        stack.append([xs[i], weights[i], i, i + 1])
        while len(stack) >= 2 and stack[-2][0] > stack[-1][0]:
            m2, w2, s2, e2 = stack.pop()
            m1, w1, s1, e1 = stack.pop()
            w = w1 + w2
            stack.append([(m1 * w1 + m2 * w2) / w, w, s1, e2])
    for mean, _, s, e in stack:
        for i in range(s, e):
            out[i] = mean
    return out


def to_table(xs):
    """Shift relative fit onto the 20k=800 absolute pin."""
    return [PIN_20K + x for x in xs]


def wave_two_targets(disputes):
    """[(a, b, p_genuine)] -> pairs earning more games."""
    return [(a, b) for a, b, p in disputes if p >= DISPUTE_THRESHOLD]
