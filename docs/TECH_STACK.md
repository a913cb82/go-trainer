# Tech Stack

Constraint: browser app first, portable to Android with no second codebase.

## Frontend: PWA + Capacitor

- **Vite + TypeScript + React** (SvelteKit is the alternative — either works). Vite gives fast dev + `vite-plugin-pwa`.
- **PWA** via `vite-plugin-pwa` → installable on Android Chrome, offline shell cached.
- **Board:** custom SVG (9×9 is ~20 lines). Full control over A–E markers, ownership overlay. Client legality via tiny `goban.ts` (or `sabaki/go-board`); server is source of truth.
- **State:** Zustand + TanStack Query (caching for `/candidates`).

## Backend

- **Node or Python** service spawning `katago analysis`. Pick one.
- Two networks: **HumanSL** (opponent + candidate policy `P_h`) and **strong** (ground-truth win-rate `W_s`).
- Endpoints: `POST /candidates`, `POST /genmove`, `POST /evaluate`. Dockerized; `models/*.bin.gz` gitignored, fetched by `scripts/download-models.sh`.
- 9×9 at 50–200 visits runs fine on CPU. GPU optional.

## KataGo Delivery

- **Server first (M1).** Small frontend, easy model updates.
- **WASM later (M5, feature-flagged).** Offline but 20–40 MB download, slower on mid Android.

## Android

Same `dist/`:

1. **PWA alone:** Add to Home Screen — zero store work, good for beta.
2. **Play Store:** Capacitor wrapper (`npx cap add android && npx cap sync`) or TWA/Bubblewrap. No code fork.

## Tooling

Vitest + Playwright, ESLint/Prettier, GitHub Actions + Lighthouse CI.

## Not Now

No iOS, no auth (localStorage), no multiplayer.

## Open

- React vs Svelte — decide before M0.
- Strong vs shared network for `W_s`.
