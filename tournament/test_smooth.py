"""Centered 3-point SMA with 18k interpolated away (contract).

Substitution first: 18k := avg(19k, 17k) free values. Then each rung is
the mean of itself and its available neighbours, e.g.
17k = avg(16k, 17k, interp18k), 19k = avg(20k, 19k, interp18k).
Ends average what exists. LOOCV predicts a held-out rung from its
fitted neighbours alone (itself unseen, same as every other family).
"""

import unittest

from tournament import smooth
from tournament.smooth import BANDS


class SmaTest(unittest.TestCase):
    def test_substitute_18k_is_neighbour_mean(self):
        ys = [float(100 + i) for i in range(29)]  # rung i -> 100+i
        sub = smooth.substitute(ys, 2)
        self.assertAlmostEqual(sub[2], (101.0 + 103.0) / 2.0)
        self.assertEqual(sub[:2] + sub[3:], ys[:2] + ys[3:])

    def test_sma_matches_hand_values(self):
        ys = [float(100 + i) for i in range(29)]
        s = smooth.sma(smooth.substitute(ys, 2))
        # 19k(rung1) = avg(interp18k, 19k, 20k)
        self.assertAlmostEqual(s[1], (102.0 + 101.0 + 100.0) / 3.0)
        # 17k(rung3) = avg(16k, 17k, interp18k)
        self.assertAlmostEqual(s[3], (104.0 + 103.0 + 102.0) / 3.0)
        # ends average what exists
        self.assertAlmostEqual(s[0], (100.0 + 101.0) / 2.0)
        self.assertAlmostEqual(s[28], (127.0 + 128.0) / 2.0)

    def test_loocv_scores_sma(self):
        from tournament import model_select as M
        xs = [float(i + 1) for i in range(12)]
        ys = [5.0 - 1.5 * x for x in xs]
        s = M.loocv({"linear": M.linear_fits, "sma": smooth.sma_fits},
                    xs, ys)
        # straight line: SMA tracks well, in the same ballpark as linear
        self.assertLess(s["sma"], 4 * s["linear"] + 1.0)

    def test_bands_cover_all_rungs_once(self):
        covered = [i for lo, hi in BANDS for i in range(lo, hi + 1)]
        self.assertEqual(sorted(covered), list(range(29)))

    def test_seg3_recovers_line_per_band(self):
        from tournament import model_select as M
        xs = [float(i + 1) for i in range(29)]
        ys = [2.0 * x - 7.0 for x in xs]
        pred = smooth.seg3_fits(xs, ys)
        for x in (1.0, 9.0, 15.0, 29.0):
            self.assertAlmostEqual(pred(x), 2.0 * x - 7.0, places=6)

    def test_seg3_follows_band_levels(self):
        # flat bottom, steep top: segments must differ, joints may jump
        ys = [0.0] * 10 + [100.0 + 5.0 * i for i in range(10, 20)] \
            + [200.0 + 8.0 * i for i in range(20, 29)]
        xs = [float(i + 1) for i in range(29)]
        pred = smooth.seg3_fits(xs, ys)
        self.assertAlmostEqual(pred(1.0), 0.0, places=6)
        self.assertGreater(pred(25.0) - pred(21.0), 20.0)


if __name__ == "__main__":
    unittest.main()
