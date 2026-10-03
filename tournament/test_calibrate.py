"""Monotonicity + calibration assembly contract.

Policy (pre-committed): free MLE first; isotonic regression (weighted
PAVA) enforces the ladder's promise that labels never invert; the table
pins 20k at 800. Disputed pairs go to wave two, never into hand-tuning.
"""

import unittest

from tournament import calibrate


class CalibrateTest(unittest.TestCase):
    def test_pava_leaves_ordered_data_alone(self):
        xs = [0.0, 40.0, 80.0]
        self.assertEqual(calibrate.isotonic(xs, [1.0, 1.0, 1.0]), xs)

    def test_pava_pools_a_noise_flip(self):
        # Middle rung measured high by noise; pools with neighbors.
        out = calibrate.isotonic([0.0, 90.0, 80.0], [1.0, 1.0, 1.0])
        self.assertTrue(out[0] <= out[1] <= out[2])
        self.assertAlmostEqual(out[1], out[2])  # pooled block shares value

    def test_pava_weights_trust_certain_points(self):
        # Same flip, but the middle point is certain: the pooled block
        # sits next to it (89.9) instead of the naive mean (56.7).
        out = calibrate.isotonic([0.0, 90.0, 80.0], [1.0, 100.0, 1.0])
        self.assertTrue(out[0] <= out[1] <= out[2])
        self.assertAlmostEqual(out[1], 89.901, places=3)

    def test_table_pins_20k_at_800(self):
        xs = [0.0, 40.0, 80.0]
        table = calibrate.to_table(xs)
        self.assertEqual(table[0], 800.0)
        self.assertAlmostEqual(table[1] - table[0], 40.0)

    def test_wave_two_targets_disputed_pairs(self):
        # Only high-P(genuine) inversions earn more games.
        targets = calibrate.wave_two_targets(
            [(7, 8, 0.6), (12, 13, 0.02), (20, 21, 0.9)])
        self.assertEqual(targets, [(7, 8), (20, 21)])


if __name__ == "__main__":
    unittest.main()
