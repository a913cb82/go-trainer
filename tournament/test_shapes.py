"""S-shape and regime model contract (nested LOOCV like the rest).

Candidates for the ladder's bend: logistic sigmoid, Gompertz
(asymmetric S), broken stick (two linear regimes, continuous joint).
Fitted by Nelder-Mead (stdlib); every fit happens inside each LOOCV
fold, same honesty rule as FP2.
"""

import math
import unittest

from tournament import shapes


class ShapesTest(unittest.TestCase):
    def test_logistic_midpoint_symmetry(self):
        f = shapes.logistic(lo=0.0, hi=100.0, k=1.0, x0=5.0)
        self.assertAlmostEqual(f(5.0), 50.0)
        self.assertAlmostEqual(f(5.0 - 3.0) + f(5.0 + 3.0), 100.0, places=9)

    def test_logistic_approaches_asymptotes(self):
        f = shapes.logistic(lo=10.0, hi=20.0, k=2.0, x0=0.0)
        self.assertAlmostEqual(f(-50.0), 10.0, places=6)
        self.assertAlmostEqual(f(50.0), 20.0, places=6)

    def test_broken_stick_is_continuous_at_joint(self):
        f = shapes.broken_stick(b0=0.0, s1=10.0, s2=50.0, c=7.0)
        self.assertAlmostEqual(f(7.0 - 1e-7), f(7.0 + 1e-7), places=4)
        self.assertAlmostEqual(f(0.0), 0.0)
        self.assertAlmostEqual(f(10.0), 10 * 7.0 + 50 * 3.0)

    def test_nelder_mead_finds_bowl_minimum(self):
        f = lambda v: (v[0] - 3.0) ** 2 + (v[1] + 1.0) ** 2
        best, val = shapes.nelder_mead(f, [0.0, 0.0])
        self.assertAlmostEqual(best[0], 3.0, places=4)
        self.assertAlmostEqual(best[1], -1.0, places=4)
        self.assertAlmostEqual(val, 0.0, places=8)

    def test_sigmoid_family_fits_s_curve_data(self):
        # Synthetic S: sigmoid must beat linear held-out on S data.
        from tournament import model_select as M
        xs = [float(i + 1) for i in range(15)]
        ys = [100.0 / (1.0 + math.exp(-1.2 * (x - 8.0))) for x in xs]
        s = M.loocv({"linear": M.linear_fits,
                      "sigmoid": shapes.sigmoid_fits}, xs, ys)
        self.assertLess(s["sigmoid"], s["linear"])


if __name__ == "__main__":
    unittest.main()
