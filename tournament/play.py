"""One bot-vs-bot game on a shared persistent board (mirrors the app).

Exactly one engine process per game; the profile is (re)armed before every
move, as the app does per query. Pass twice ends it, `final_score` settles
it, resign counts as a loss. Pure logic over a thin engine interface, so
the whole loop is pinned in test_play.py without a binary.
"""

from tournament.schedule import rung_label

MAX_PLIES = 350  # 9x9 safety cap; real games end long before it


class Game:
    def __init__(self, moves, result, resigns):
        self.moves = moves  # GTP coords in play order, passes excluded
        self.result = result  # ("B"|"W", margin)
        self.resigns = resigns  # resigns played as passes (app semantics)


def play_game(engine, black_rung, white_rung):
    """Play to double pass; resign counts as a pass, exactly like the app
    (parseGenmovePacket maps resign -> pass, "bot gives up"). Scoring
    settles the true margin, so a spurious 10-visit resign costs a tempo,
    never a game. Never raises on game content."""
    moves = []
    resigns = 0
    passes = 0
    turn = "B"
    rungs = {"B": black_rung, "W": white_rung}
    while passes < 2 and len(moves) + passes < MAX_PLIES:
        engine.set_side(rungs[turn])
        move, is_pass = engine.genmove(turn)
        if move == "resign":
            resigns += 1
            passes += 1
        elif is_pass:
            passes += 1
        else:
            passes = 0
            moves.append(move)
        turn = "W" if turn == "B" else "B"
    return Game(moves, engine.final_score(), resigns)


def _sgf_coord(gtp):
    cols = "abcdefghj"  # GTP skips I, exactly like the board labels
    col = cols.index(gtp[0].lower())
    row_from_bottom = int(gtp[1:])
    row_from_top = 9 - row_from_bottom
    return chr(ord("a") + col) + chr(ord("a") + row_from_top)


def to_sgf(game, black_rung, white_rung):
    """Minimal audit SGF (SZ/KM/RU/PB/PW/RE + move list)."""
    winner, margin = game.result
    seq = "".join(
        ";%s[%s]" % ("B" if i % 2 == 0 else "W", _sgf_coord(m))
        for i, m in enumerate(game.moves)
    )
    return "(;GM[1]FF[4]SZ[9]KM[7.5]RU[Chinese]PB[%s]PW[%s]RE[%s+%.1f]%s)" % (
        rung_label(black_rung), rung_label(white_rung),
        winner, margin, seq,
    )


def to_row(game, black_rung, white_rung, game_id):
    """games.jsonl row: Black-perspective score, the fit's only input."""
    winner, _ = game.result
    return {
        "game_id": game_id,
        "black": black_rung,
        "white": white_rung,
        "score": 1.0 if winner == "B" else 0.0,
        "plies": len(game.moves),
        "resigns": game.resigns,
    }
