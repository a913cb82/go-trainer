# Tech Stack

Constraint: browser first, Android via same codebase.

## Frontend: PWA + Capacitor

- **Vite + TypeScript + React** + `vite-plugin-pwa` → installable, same `dist/` in Capacitor (`npx cap add android`)
- **State:** Zustand + TanStack Query

## Reuse — Don't Rewrite

| Need | Library | Verdict |
|------|---------|---------|
| Rules (capture/ko/suicide) | `@sabaki/go-board` 1.4.3 MIT | **Reuse** — `Board.makeMove()` handles all. Alt `online-go/goban` 8.3.226 Apache-2.0 (heavier, adds scoring) |
| Board UI (stones, A–E markers) | `@sabaki/shudan` 1.8.0 MIT (Preact) | **Reuse** — `signMap`/`paintMap`/`ghostStoneMap`; React via `preact/compat`. Alt `jgoboard` 5.0.4 CC-BY-NC-4.0 (lighter) |
| SGF parse/stringify | `@sabaki/sgf` 3.5.0 MIT | **Reuse** — `sgf.parse`/`stringify`, 9×9 + variations |

```ts
import {Board} from '@sabaki/go-board'
let b = new Board(9); b = b.makeMove(3,3,1)
import * as sgf from '@sabaki/sgf'; sgf.parse(str)
import {Goban} from '@sabaki/shudan'; <Goban vertexSize={9} signMap={m} paintMap={p} />
```

## Backend

- **Node or Python** spawning `katago analysis`. No npm KataGo lib — speak Analysis JSON directly.
- `@sabaki/gtp` 3.2.0 is GTP-only, skip for analysis engine.
- Two nets: HumanSL + strong for `P_h`/`W_s`. Dockerized, `models/*.bin.gz` gitignored.

## KataGo Delivery

- **Server first (M1).** WASM (`@multi-game-engines/adapter-katago` etc) → M5, ~95MB, deferred.

## Tooling

Vitest + Playwright, ESLint/Prettier, Lighthouse CI.

## Open

- React vs Svelte (either works, decide before M0)
