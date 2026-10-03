"""Komi counterfactuals from recorded margins (no new games needed).

A recorded Black margin m under komi 7.5 becomes m + (7.5 - k) under
komi k, assuming identical play. Exact for small komi moves (only close
games flip); behavioral response (passing, close-endgame choices) is the
unmodeled error, growing with |7.5 - k|.
"""

import re
import unittest

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


class KomiTest(unittest.TestCase):
    def test_margin_sign(self):
        self.assertEqual(black_margin("(;RE[B+3.5])"), 3.5)
        self.assertEqual(black_margin("(;RE[W+3.5])"), -3.5)

    def test_close_games_flip(self):
        # B+0.5 at 7.5: Black still wins at 6.5, loses at 8.5.
        self.assertEqual(winner_at(0.5, 6.5), 1.0)
        self.assertEqual(winner_at(0.5, 8.5), 0.0)
        # W+0.5 at 7.5 flips to Black at 6.5.
        self.assertEqual(winner_at(-0.5, 6.5), 1.0)

    def test_blowouts_never_flip(self):
        self.assertEqual(winner_at(85.5, 0.5), 1.0)
        self.assertEqual(winner_at(-85.5, 12.5), 0.0)


if __name__ == "__main__":
    unittest.main()
