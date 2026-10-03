"""Komi counterfactuals from recorded margins (no new games needed).

A recorded Black margin m under komi 7.5 becomes m + (7.5 - k) under
komi k, assuming identical play. Exact for small komi moves (only close
games flip); behavioral response (passing, close-endgame choices) is the
unmodeled error, growing with |7.5 - k|.
"""

import re

BASE_KOMI = 7.5


def black_margin(sgf):
    """Black-perspective final margin, komi included. B+3.5 -> 3.5."""
    m = re.search(r"RE\[([BW])\+([0-9.]+)\]", sgf)
    if not m:
        raise ValueError("no result in SGF")
    x = float(m.group(2))
    return x if m.group(1) == "B" else -x


def winner_at(margin, komi, base=BASE_KOMI):
    """Black score (1/0.5/0) under komi, holding play fixed."""
    m = margin + (base - komi)
    if m > 0:
        return 1.0
    if m < 0:
        return 0.0
    return 0.5


def curve(margins, komis):
    """Black win rate at each komi (draws count half)."""
    return {k: sum(winner_at(m, k) for m in margins) / len(margins)
            for k in komis}
