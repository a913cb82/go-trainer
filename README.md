# Go 9×9 Teaching Game — KataGo Human SL

> **Goal:** Browser app (PWA) where the player plays 9×9 Go against KataGo's *human supervised-learning* (HumanSL) model at a chosen rank, and on each of their turns is shown **n candidate moves to choose from** — designed to accelerate learning. Easily portable to Android via the same web codebase.

`AGENTS.md` is a symlink to this file.

## Core Concept

1. **Player** picks a difficulty rank (e.g. 15k → 3d) and a hint mode (e.g. “5 choices”).
2. **Opponent** is KataGo HumanSL at that rank — plays plausible human moves, not superhuman.
3. **On the player's turn** the board shows n markers (A–E). Player taps one to play it. No win-rate shown until *after* the choice, then immediate feedback (what was best, why the others were worse).
4. Board is **9×9 only** for now — fast games, tight reading, ideal for learning.

Why 9×9 + multiple choice? Free play teaches shape and tactics quickly, but beginners freeze on an empty board. Curated choices turn every move into a focused reading exercise while keeping agency (you still choose).

## Tech Stack (portable to Android)

**Frontend — PWA wrapped for Android via Capacitor:**
- **Vite + TypeScript + React** + `vite-plugin-pwa` → installable, same `dist/` in Capacitor WebView
- **Board UI:** custom SVG goban (`app/src/components/Board/GobanView.tsx`) — A–E candidate markers, rank-graduated feedback halo, last-move marker. (Shudan was dropped: it's Preact and crashes under React 19.)
- **Rules:** `@sabaki/go-board` 1.4.3 (MIT) for capture/ko/suicide legality
- **SGF:** `@sabaki/sgf` 3.5.0 (MIT) for parse/stringify
- **State:** Zustand

**Backend — KataGo analysis server (Node):**
- Spawns `katago analysis`, loads **HumanSL** (`b18c384nbt-humanv0`) + **strong** nets; endpoints `POST /candidates|/genmove|/evaluate`
- No npm wrapper — speaks Analysis JSON directly (`@sabaki/gtp` is GTP-only, skip)
- Ranks candidates by `humanPolicy` and samples White's moves proportional to it (KataGo's recommended HumanSL imitation procedure)
- GPU (CUDA) for 9×9 at 400+ visits; models & the `katago` binary are gitignored

