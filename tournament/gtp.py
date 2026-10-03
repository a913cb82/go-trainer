"""GTP engine driver for bot-vs-bot games (tournament branch only).

Source of truth for every value: KataGoGtpEngine.kt. The pilot diffs the
opener below against the app's engine log line-for-line before any game
counts (see README). The subprocess loop is untestable without a binary;
all parsing/shaping with observable behavior is pinned in test_gtp.py.
"""

import re
import subprocess

BOARD = 9
KOMI = 7.5
THINK_TIME_SEC = 10  # mirrors KataGoGtpEngine.THINK_TIME_SEC
PLAY_VISITS = 10  # mirrors KataGoGtpEngine.PLAY_VISITS

# HumanSL play bundle, copied from KataGoGtpEngine.humanSlParams().
# PILOT: extend verbatim from humanSlParams() (cpuct etc.) — the pilot
# opener diff against the app log is the gate, not this comment.
PLAY_PARAMS = {
    "chosenMoveTemperatureEarly": 0.85,
    "chosenMoveTemperature": 0.70,
    "chosenMoveTemperatureHalflife": 80,
    "chosenMoveTemperatureOnlyBelowProb": 0.01,
    "humanSLChosenMoveProp": 1.0,
    "humanSLChosenMoveIgnorePass": True,
    "humanSLChosenMovePiklLambda": 100000000,
    "humanSLRootExploreProbWeightless": 0.0,
    "humanSLRootExploreProbWeightful": 0.0,
    "humanSLPlaExploreProbWeightless": 0.0,
    "humanSLPlaExploreProbWeightful": 0.0,
    "humanSLOppExploreProbWeightless": 0.0,
    "humanSLOppExploreProbWeightful": 0.0,
    "maxVisits": PLAY_VISITS,
}


class GtpError(Exception):
    """Engine answered '?' or the result didn't parse. Quarantine, don't fit."""


def profile_for(rung):
    """rank_<id> for a rung index (mirrors profileFor; ladder starts at 20k)."""
    from tournament.schedule import rung_label

    return "rank_" + rung_label(rung)


def _params_json():
    import json

    return json.dumps(PLAY_PARAMS, separators=(",", ":"))


def opener(rung):
    """GTP lines opening a game for one side's profile (fresh board)."""
    return [
        "boardsize %d" % BOARD,
        "clear_board",
        "komi %s" % KOMI,
        "kata-set-rules chinese",  # PILOT: confirm against the app's gtp.cfg
        "kata-set-param humanSLProfile %s" % profile_for(rung),
        "kata-set-params %s" % _params_json(),
        "kata-set-param maxVisits %d" % PLAY_VISITS,
        "time_settings 0 %d 1" % THINK_TIME_SEC,
    ]


def parse_move(line):
    """`= E5` -> ("E5", False); `= pass` -> ("pass", True)."""
    body = _body(line).strip().lower()
    if body == "pass":
        return ("pass", True)
    if body == "resign":
        return ("resign", False)
    if re.fullmatch(r"[a-hj][1-9]", body):
        return (body.upper(), False)
    raise GtpError("unparseable move: %r" % line)


_SCORE = re.compile(r"([BW])\+([0-9]+(?:\.[0-9]+)?)")


def parse_score(line):
    """`= B+3.5` -> ("B", 3.5)."""
    m = _SCORE.fullmatch(_body(line).strip())
    if not m:
        raise GtpError("unparseable score: %r" % line)
    return (m.group(1), float(m.group(2)))


def _body(line):
    if not line.startswith("="):
        raise GtpError("GTP error: %r" % line)
    return line[1:]


class Engine:
    """One persistent-board `katago gtp` process. PILOT: needs the binary."""

    def __init__(self, katago_bin, model, human_model, config):
        self.proc = subprocess.Popen(
            [katago_bin, "gtp", "-model", model, "-human-model", human_model,
             "-config", config],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, text=True, bufsize=1,
        )

    def command(self, cmd, timeout=30):
        import queue
        import threading

        self.proc.stdin.write(cmd + "\n")
        self.proc.stdin.flush()
        out = queue.Queue()

        def read():
            lines = []
            while True:
                line = self.proc.stdout.readline()
                lines.append(line)
                if line.startswith("=") or line.startswith("?"):
                    break
            out.put("".join(lines).strip())

        t = threading.Thread(target=read, daemon=True)
        t.start()
        t.join(timeout)
        if t.is_alive() or out.empty():
            raise GtpError("timeout on %r" % cmd)
        return out.get()
