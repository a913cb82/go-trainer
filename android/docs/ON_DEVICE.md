# Android on-device notes (living doc — update after every phone session)

## Gesture fix merged + installed (2026-10-03, `stats-gestures` -> main)

Root cause of the one-step gestures found on PC: the detectors were keyed
on the viewport, restarting mid-gesture. Stable keys + refs now; trajectory
clipped to the plot; preset row and hint caption removed. Installed over
WiFi adb, launches clean (empty board, 14k). Finger-feel retest pending.

## Stats gestures merged + installed (2026-10-03, `stats-zoom` -> main)

Merged `0e53f21` to main, `installDebug` over WiFi adb clean, app launches
with no crash (empty board, restored 14k rank). Stats graph now pans/zooms
with y auto-scale; tap-axis toggle + double-tap reset untested by fingers —
needs a device pass.

## Free-play-only cut (2026-10-03, `free-play-only` branch)

Multiple-choice mode removed from the shipped app: no candidate queries
(`kata-analyze`, raw human policy, analysis-mode params all deleted from
`KataGoGtpEngine`), no per-move evaluations, no feedback UI, save format
v1 -> v2 (old saves start fresh; rated history survives on its own key).
The candidate-breadth notes below are history, not live behavior.

Device: Xiaomi 15 Ultra, HyperOS 3 / Android 16 (API 36), arm64-v8a. App id
`com.gotrainer.nine`. No Studio, no emulator (no KVM). Tooling is the SDK at
`~/Android/Sdk` plus `adb`. Install runs in the background. Verification
runs through `run-as`, `logcat`, and screen captures.

Key terms used here: cwd means the process working directory. The linker
(`/system/bin/linker64`) is the system program that loads native libraries.
Eigen is the math library that runs the neural nets on the phone CPU. A
visit is one simulated continuation of a game. NPU means the phone's AI
accelerator chip. SNPE is Qualcomm's toolkit for it.

## Current state (2026-10-03)

- Update shipped over WiFi adb (`192.168.0.5:5555`, `5s` push at `12.8 MB/s`).
  USB bulk wedged at `3.5M` of `80M` (shell stayed alive); rebind did not
  help. `adb tcpip 5555` plus `adb connect` bypassed it. Lesson: sick usbip
  channel, not slow link.
- Build needs Java 21 (`~/java21`, Temurin `21.0.12.1`). System Java is 17;
  Paparazzi `2.0.0-alpha05` refuses 17. `local.properties` was missing and
  was recreated (`sdk.dir=/home/acbraith/Android/Sdk`).
- WHR commit failed `compileDebugKotlin` (`WhrAnchors` missing `}`); fixed.
  `:app:testDebugUnitTest` then `assembleDebug` both green.
- Installed `versionCode=1791020091` (`2026-10-03 10:37:31`, was `1790802817`
  from `2026-09-30`). Launch resumes mid-game (White to play, 30k) with
  engine warmup genmove `536ms`. No crash.
- Causal-curve update `versionCode=1791021905` (`2026-10-03 11:05:46`, WiFi
  push `3.1s` at `25.6 MB/s`, md5 matched). Same mid-game resumes, warmup
  `527ms`, no crash. First Stats open will run the one-time curve backfill
  in the background ("Updating rating curve…" until the cache completes).
- 20k-ladder update `versionCode=1791023771` (`2026-10-03 11:36:43`, WiFi
  push `4.0s` at `20.2 MB/s`, md5 matched, zero `R30K` strings in dex).
  Phone was asleep + locked (black screencaps); `KEYCODE_WAKEUP` then swipe
  up dismissed the keyguard. Same mid-game resumes, header now reads 20k
  (was 30k), engine ready `2877ms`, warmup `693ms`, no crash.

## Prior state (2026-09-20)

