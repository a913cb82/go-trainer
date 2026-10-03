"""Pairing schedule generation (pure — no engine, no I/O).

Ladder: 29 rungs, index 0 = 20k … 28 = 9d. Mirrors the app's Rank/BotRatings
mapping; if the app ladder ever changes, this module must follow it.
"""

import random

RUNGS = 29
LADDER_SPAN = (1, 2, 3)

# Fixed long-range anchors: full span, halves, and thirds coverage so the
# scale can't drift on adjacent-only data. Chosen once, recorded forever.
LONG_RANGE = [
    (0, 28),
    (0, 14), (14, 28),
    (0, 19), (9, 28),
    (4, 14), (14, 24),
    (19, 28),
    (0, 9), (9, 19),
]


def rung_label(i):
    """Human label for a rung index (mirrors BotRatings.rankLabel)."""
    if not 0 <= i < RUNGS:
        raise ValueError("rung %r outside 0..%d" % (i, RUNGS - 1))
    if i < 20:
        return "%dk" % (20 - i)
    return "%dd" % (i - 19)


def ladder_pairs():
    """Unique unordered pairings (a, b) with 1 <= |a-b| <= 3."""
    pairs = []
    for dist in LADDER_SPAN:
        for a in range(RUNGS - dist):
            pairs.append((a, a + dist))
    return pairs


def all_pairings():
    """Ladder pairings plus the fixed long-range anchors (81 + 10)."""
    return ladder_pairs() + LONG_RANGE


def build_schedule(n=10, seed=0):
    """Full game list: (black_rung, white_rung) per game.

    Every pairing plays n games per color, colors alternate within a
    pairing, and the whole list is shuffled under the seed. Interleaving
    guards against time confounds (GPU temperature, throttling); the seed
    makes the order reproducible and recorded in the manifest.
    """
    games = []
    for (a, b) in all_pairings():
        for k in range(n):
            games.append((a, b) if k % 2 == 0 else (b, a))
            games.append((b, a) if k % 2 == 0 else (a, b))
    rng = random.Random(seed)
    rng.shuffle(games)
    return games
