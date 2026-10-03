"""GTP protocol pieces: command shaping and response parsing.

Source of truth for values: KataGoGtpEngine.kt (HumanSL play bundle).
The subprocess loop itself is untestable without a binary; everything
with observable behavior lives here and is pinned below.
"""

import unittest

from tournament import gtp


class GtpTest(unittest.TestCase):
    def test_opener_configures_board_rules_komi(self):
        cmds = gtp.opener(rung=10)
        self.assertIn("boardsize 9", cmds)
        self.assertIn("clear_board", cmds)
        self.assertIn("komi 7.5", cmds)
        self.assertTrue(any("chinese" in c.lower() for c in cmds))

    def test_opener_sets_profile_and_play_params(self):
        cmds = gtp.opener(rung=10)
        joined = "\n".join(cmds)
        self.assertIn("rank_10k", joined)  # rung 10 = 10k
        self.assertIn("humanSLProfile", joined)
        self.assertIn("humanSLChosenMoveProp", joined)
        self.assertIn("maxVisits 10", joined)

    def test_genmove_parses_stone_pass_resign(self):
        self.assertEqual(gtp.parse_move("= E5"), ("E5", False))
        self.assertEqual(gtp.parse_move("= pass"), ("pass", True))
        self.assertEqual(gtp.parse_move("= resign"), ("resign", False))

    def test_genmove_rejects_gtp_errors(self):
        with self.assertRaises(gtp.GtpError):
            gtp.parse_move("? unknown command")

    def test_score_parses_winner_and_margin(self):
        self.assertEqual(gtp.parse_score("= B+3.5"), ("B", 3.5))
        self.assertEqual(gtp.parse_score("= W+12.5"), ("W", 12.5))
        with self.assertRaises(gtp.GtpError):
            gtp.parse_score("? scoring failed")


if __name__ == "__main__":
    unittest.main()