The engine works on the device. It is a vendored KataGo binary (BadukAI
v1.20.13's copy, KataGo v1.16.0, has `humanSLProfile`), loaded through the
linker with a documented patch recipe (below). No self-build ran. No
remote server exists anymore — on-device GTP is the only engine. GTP is the
text protocol that drives KataGo.

- **Split nets:** b10 searches, b18-human steers. Searching with b18 cost
  ~1.8s per fresh search. b10 wins by ~3.5x at equal visits. Measured:
  model load 2.3s, warmup genmove ~0.5s, bot reply **483ms** (was 1741ms),
  analyze at 25 visits 457ms, raw human policy 141ms, candidates ~640ms
  engine time. Verified end to end: free play with 483ms replies.
- **Steady latency:** cold load 4.3s plus warmup, both in background at
  load. First real candidates query **726ms**. Repeats run 0.2–0.6s.
  Move-to-move feel stays under a second.
- **Budgets:** bot replies run small visit counts, candidates run 150 visits,
  scoring runs 250 visits (~8–15s, once per game, background). The b28
  strong net remains staged but unused for play (too slow on CPU: ~7 board
  rows per second).
- **Threads:** fewer search threads run faster (oversubscription: 8 threads
  trash Eigen's internal pool). Measured per 15-visit query: 8 threads
  ~2.7s, 4 ~2.2s, 2 ~1.7s, 1 ~1.5s. Shipped value is 2. The per-query
  override cannot set thread counts (the engine warns on unknown params).
  `OMP_NUM_THREADS=1` changed nothing (Eigen ignores it for its pool).
- **Candidate breadth:** 19 distinct scored moves measured mid-game. HumanSL
  off plus wide root noise for candidates (below). Symmetry-padded filler is
  filtered. Symmetry padding means KataGo copies one move into mirrored
  slots when the report runs thin — those copies are not real candidates.
- **Winrate graph:** carries the last known value forward, then updates it
  from the bot reply appraisal. No fabricated 50% exists. Verified
  on-device: 23% shown while White thought.

## Install loop (HyperOS)

- The first install needs one on-screen Allow. Updates with `-r` ask
  nothing. But "Don't ask again" plus auto-deny poisons the well
  (`INSTALL_FAILED_USER_RESTRICTED`). Recovery: Dev options → "Install via
  USB" OFF → reboot → ON, plus the per-app block list under that toggle
  (unblock there).
- `versionCode` equals epoch seconds (unique per build). Same-version
  reinstalls stall in MIUI verification. Always verify with `dumpsys
  package` that versionCode changed.
- A streamed install of ~72MB takes ~90s even when healthy (scan plus
  verify).
- Keep the phone awake while plugged: `svc power stayon true`.
- `run-as com.gotrainer.nine` works on debug builds, BUT wildcards do not
  expand through it — use explicit paths, never `files/*.cfg`.

## Engine binary provenance

- `libkatago.so` is BadukAI v1.20.13 APK's copy (KataGo v1.16.0). v1.19 and
  v1.20 lack `humanSLProfile`. v1.23 and later carry a
  `/data/data/net.kir.baduk_ai` path gate. v1.20.13 is the slot. Re-vendor
  with `android/scripts/vendor-engine.sh`.
- Dependency closure (non-system): `libSNPE.so` plus `libtensorflowlite.so`
  (~27MB in `jniLibs/arm64-v8a`). Everything else comes from the system.
  Staging ONLY `libkatago.so` kills the engine instantly with zero output.
  A unit test pins the required set.
- Start recipe: `/system/bin/linker64 <files>/katago/libkatago.so` with
  `LD_LIBRARY_PATH` set, `ADSP_LIBRARY_PATH` set, cwd set to filesDir.
  Staged from the APK (legacy packaging — else the native library directory
  is empty and staging fails loudly).

## The gate, mapped and patched (2026-09-20)

The vendored binary refused every subcommand: process starts, exits 0 with
zero output, for `version`, `gtp`, `analysis`, `runtests`, bogus commands,
and missing models alike. No-args and `--help` printed full usage (2205 B),
so the binary loads and its argument parser runs. Missing libraries,
configs, models, layouts, and argv[0] variants were all ruled out one by
one — every probe stayed silent.

- Root cause: a package-anchored gate before command dispatch. Disassembly
  showed `readlink("/proc/self/cwd")`, a length test, and a byte loop
  against a rodata string. A public report (philippmerz/badukai) verified
  the shape: the path must satisfy a predicate over `net.kir.baduk_ai`.
  Patching it to our longer package name needs length and pointer surgery
  on a foreign closed binary. Verdict stands: do not ship that. The vendored
  path below replaces patching, not thinking.
- The working recipe (`scripts/patch-engine.sh`, with asserts):
  1. rodata `/data/data/net.kir.baduk_ai` (27 bytes, exactly 1 occurrence)
     becomes `/data/user/0/com.gotrainer.` (27 for 27, resolved-cwd prefix).
  2. The exact-length-27 requirement in code becomes NOP, so the short
     chdir-able prefix cwd `/data/user/0` passes the loop.
- Start contract (load-bearing, in code): cwd is `/data/user/0`, argv[0] is
  absolute, HOME is filesDir, log and home-data dirs are absolute under
  filesDir (cwd is not writable). `version` prints. Bogus commands exit 1
  with "Unknown subcommand". Analysis JSON flows. The binary carries
  `PT_INTERP=/system/bin/linker64`, but we still exec through linker64
  explicitly (argv[0] control).
- HumanSL needs the net in the HUMAN slot: `-model b18` alone yields NO
  human policy. `-model b18 -human-model b18` (same file, KataGo dedups it)
  works.
- Staging lesson: mtime gates lie across rebuilds (one incident shipped the
  new binary while the gate kept the stale one). The `ENGINE_PATCH_REV`
  marker file forces restage. Bump it for ANY engine or config change
  (sizes often look identical).

## Why not BadukAI-fast

BadukAI feels under a second with 55–720 n/s tables because it pairs
per-chipset optimized nets with hardware acceleration (SNPE, NPU use for
18b and larger. 10b runs on CPU). Our vendored binary is Eigen-CPU-only
(no OpenCL strings in it). CPU parity path: tiny budgets plus small nets.
NPU parity path (future): newer BadukAI binary plus Snapdragon optimized
net plus SNPE NPU config.

## Model downloads

- The human net MUST come from the KataGo v1.15.0 release file
  `b18c384nbt-humanv0.bin.gz` (99,066,230 B — the same file KaTrain uses).
  The katagotraining `/modelsextra/` path (no underscore) returns 403 to all
  automated clients. Fixed in both `ModelManager` and the server script.
- The strong net on `/models/kata1/` (271,440,852 B) serves fine (200 plus
  `accept-ranges: bytes`).
- The downloader resumes with HTTP Range, retries 6 times with exponential
  backoff, enforces stall timeouts, renames atomically, and verifies sizes.
  It emits progress at every attempt start so taps feel instant. Covered by
  `DownloaderTest` (local server: fresh, resume, flaky cases).
- `android.util.Log` crashes JVM unit tests, so logging flows through an
  injectable sink (silent in tests).

## GTP text format (KataGo v1.16 source, cpp/command/gtp.cpp — NOT JSON)

- `kata-analyze` and `kata-genmove_analyze` print ONE text line per report:
  `info move E5 visits 10 ... winrate ... scoreLead ... prior ... order 0
  pv ...` (plus more `info` segments), then `rootInfo visits N winrate W
  scoreLead S ...`. The move pool is uncapped by default; request `rootInfo
  true` explicitly (default is off).
- GTP text carries NO human policy (only the main-net `prior`). The TRUE
  human signal is `kata-raw-human-nn 0` (multi-line response,
  blank-terminated): the human net's raw policy grid under the current
  profile. Candidates pair analyze moves (real scores for gap math) with
  the human policy from that grid.
- `kata-genmove_analyze <color> <interval> rootInfo true` plays and
  analyzes. `= move` is the played move. Interval 5 keeps the graph's
  rootInfo at play budgets near 10 visits.
- v1.16 facts, all observed on-device: GTP mode REQUIRES `resignThreshold`
  in cfg (analysis mode does not) — without it the engine dies exit 134.
  `kata-genmove_analyze` answers with a bare `=` header FIRST, then analyze
  text, then the bare line `play <move>` (there is no `= <move>`). Plain
  `kata-genmove` is NOT a command. Searches with `-human-model` but NO
  `humanSLProfile` die silently (exit 1) — always arm a profile before any
  search (default rank_10k at startup). v1.16 `kata-raw-human-nn` takes ONLY
  the symmetry argument. The color-first form is newer and errors. `quit`
  answers `= ` (every command gets exactly one line).

## Streaming-response gotcha (cost an hour)

`kata-analyze` prints its `= ` header at stream start with NO terminator.
The analysis ends only when the next command arrives, and that termination
emits a blank line. The raw-policy collector treated that stray blank as
its own payload's end ("0 bytes in 7ms", then "no policy grid"). Fix: while
collecting a raw multi-line response, IGNORE blank lines while the buffer is
empty. Also drain `responses` after each analyze collect, so the next command
cannot eat the stale header.

## Asset gotcha (cost another hour)

aapt2 (the Android asset packer) SILENTLY decompresses `.gz` assets and
strips the extension: shipping `assets/models/b10.bin.gz` produced APK entry
`assets/models/b10.bin` (12,003,218 B uncompressed), so opening
`models/b10.bin.gz` threw FileNotFoundException and setup showed a bare
failure. Rule: bundle models under a plain name (`.bin`), never `.gz`. A
unit test pins the surviving asset name.

## Candidate breadth fix (2026-09-20)

HumanSL's play params focus the search: measured on-device, even after 1236
root visits only **4 DISTINCT root moves** held visits. The old 3-move pools
starved the selector. Fix (mirrors BadukAI, which disables HumanSL for its
analysis panel): get the raw human policy FIRST (needs the profile armed),
then unset the profile plus BadukAI's DEACTIVATED param column plus
`analysisWideRootNoise = 0.20` (KataGo's documented breadth knob), collect
to ~150 visits — **19 distinct scored moves** measured mid-game (empty board
~6), candidates query ~0.8s. Then restore the play profile. Symmetry-padded
filler gets filtered (kept only as fallback if the real pool runs smaller
than n). Logs "candidates pool: X real / Y total" every query.

## What left the app (2026-09-20)

The Engine selector, server URL, RemoteEngine, INTERNET permission, and the
cleartext-localhost network config are gone. On-device GTP is the only
engine. The PC `server/` remains as a dev tool, unreferenced by the app.

## See also

- [KATAGO_INTEGRATION.md](../../docs/KATAGO_INTEGRATION.md) — the curated
  engine picture (this file holds the session evidence behind it)
- [ENGINE_PLAN.md](ENGINE_PLAN.md) — the brainstorm this work closed out

## 2026-10-03 — tournament FP2 anchors land (CURVE_VERSION 3)
- Build md5 59069f9cd1f42f801e6046f13f83ae78, install -r Success, no crash.
- `WhrAnchors` now FP2(3,3) no-18k fit, 5k=1500 (20k 1031.67 … 9d 2066.19).
- Stats on 209-game history: 10k ±1, curve backfilled under v3, per-game
  points render, no stuck "Updating" state. (Two black screencaps during
  verify were a resume-transition artifact, not a crash — app renders fine.)

## Stats x-gridlines (games + time)
- Helpers `gamesXTicks` (1/2/5 steps, absolute numbers) + `timeXTicks`
  (midnights/Mondays/month-starts by span, <1.5d none); 8 unit tests.
- Phone: Games/All shows 0/50/100/150/200; Time/All shows daily M/d lines
  with collision-skipped labels. No crash. (Black screencaps mid-session
  were display-off captures, not app state — app renders throughout.)
