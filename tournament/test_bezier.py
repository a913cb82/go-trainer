"""Bezier ladder fits: fixed-knot Bezier is polynomial regression.

A degree-n Bezier with evenly spaced x-knots spans the degree-n
polynomials, so the fit is linear in the control heights (plain OLS on
the Bernstein basis). Cubic Bezier must reproduce cubic regression;
higher degrees buy wiggles.
"""

import math
import unittest

from tournament import bezier


class BezierTest(unittest.TestCase):
    def test_bernstein_partition_of_unity(self):
        for t in (0.0, 0.25, 0.7, 1.0):
            self.assertAlmostEqual(sum(bezier.basis(3, t)), 1.0)

    def test_endpoints_interpolate_controls(self):
        self.assertEqual(bezier.basis(3, 0.0), [1.0, 0.0, 0.0, 0.0])
        self.assertEqual(bezier.basis(3, 1.0), [0.0, 0.0, 0.0, 1.0])

    def test_cubic_bezier_matches_cubic_regression(self):
        from tournament import model_select as M
        xs = [float(i) for i in range(10)]
        ys = [1.0 + 2.0 * x - 0.5 * x * x + 0.1 * x ** 3 for x in xs]
        pred = bezier.bezier_fits(3)(xs, ys)
        lin = M.cubic_fits([x + 1 for x in xs], ys)
        for x in (0.5, 4.5, 9.0):
            self.assertAlmostEqual(pred(x), lin(x + 1), places=6)


if __name__ == "__main__":
    unittest.main()
