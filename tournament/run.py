"""Tournament runner: smoke | batch. Usage:

  python3 tournament/run.py smoke --black 10 --white 10
  python3 tournament/run.py batch --pairs 10-10 --n 4 --streams 1 --out runs/pilot-det
  python3 tournament/run.py batch --pairs 10-9,9-8,10-8 --n 10 --streams 3 --out runs/mini

Each game gets a fresh engine process (startup ~3s amortizes over ~60
moves). GtpError quarantines after one retry with a fresh engine; the
quarantine log is part of the evidence, never silently dropped.
"""

import argparse
import re
import concurrent.futures
import hashlib
import json
import os
import subprocess
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from tournament import gtp, play  # noqa: E402

KATAGO = os.path.join(HERE, "bin", "katago")
MODEL = os.path.join(HERE, "models", "b10.bin")
HUMAN_MODEL = os.path.join(HERE, "models", "b18c384nbt-humanv0.bin.gz")

APP_CFG = os.path.join(HERE, "..", "android", "app", "src", "main",
                     "assets", "gtp.cfg")


def write_cfg(out_dir, threads):
    """Config is the app's staged base with our thread count. Models, rules,
    and komi arrive via flags/GTP (mirrors the app); resign behavior,
    search factors, and cache sizing ride along from the shipped cfg."""
    import re
    os.makedirs(out_dir, exist_ok=True)
    cfg = os.path.join(out_dir, "gtp.cfg")
    with open(APP_CFG) as f:
        body = f.read()
    body = re.sub(r"(?m)^numSearchThreads\s*=.*$",
                  "numSearchThreads = %d" % threads, body)
    with open(cfg, "w") as f:
        f.write(body)
        # Mirror the app's staging append: thread count + log dir.
        f.write("numSearchThreads = %d\n" % threads)
        logs = os.path.join(os.path.abspath(out_dir), "gtp_logs")
        os.makedirs(logs, exist_ok=True)
        f.write("logDir = %s\n" % logs)
    return cfg




def md5(path):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def katago_version():
    out = subprocess.run([KATAGO, "version"], capture_output=True, text=True)
    return (out.stdout + out.stderr).strip().splitlines()[0]


def fresh_engine(cfg):
    eng = gtp.Engine(KATAGO, MODEL, HUMAN_MODEL, cfg)
    for cmd in ("boardsize 9", "clear_board", "komi 7.5",
                "kata-set-rules chinese", "time_settings 0 10 1"):
        eng.command(cmd)
    return eng


# One persistent engine per worker thread: ~25s startup amortizes over
# the whole stream instead of every game. clear_board per game isolates.
_local = threading.local()


def worker_engine(cfg):
    eng = getattr(_local, "eng", None)
    if eng is None or eng.proc.poll() is not None:
        eng = fresh_engine(cfg)
        _local.eng = eng
    else:
        for cmd in ("boardsize 9", "clear_board"):
            eng.command(cmd)
    return eng


def drop_worker_engine():
    eng = getattr(_local, "eng", None)
    try:
        if eng is not None:
            eng.proc.kill()
    except Exception:
        pass
    _local.eng = None


def one_game(pair, game_id, cfg, out_dir, quaran):
    black, white = pair
    for _ in (1, 2):
        try:
            eng = worker_engine(cfg)
            t0 = time.time()
            game = play.play_game(eng, black, white)
            secs = time.time() - t0
            row = play.to_row(game, black, white, game_id)
            row["seconds"] = round(secs, 1)
            with open(os.path.join(out_dir, "games.jsonl"), "a") as f:
                f.write(json.dumps(row) + "\n")
            with open(os.path.join(out_dir, "%s.sgf" % game_id), "w") as f:
                f.write(play.to_sgf(game, black, white))
            return ("ok", row)
        except gtp.GtpError as e:
            last = e
            drop_worker_engine()
    with open(os.path.join(out_dir, "quarantine.jsonl"), "a") as f:
        f.write(json.dumps({"game_id": game_id, "pair": pair,
                            "error": str(last)}) + "\n")
    return ("quarantined", None)


def parse_pairs(s):
    pairs = []
    for tok in s.split(","):
        a, b = tok.split("-")
        pairs.append((int(a), int(b)))
    return pairs


def cmd_smoke(args):
    cfg = write_cfg(args.out, args.threads)
    os.makedirs(args.out, exist_ok=True)
    status, row = one_game((args.black, args.white), "smoke", cfg,
                           args.out, None)
    print(status, json.dumps(row, indent=1) if row else "")


def cmd_batch(args):
    from tournament.schedule import rung_label

    cfg = write_cfg(args.out, args.threads)
    pairs = parse_pairs(args.pairs)
    jobs = []
    seq = 0
    for (a, b) in pairs:
        for k in range(args.n):
            jobs.append(((a, b), "g-%04d-b%d-w%d" % (seq, a, b)))
            seq += 1
            jobs.append(((b, a), "g-%04d-b%d-w%d" % (seq, b, a)))
            seq += 1
    # Interleave (schedule.py's rule): no pairing runs back-to-back.
    import random
    rng = random.Random(args.seed)
    rng.shuffle(jobs)
    t0 = time.time()
    results = []
    with concurrent.futures.ThreadPoolExecutor(
            max_workers=args.streams) as pool:
        futs = {pool.submit(one_game, pair, gid, cfg, args.out, None): gid
                for pair, gid in jobs}
        for fut in concurrent.futures.as_completed(futs):
            results.append(fut.result())
    secs = time.time() - t0
    ok = sum(1 for s, _ in results if s == "ok")
    print("games=%d ok=%d quarantined=%d wall=%.0fs (%.1f games/min)" % (
        len(jobs), ok, len(jobs) - ok, secs, 60.0 * ok / max(secs, 1)))
    manifest = {
        "katago": katago_version(),
        "md5": {"katago": md5(KATAGO), "b10": md5(MODEL),
                "human": md5(HUMAN_MODEL)},
        "pairs": ["%s-%s" % (rung_label(a), rung_label(b)) for a, b in pairs],
        "n_per_color": args.n,
        "seed": args.seed,
        "streams": args.streams,
        "threads": args.threads,
        "play_params": gtp.PLAY_PARAMS,
    }
    with open(os.path.join(args.out, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=1, default=str)


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    sm = sub.add_parser("smoke")
    sm.add_argument("--black", type=int, default=10)
    sm.add_argument("--white", type=int, default=10)
    sm.add_argument("--threads", type=int, default=4)
    sm.add_argument("--out", default="tournament/runs/smoke")
    sm.set_defaults(fn=cmd_smoke)
    ba = sub.add_parser("batch")
    ba.add_argument("--pairs", required=True)
    ba.add_argument("--n", type=int, default=10)
    ba.add_argument("--seed", type=int, default=0)
    ba.add_argument("--streams", type=int, default=1)
    ba.add_argument("--threads", type=int, default=4)
    ba.add_argument("--out", required=True)
    ba.set_defaults(fn=cmd_batch)
    args = ap.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
