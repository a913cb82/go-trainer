# Android on-device notes (living doc — update after every phone session)

Device: Xiaomi 15 Ultra (25010PN30G, `c3c8b7bf`), HyperOS 3 / Android 16, API 36,
arm64-v8a. App id `com.gotrainer.nine`. No Studio, no emulator (no KVM):
`~/Android/Sdk` + `adb`, install in background, verify via `run-as`/`logcat`/screencap.

## Install loop (HyperOS)

- First install needs one on-screen Allow; `-r` updates are prompt-free — but a
  "Don't ask again" + auto-deny poisons the well (`INSTALL_FAILED_USER_RESTRICTED`).
  Recovery: Dev options → "Install via USB" OFF → reboot → ON, plus the per-app
  block list under that toggle (unblock there).
- `versionCode` = epoch seconds (unique per build). Same-version reinstalls stall
  in MIUI verification; always confirm `dumpsys package` versionCode changed.
- Streamed install of ~72MB takes ~90s even when healthy (scan + verify).
- Keep the phone awake: `svc power stayon true` while plugged.
- `run-as com.gotrainer.nine` works (debug build) BUT globs don't expand through
  it — use explicit paths, never `files/*.cfg`.

## Engine binary provenance (important)

- `libkatago.so` = BadukAI v1.20.13 APK's copy (KataGo v1.16.0, has
  `humanSLProfile`, verified NO `/data/data` string at the time — see below).
  v1.19/v1.20 lack `humanSLProfile`; v1.23+ has a `/data/data/net.kir.baduk_ai`
  path check. v1.20.13 is the slot. Re-vendor: `android/scripts/vendor-engine.sh`.
- Deps closure (DT_NEEDED, non-system): `libSNPE.so` + `libtensorflowlite.so`
  (~27MB total in `jniLibs/arm64-v8a`). Everything else is system-provided.
  Learned the hard way: staging ONLY libkatago.so kills the engine instantly
  with zero output ("Stream closed" in queries). Pinned by
  `StagingTest.required libs include the linker companions`.
- Launch recipe: `/system/bin/linker64 <files>/katago/libkatago.so analysis …`,
  `LD_LIBRARY_PATH=<katagoDir>:<nativeLibDir>:/system/lib64:/vendor/lib64`,
  `ADSP_LIBRARY_PATH` set, cwd = filesDir. Staged from APK (`useLegacyPackaging`,
  else `nativeLibraryDir` is empty and staging fails loudly).

## OPEN PROBLEM (2026-09-20): engine exits 0 silently

- `linker64 libkatago.so` with NO args prints full usage (so the binary runs).
- ANY subcommand (`version`, `gtp`, `analysis`, even with a bogus model path)
  exits 0 with zero stdout/stderr. Not a model/config issue.
- Suspect: BadukAI package/path gate before dispatch (rodata holds
  `/proc/self/cwd`, `/data/app/`, `net.kir.baduk_ai`; `main` returns 0 when a
  lookup yields null; a `readlink`+`strlen`+`memcmp len 0x17=23` sequence sits in
  the dispatch path). Probes so far (cwd+layout matrix incl. exact BadukAI
  layout files/+hexagon, in-APK execution) all exit 0 silent.
- Next: identify the 23-byte constant / gate predicate via disassembly
  (capstone+pyelftools installed; PLT map: getcwd=0x28400, strcmp=0x285e0,
  exit=0x28640, readlink=0x289c0 — note AArch64 PLT0 is 32B, stubs are +32+16*i),
  then same-length binary patch or drop-in replacement. Nuclear option: Eigen
  NDK self-build (clean MIT, no gate) — needs NDK download + hours on 3.8GB RAM.
- Diagnostics added for this: unrouted engine stdout is logged (`[katago-out]`),
  spawn logs cmd/env/file sizes, 800ms post-spawn liveness check throws with
  exit code, query failures attach `KataGo process dead (exit=…)`.

## Ruled out

