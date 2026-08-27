# go-trainer

> 9×9 Go vs KataGo HumanSL — pick from *n* moves, get points-based feedback. Browser PWA, portable to Android via Capacitor.

`AGENTS.md` → `README.md`.

## How it plays
Pick rank (15k→3d) and strategy (`n=5` good-vs-tempting-bad). Board shows *n* faint options; tap one. Values revealed after — halo + predicted points/tiny winrate, rank-graduated colors. 9×9 only.

## Stack
- **App:** Vite + React + TS + `vite-plugin-pwa`, custom SVG board, `@sabaki/go-board`/`sgf`, Zustand
- **Server:** Fastify → `katago analysis` (HumanSL `b18c384nbt-humanv0` + strong net), `rank_*` profiles, `includePolicy` → sample by `humanPolicy`

Docs: [`TECH_STACK`](docs/TECH_STACK.md) · [`ARCHITECTURE`](docs/ARCHITECTURE.md) · [`LEARNING_DESIGN`](docs/LEARNING_DESIGN.md) · [`KATAGO_INTEGRATION`](docs/KATAGO_INTEGRATION.md) · [`PLAN`](docs/PLAN.md)

## Setup

Binary/models gitignored. One-time:

```bash
cd app && npm i && cd ../server && npm i
./server/scripts/download-models.sh  # katago→server/katago, nets→server/models/, libs→server/libs/, cfg→server/config/analysis.cfg
```

Run:

```bash
cd server && KATAGO_MODE=real npm run dev  # :3001 (mock without binary)
cd app && npm run dev                       # :5173 proxies /api → :3001
curl http://localhost:3001/health
```

CUDA needs `libcublas12`/`libcudnn8`; the script bundles `libssl1.1`/`libzip5` to `server/libs/` (auto-added to `LD_LIBRARY_PATH`). Without CUDA use Eigen CPU build, or build KataGo from source.

## Structure

```
go-trainer/
├─ app/    # Vite PWA
├─ server/ # Fastify KataGo bridge
├─ docs/   # TECH_STACK, ARCHITECTURE, LEARNING_DESIGN, KATAGO_INTEGRATION, PLAN
└─ sgf/
```

## Roadmap
M0 Scaffold → M1 KataGo server → M2 Choice UI → M3 Polish → M4 Android → M5 WASM/puzzles. See [`docs/PLAN.md`](docs/PLAN.md).

MIT