See [`docs/TECH_STACK.md`](docs/TECH_STACK.md) for rationale & alternatives, [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for diagram + API, [`docs/KATAGO_INTEGRATION.md`](docs/KATAGO_INTEGRATION.md) for engine details.

## Learning Design — Which n Moves to Show?

This is the product's core. Naive “top 5 by HumanSL” is plausible but doesn't teach contrast. We propose a **hybrid good-vs-tempting-bad** strategy (configurable).

**Default: `n=5` → 3 Good + 2 Tempting Bad (shuffled, unlabeled)**
- **3 Good:** intersection of HumanSL@rank top-8 and Strong KataGo top-10 (win-rate drop ≤ 2%). These are moves a human at your level *should* consider and that are objectively fine.
- **2 Tempting Bad:** moves where HumanSL@rank policy is high (top-8) but Strong eval is ≥ 4–6% win-rate worse than best. These are classic human biases / shape mistakes at that rank. Learning to *reject* them is the lesson.

Other modes (user-selectable):
- **Purist:** Top-5 HumanSL@rank only — “play like a human at this rank, pick the best of them.”
- **Find the Tesuji:** 1 brilliant strong move + 4 plausible human moves — forces spotting the key point.
- **Blunder Check:** 4 good + 1 obvious blunder — train to avoid the single bad shape.
- **Rank-graduated spread:** at 15k the 5 moves span ~8% win-rate; at dan level they span ~2% — difficulty scales naturally.

**Feedback after pick (essential):** reveal ordering by strong win-rate, HumanSL prior %, short tag (“keeps sente”, “overconcentrated”, “atari miss”) from score/ownership + optional LLM-generated one-liner. Win-rate graph over time.

Full analysis in [`docs/LEARNING_DESIGN.md`](docs/LEARNING_DESIGN.md).

## Project Structure

```
go_game/
├── README.md                    # this file (AGENTS.md -> README.md)
├── PLAN.md                      # phased milestone plan
├── docs/
│   ├── TECH_STACK.md            # stack choice & portability notes
│   ├── ARCHITECTURE.md          # system diagram + API contracts
│   ├── LEARNING_DESIGN.md       # move-selection strategies & feedback UX
│   └── KATAGO_INTEGRATION.md    # KataGo models, GTP/analysis, deployment
├── app/                         # frontend PWA (Vite + TS + React)
│   ├── src/components/Board/    # Shudan wrapper + Marker overlay
│   ├── src/lib/goban.ts         # wraps @sabaki/go-board (legality)
│   ├── src/lib/sgf.ts           # wraps @sabaki/sgf
│   ├── src/store/               # Zustand state
│   └── public/
├── server/                      # KataGo bridge (Node/Python)
│   ├── src/katago.ts            # Analysis JSON over stdin/stdout
│   └── models/                  # (gitignored) .bin.gz networks
└── sgf/                         # sample SGFs / test fixtures
```

## Roadmap

1. **M0 — Scaffold:** Vite PWA + 9×9 SVG board, local rule engine, tap-to-play vs random.
2. **M1 — KataGo server:** Dockerized `katago analysis` + HumanSL model, `/genmove` & `/candidates` endpoints, difficulty mapping.
3. **M2 — Choice UI:** n-move markers, shuffle, feedback panel (win-rate bar, ordering), rank selector.
4. **M3 — Learning polish:** good-vs-bad strategy tuning, win-rate graph, SGF export, pass/resign/scoring.
5. **M4 — Android:** `vite-plugin-pwa` installable + Capacitor wrapper, Play Store build, touch Polish.
6. **M5 — Stretch:** WASM offline, review mode, spaced-repetition puzzles from mistakes.

See [`PLAN.md`](PLAN.md) for detailed tasks.

## Setup from scratch

The KataGo binary and the model weights are large and are **gitignored** (see `.gitignore`); they are never committed. Follow the steps below after cloning.

### 1. Install frontend & server deps

```bash
cd app    && npm install
cd ../server && npm install
```

### 2. Fetch the KataGo binary, models, and runtime libs

Run the setup script — it downloads the CUDA KataGo binary to `server/katago`, the HumanSL + strong nets to `server/models/`, bundles the binary's non-default shared libs into `server/libs/`, and writes a 9×9-tuned `server/config/analysis.cfg`. All of these are gitignored.

```bash
./server/scripts/download-models.sh
```

#### What each step does
- **Binary** (`server/katago`): the CUDA 12.1 build. If you don't have CUDA/cuDNN, use an **Eigen (CPU)** build instead — fine for 9×9 up to a few hundred visits. Without any binary, the server runs in **mock** mode (no KataGo).
- **Models**: `server/models/b18c384nbt-humanv0.bin.gz` (HumanSL) and `server/models/strong.bin.gz` (strong net for the good-vs-tempting-bad comparison).
- **Runtime libs** (`server/libs/`): the CUDA binary was built on Ubuntu 20.04 and needs `libssl.so.1.1` and `libzip.so.5`, which **Ubuntu 22.04+ does not ship by default** (22.04 has `libssl.so.3`). The script extracts those `.so` files into `server/libs/`; the server adds that dir to `LD_LIBRARY_PATH` automatically. It also needs `libcublas.so.12`/`libcudnn.so.8` from your CUDA install.

> **If you'd rather not bundle libs**, build KataGo from source (KataGo's own docs recommend this when prebuilt binaries hit `libssl`/`libzip` version mismatches) or install `libssl1.1`/`libzip4` via a package that provides them.

### 3. Run in real mode

```bash
cd server
export KATAGO_MODE=real KATAGO_BIN=./katago
npm run dev   # http://localhost:3001
```

`server/models/*.bin.gz` and `server/libs/` are resolved relative to the server module, so you don't need absolute `/home/...` paths or to set `KATAGO_MODEL`/`KATAGO_HUMAN_MODEL` manually. If your CUDA libraries aren't under `/usr/local/cuda/targets/x86_64-linux/lib`, set `LD_LIBRARY_PATH` to include them.

### 4. Run the app

```bash
cd app && npm run dev   # http://localhost:5173  (proxies /api -> :3001)
```

### Docker

`docker compose up` builds the server. The Dockerfile defaults to **mock** mode (`KATAGO_MODE=mock`) because the real binary/models are large and host-specific; mount them at runtime to use `real`:

```bash
KATAGO_MODE=real docker compose up
```

### Verify

```bash
curl http://localhost:3001/health   # {"ok":true,"mode":"real",...}
curl -X POST http://localhost:3001/candidates -H 'Content-Type: application/json' \
  -d '{"board":[[0,0,0,0,0,0,0,0,0],...],"toMove":"B","rank":"3d","n":5,"strategy":"good-vs-tempting","maxVisits":400}'
```

## Contributing

PRs welcome. Keep `AGENTS.md` symlinked to `README.md` — single source of truth.

## License

MIT