- 16K pages: `getconf PAGESIZE` = 4096 on-device (max 16384 supported). The
  binary loads, links deps and prints usage — loader-level breakage would be loud.
- Missing deps: fixed (Staging closure); linker errors are loud when they happen.
- Bad config/model: no-config, missing-model and model-free `runtests` runs are
  all equally silent — the failure is before any of that matters.
- argv[0] symlink, cwd/layout matrix (files/, hexagon subdir, exact BadukAI
  layout, in-APK execution): all silent. Not layout alone.
- Naive string patch (`/net.kir.baduk_ai-` → `/com.gotrainer.nin`, byte-exact):
  still silent in both layouts. Predicate is structural, not a lone substring.

## Model downloads

- Human net MUST come from
  `https://github.com/lightvector/KataGo/releases/download/v1.15.0/b18c384nbt-humanv0.bin.gz`
  (99,066,230 B — same file KaTrain uses). The katagotraining
  `/modelsextra/` (no underscore) path 403s all automated clients; fixed in both
  `ModelManager.HUMAN` and `server/scripts/download-models.sh`.
- Strong net (`kata1-b28c512nbt…`, 271,440,852 B) on `/models/kata1/` is fine
  (200 + `accept-ranges: bytes`).
- Downloader: Range resume, 6 attempts exp backoff, stall timeouts, atomic
  rename, size verification. Emits progress at every attempt start so taps feel
  instant. Covered by `DownloaderTest` (local HttpServer: fresh/resume/flaky).
- `android.util.Log` crashes JVM unit tests → `ModelManager.EngineLogger`
  injectable sink (SilentLogger in tests). `testOptions.unitTests.isReturnDefaultValues`
  also set.

## Decision (2026-09-20, from web research) — build our own binary

- philippmerz/badukai (the only other `linker64` user) documents the BadukAI
  lib's gate: path check `/data/data/net.kir.baduk_ai` + memcmp len 27, placed
  after the no-args usage branch → exactly our silent exit-0 for every subcommand.
- Patching it to our package needs a LONGER string (27→32 B) plus length/pointer
  surgery on a foreign closed binary. Verdict: don't ship that. Vendored path CLOSED.
- Linker research (AOSP sources): `linker64 <lib> args` forwards argv correctly
  (argv[0] = lib path) and jumps to e_entry — our invocation was never the bug.
- Endgame: own NDK build, Eigen CPU backend, per acristescu/PaooGo branch
  `251101a_humansl` (proven on Pixel 8 Pro, KataGo v1.16.3, humanSL): build with
  `add_executable` but `OUTPUT_NAME libkatago / SUFFIX .so` so Gradle packages it
  into jniLibs, then launch DIRECTLY via ProcessBuilder (no linker64) after
  staging to filesDir + chmod. JNI in-process (ChuiShui233, karino2) is the
  backup if exec-from-app-context is blocked on API 36.
- Until then: remote engine (PC CUDA) is the playable path and the oracle for
  cross-checking on-device results later.

## The gate, fully mapped (2026-09-20, capstone on BadukAI v1.20.13 binary)

