"""Game loop contract, driven by a scripted stub (no binary needed)."""

import unittest

from tournament import play


class StubEngine:
    """Scripted moves: {(color): [moves...]}, then passes; fixed score."""

    def __init__(self, script, winner="B", margin=3.5):
        self.script = {c: list(m) for c, m in script.items()}
        self.winner = winner
        self.margin = margin
        self.profiles = []

    def set_side(self, rung):
        self.profiles.append(rung)

    def genmove(self, color):
        moves = self.script.get(color, [])
        return (moves.pop(0), False) if moves else ("pass", True)

    def final_score(self):
        return (self.winner, self.margin)


class PlayTest(unittest.TestCase):
    def test_alternates_until_double_pass_then_scores(self):
        eng = StubEngine({"B": ["C3", "D4"], "W": ["C4"]})
        game = play.play_game(eng, black_rung=10, white_rung=12)
        self.assertEqual(game.moves, ["C3", "C4", "D4"])
        self.assertEqual(game.result, ("B", 3.5))
        self.assertEqual(game.resigns, 0)

    def test_profiles_follow_each_side(self):
        eng = StubEngine({"B": ["C3"], "W": ["C4"]})
        play.play_game(eng, black_rung=10, white_rung=12)
        # Profile set before every move: B10 W12 B10(pass) W12(pass).
        self.assertEqual(eng.profiles, [10, 12, 10, 12])

    def test_resign_counts_as_pass_and_is_recorded(self):
        # App semantics: resign -> pass ("bot gives up"); scoring settles.
        eng = StubEngine({"B": ["resign"], "W": []})
        game = play.play_game(eng, black_rung=10, white_rung=12)
        self.assertEqual(game.moves, [])
        self.assertEqual(game.resigns, 1)
        self.assertEqual(game.result, ("B", 3.5))

    def test_sgf_records_board_rules_and_moves(self):
        eng = StubEngine({"B": ["C3"], "W": []})
        game = play.play_game(eng, black_rung=10, white_rung=12)
        sgf = play.to_sgf(game, black_rung=10, white_rung=12)
        self.assertIn("SZ[9]", sgf)
        self.assertIn("KM[7.5]", sgf)
        self.assertIn(";B[cg]", sgf)  # C3: 3rd col, 3rd row from bottom
        self.assertIn("PB[10k]", sgf)
        self.assertIn("PW[8k]", sgf)

    def test_row_is_black_perspective_jsonl(self):
        eng = StubEngine({"B": ["C3"], "W": []}, winner="W", margin=5.5)
        game = play.play_game(eng, black_rung=10, white_rung=12)
        row = play.to_row(game, black_rung=10, white_rung=12, game_id="g1")
        self.assertEqual(row["black"], 10)
        self.assertEqual(row["white"], 12)
        self.assertEqual(row["score"], 0.0)  # White won: Black scores 0
        self.assertEqual(row["game_id"], "g1")


if __name__ == "__main__":
    unittest.main()
