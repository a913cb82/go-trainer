"""Model-selection contract: LOOCV with selection inside each fold.

Honest comparison of ladder shapes (linear, FP1/FP2, sqrt, log,
quadratic, isotonic): for each held-out rung, every choice — including
FP2's powers — is made on the other 28 alone, then predicts the held
out. No peeking, ever.
"""

import unittest

from tournament import model_select as M


class ModelSelectTest(unittest.TestCase):
    def test_fp_transforms(self):
        # Power 0 is log; repeated powers get the x^p*ln(x) form.
        import math
        self.assertEqual(M.fp_terms(2.0, (1, 1))[0], 2.0)
        self.assertAlmostEqual(M.fp_terms(2.0, (1, 1))[1], 2.0 * math.log(2.0))
        self.assertAlmostEqual(M.fp_terms(4.0, (0,))[0], math.log(4.0))
        self.assertEqual(M.fp_terms(8.0, (2, 3)), [64.0, 512.0])

    def test_ols_recovers_line(self):
        xs = [float(i) for i in range(10)]
        ys = [3.0 + 2.0 * x for x in xs]
        coefs = M.ols([[1.0, x] for x in xs], ys)
        self.assertAlmostEqual(coefs[0], 3.0, places=6)
        self.assertAlmostEqual(coefs[1], 2.0, places=6)

    def test_loocv_selects_inside_folds(self):
        # Perfectly linear data: linear must beat FP2 (which can
        # overfit the 28 training points but gains nothing held out).
        xs = [float(i + 1) for i in range(12)]
        ys = [5.0 - 1.5 * x for x in xs]
        scores = M.loocv({"linear": M.linear_fits, "fp2": M.fp2_fits},
                          xs, ys)
        # FP2 contains the line: in-fold it ties, held out it cannot win.
        self.assertLessEqual(scores["linear"], scores["fp2"] + 1e-9)

    def test_isotonic_predicts_monotone_neighbourhood(self):
        # Held-out interior point: interpolate fitted neighbours.
        fitted = {0: 0.0, 1: 0.0, 3: 30.0, 4: 40.0}  # rung 2 held out
        self.assertAlmostEqual(M.isotonic_predict(fitted, 2), 15.0)


if __name__ == "__main__":
    unittest.main()