- The check is real: `readlink("/proc/self/cwd")` → length must satisfy
  `min(len,27)==27` (i.e. len ≥ 27) → byte loop compares cwd[1..len-1] against
  the rodata check string — which lives in a malloc'd 32B COPY NUL-terminated
  at [27]. Consequence: only a cwd of EXACTLY 27 chars can ever pass, and only
  if it prefix-matches. (philippmerz/badukai's hexagon-cwd recipe CANNOT pass
  its own gate — resolved cwd is 40+ chars. Their app never ran; don't copy it.)
- Our patches (`scripts/patch-engine.sh`, asserts included):
  1. rodata `/data/data/net.kir.baduk_ai` (27B, exactly 1 occurrence)
     → `/data/user/0/com.gotrainer.` (27-for-27; resolved-cwd prefix).
  2. code `0x37d9a0: b.ne fail` (exact-length-27 requirement) → NOP, so the
     short chdir-able prefix cwd `/data/user/0` passes the loop.
- Launch contract (in `KataGoAnalysisEngine`, load-bearing): cwd=`/data/user/0`,
  argv[0] absolute `/data/user/0/...`, HOME=filesDir, logDir+homeDataDir absolute
  under filesDir (cwd is not writable). `version` prints, bogus cmds exit 1 with
  "Unknown subcommand", analysis JSON flows. `PT_INTERP=/system/bin/linker64` is
  present but we still exec via linker64 explicitly (argv[0] control).
- HumanSL needs the net in the HUMAN slot: `-model b18` alone yields NO
  humanPrior; `-model b18 -human-model b18` (same file, KataGo dedups it) works.

## On-device performance (Xiaomi 15 Ultra, Eigen CPU, 2026-09-20)

- b28 strong: ~7 rows/s → 15-visit ≈ 5s+, 500-visit scoring ≈ 90s. TOO SLOW.
- b18 human: cold load ~4-6s (once, pre-warmed at launch), 15-visit ≈ 1-2s warm,
  50-visit ≈ 2-3s. Judgment: playable for candidates/genmove; scoring cut to
  250 visits (~10-15s, once per game).
- Shipped recipe: everything on b18 (`-model`+`-human-model` same file);
  score() carries mandatory `humanSLProfile=rank_1d` (human nets FATAL without
  any profile). b28 stays staged for future strong-engine work. Server (CUDA
  b28) remains the one-tap cross-check via the Engine selector.

## Performance, measured (2026-09-20, Xiaomi 15 Ultra, b18 Eigen CPU)

- Cold model load: ~4.3s once (pre-warmed at launch). Warm query floor: ~1.5s
  per 15-visit candidates query — fixed per-query overhead dominates, not visits.
- Thread lesson: FEWER search threads are faster (oversubscription — 8 search
  threads × Eigen's internal pool thrash). Measured per 15-visit query:
  8T~2.7s, 4T~2.2s, 2T~1.7s, 1T~1.5s. Shipped `numSearchThreadsPerAnalysisThread
  = 2` (stock config comments agree: keep it small). `numSearchThreads*` is NOT
  settable via per-query overrideSettings (engine warns "Unknown config params").
- OMP_NUM_THREADS=1: no change (Eigen doesn't use OMP for its pool).
- The BadukAI gap, explained (aki65.github.io): their <1s feel and 55-720 n/s
  table come from per-chipset OPTIMIZED nets + NPU/GPU HW acceleration (SNPE,
  `useSpecificNpuAccelerator`) — 10b runs on CPU, 18b+ on accelerators. Our
  vendored binary is Eigen-CPU-only (no OpenCL strings in it). CPU parity path:
  tiny budgets + small nets. NPU parity path (future): newer BadukAI binary +
  Snapdragon optimized net + SNPE NPU config (gate RE recipe now takes minutes).
- Staging lesson: mtime-gates lie across rebuilds (02:19 incident — APK had the
  new binary, gate kept the stale one). `ENGINE_PATCH_REV` marker file forces
  restage; bump it for ANY engine/cfg change (sizes often identical).

## Latency, final (2026-09-20, in-app, b18, 2 threads)

- Cold load 4.3s + 15-visit warmup 3.9s, both in background at launch.
- Steady state: first real candidates query **726ms**; probes show repeats
  0.2-0.6s. Move-to-move feel: <1s. (From 30s+ at the start of the night.)
- The three costs, in order found:
  1. `includePolicy=true`: +2.6s/query on Eigen (measured A/B). Dropped —
     the HumanSL profile already puts humanPrior on every moveInfo.
  2. First-15-visit-search one-time cost ~3s (NN cache/thread first-touch; a
     1-visit warmup does NOT cover it). Warmup is now shaped exactly like a
     real candidates call and runs at launch.
  3. Thread oversubscription (8T slower than 2T, see above).
- `reportAnalysisWinratesAs:BLACK` per-query override costs nothing (A/B 0.2s)
  — kept (correctness: KaTrain-style BLACK perspective).
- Estimates from ~30 v/s steady: evaluate(50v) ~2s background, score(250v)
  ~8s once per game. Both unmeasured in-app — verify on a full game.

## GTP text format (KataGo source, cpp/command/gtp.cpp — NOT JSON)
- `kata-analyze`/`kata-genmove_analyze` print ONE text line per report:
  `info move E5 visits 10 ... winrate ... scoreLead ... prior ... order 0 pv ...`
  (+ more `info` segments) + `rootInfo visits N winrate W scoreLead S ...`.
-(move pool uncapped by default; request `rootInfo true` explicitly — default off.)
- There is NO humanPrior in GTP text (only main-net `prior`). TRUE human signal:
  `kata-raw-human-nn <C> 0` (multi-line `=` response, blank-terminated) returns
  the human net's raw policy grid evaluated under the current humanSLProfile.
  Candidates = analyze moves (real scores for gap math) + humanPrior from grid.
- `kata-genmove_analyze <C> <interval> rootInfo true` plays + analyzes; `= move`
  is the played move. interval=5 (BadukAI uses 25; we want the graph's rootInfo
  at play budgets ~10 visits).

## GTP v1.16 protocol facts (all observed on-device, 2026-09-20)
- GTP mode REQUIRES `resignThreshold` in cfg (analysis mode doesn't) — dies
  exit=134 `IOError: Could not find key` without it.
- `kata-genmove_analyze` answers with a bare `=` header FIRST, analyze text,
  then the bare line `play <move>` (v1.16 source: `response = "play " + move`).
  There is no `= <move>`. Plain `kata-genmove` is NOT a command (`? unknown`).
- Searches with `-human-model` but NO `humanSLProfile` die silently (exit 1):
  always arm a profile before any search (we default rank_10k at startup).
- v1.16 `kata-raw-human-nn` takes ONLY the symmetry (`kata-raw-human-nn 0`);
  the color-first form is newer and errors.
- `quit` answers `= ` (queue accounting: every command gets exactly one line).

## Split-net play: the 1.8s -> 0.5s fix (2026-09-20, THE big one)
BadukAI plays with TWO nets, and one fact from the user cracked it:
- main/search net = **10b** (`10b.bin.gz` bundled in their APK), human-SL net = b18.
We were searching with b18 for everything: ~1.8s per FRESH search (b18 Eigen).
With b10 searching + b18 steering:
- model load 2.3s, warmup genmove ~0.5s, bot reply **483ms** (was 1741ms)
- analyze 25 visits 457ms, raw human policy 141ms, candidates ~640ms engine time
The user's moves invalidate the small 10-visit tree, so every reply is a fresh
search — per-search cost is what matters, and b10 wins by ~3.5x at equal visits.
Verified end-to-end in-app: free play 483ms replies; 5-choice pills render.

## aapt2 asset gotcha (cost an hour)
aapt2 SILENTLY decompresses `.gz` assets and strips the extension: shipping
`assets/models/b10.bin.gz` produced APK entry `assets/models/b10.bin`
(12,003,218B uncompressed), so `assets.open("models/b10.bin.gz")` threw
FileNotFoundException and the setup screen showed a bare "models/b10.bin.gz"
failure. Rule: bundle models under a plain name (`.bin`), never `.gz`.
Pinned by unit test `bundled net asset name survives aapt2 packaging rules`.

## GTP streaming-response gotcha (cost another)
`kata-analyze` prints its `= ` header at stream start with NO terminator; the
analysis ends only when the next command arrives, and that termination emits a
blank line. The raw-nn collector treated that stray blank as its own payload's
end -> "0 bytes in 7ms", then "no policy grid". Fix: while collecting a raw
multi-line response, IGNORE blank lines while the buffer is empty. Also drain
`responses` after collectAnalyze so the next command can't eat the stale header.
