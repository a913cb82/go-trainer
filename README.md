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
- **Board UI:** `@sabaki/shudan` 1.8.0 (Preact goban, `signMap`/`paintMap`/`ghostStoneMap` for A–E markers) — reuse, don't rewrite
- **Rules:** `@sabaki/go-board` 1.4.3 (MIT) for capture/ko/suicide legality; server is truth (`online-go/goban` 8.3.226 is heavier alt with scoring)
- **SGF:** `@sabaki/sgf` 3.5.0 (MIT) for parse/stringify
- **State:** Zustand + TanStack Query

**Backend — KataGo analysis server (Node or Python):**
- Spawns `katago analysis`, loads **HumanSL** + **strong** nets; endpoints `POST /candidates|/genmove|/evaluate`
- No npm wrapper — speak Analysis JSON directly (`@sabaki/gtp` is GTP-only, skip); WASM offline deferred to M5
- CPU fine for 9×9 at 50–200 visits; Dockerized, models gitignored

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

## Running (planned)

```bash
# frontend
cd app && npm install && npm run dev   # http://localhost:5173

# server (needs katago binary + model in server/models/)
cd server && npm install && npm run dev # http://localhost:3001

# docker (engine + models)
docker compose up
```

## Contributing

PRs welcome. Keep `AGENTS.md` symlinked to `README.md` — single source of truth.

## License

MIT
