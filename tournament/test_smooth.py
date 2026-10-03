"""Centered 3-point SMA with 18k interpolated away (contract).

Substitution first: 18k := avg(19k, 17k) free values. Then each rung is
the mean of itself and its available neighbours, e.g.
17k = avg(16k, 17k, interp18k), 19k = avg(20k, 19k, interp18k).
Ends average what exists. LOOCV predicts a held-out rung from its
fitted neighbours alone (itself unseen, same as every other family).
"""

import unittest

from tournament import smooth


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


if __name__ == "__main__":
    unittest.main()
