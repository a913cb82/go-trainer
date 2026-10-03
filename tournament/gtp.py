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

# HumanSL play bundle, verbatim from KataGoGtpEngine.humanSlParams()
# (BadukAI's HUMAN_SL_PARAMS, activated column). Any drift here voids
# fidelity; the pilot opener diff against the app log is the gate.
PLAY_PARAMS = {
    "analysisIgnorePreRootHistory": False,
    "rootNumSymmetriesToSample": 2,
    "useLcbForSelection": False,
    "staticScoreUtilityFactor": 0.3,
    "dynamicScoreUtilityFactor": 0.0,
    "useUncertainty": False,
    "subtreeValueBiasFactor": 0.0,
    "useNoisePruning": False,
    "chosenMoveTemperatureEarly": 0.85,
    "chosenMoveTemperature": 0.70,
    "chosenMoveTemperatureHalflife": 80,
    "chosenMoveTemperatureOnlyBelowProb": 0.01,
    "chosenMovePrune": 0,
    "humanSLChosenMoveProp": 1.0,
    "humanSLChosenMoveIgnorePass": True,
    "humanSLChosenMovePiklLambda": 100000000,
    "humanSLRootExploreProbWeightless": 0.0,
    "humanSLRootExploreProbWeightful": 0.0,
    "humanSLPlaExploreProbWeightless": 0.0,
    "humanSLPlaExploreProbWeightful": 0.0,
    "humanSLOppExploreProbWeightless": 0.0,
    "humanSLOppExploreProbWeightful": 0.0,
    "humanSLCpuctExploration": 0.50,
    "humanSLCpuctPermanent": 0.2,
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
        "kata-set-rules chinese",
        "kata-set-param humanSLProfile %s" % profile_for(rung),
        "kata-set-params %s" % _params_json(),
        "kata-set-param maxVisits %d" % PLAY_VISITS,
        "time_settings 0 %d 1" % THINK_TIME_SEC,
    ]


def parse_move(line):
    """Genmove result in any app shape: `= E5`, `play E5`, `= pass`."""
    t = line.strip()
    if t.startswith("?"):
        raise GtpError("GTP error: %r" % line)
    m = re.fullmatch(r"(?:=\s*)?(?:play\s+)?(\S+)", t, re.IGNORECASE)
    if not m:
        raise GtpError("unparseable move: %r" % line)
    body = m.group(1).lower()
    if body == "pass":
        return ("pass", True)
    if body == "resign":
        return ("resign", False)
    if re.fullmatch(r"[a-hj][1-9]", body):
        return (body.upper(), False)
    raise GtpError("unparseable move: %r" % line)


_SCORE = re.compile(r"([BW])\+([0-9]+(?:\.[0-9]+)?)")


def parse_score(line):
    """`= B+3.5` -> ("B", 3.5). An exact `= 0` draw quarantines: with
    komi 7.5 it signals breakage, never a real result."""
    t = line.strip()
    if t.startswith("?"):
        raise GtpError("GTP error: %r" % line)
    if re.fullmatch(r"=\s*0\b", t):
        raise GtpError("drawn game (komi 7.5): %r" % line)
    m = _SCORE.fullmatch(t[1:].strip() if t.startswith("=") else t)
    if not m:
        raise GtpError("unparseable score: %r" % line)
    return (m.group(1), float(m.group(2)))


class Engine:
    """One persistent-board `katago gtp` process per game.

    Reader thread feeds a queue (as the app does); every call sends one
    command and collects until its answer shape. Needs the binary.
    Implements play.py's interface: set_side / genmove / final_score.
    """

    GENMOVE_TIMEOUT = 30  # mirrors KataGoGtpEngine.GENMOVE_TIMEOUT_MS
    CMD_TIMEOUT = 30
    PLAY_LINE = re.compile(r"^play\s+([A-HJ][1-9]|pass|resign)\s*$", re.IGNORECASE)

    _LIVE = []

    def __init__(self, katago_bin, model, human_model, config):
        import atexit
        import queue
        import threading

        self.q = queue.Queue()
        self.proc = subprocess.Popen(
            [katago_bin, "gtp", "-model", model, "-human-model", human_model,
             "-config", config],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, text=True, bufsize=1,
        )

        def read():
            for line in self.proc.stdout:
                if line.strip():
                    self.q.put(line.strip())

        threading.Thread(target=read, daemon=True).start()
        Engine._LIVE.append(self.proc)

    def _send(self, cmd):
        self.proc.stdin.write(cmd + "\n")
        self.proc.stdin.flush()

    def _collect(self, done, cmd, timeout):
        import time

        self._send(cmd)
        deadline = time.time() + timeout
        while True:
            remaining = deadline - time.time()
            if remaining <= 0:
                raise GtpError("timeout on %r (dead engine?)" % cmd)
            try:
                line = self.q.get(timeout=remaining)
            except Exception:
                if self.proc.poll() is not None:
                    raise GtpError("engine died on %r" % cmd)
                continue
            if line.startswith("?"):
                raise GtpError("GTP error on %r: %s" % (cmd, line))
            hit = done(line)
            if hit is not None:
                return hit

    def command(self, cmd):
        """One command, one `= ...` line back."""
        return self._collect(
            lambda l: l if l.startswith("=") else None,
            cmd, self.CMD_TIMEOUT,
        )

    def set_side(self, rung):
        """(Re)arm the profile + play bundle, exactly like setProfile."""
        self.command("kata-set-param humanSLProfile %s" % profile_for(rung))
        self.command("kata-set-params %s" % _params_json())
        self.command("kata-set-param maxVisits %d" % PLAY_VISITS)

    def genmove(self, color):
        """kata-genmove_analyze (min 5 visits), result off the play line."""
        line = self._collect(
            lambda l: l if self.PLAY_LINE.search(l) else None,
            "kata-genmove_analyze %s 5 rootInfo true" % color,
            self.GENMOVE_TIMEOUT,
        )
        return parse_move(line)

    def final_score(self):
        return parse_score(self.command("final_score"))

def _kill_all():
    for proc in Engine._LIVE:
        try:
            proc.kill()
        except Exception:
            pass


import atexit as _atexit
_atexit.register(_kill_all)
