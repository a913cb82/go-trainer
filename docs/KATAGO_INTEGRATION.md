# KataGo Integration

KataGo is a free AI program that plays Go. This app runs it on the phone.

## On-device engine (shipped path)

The engine speaks GTP (`KataGoGtpEngine`). GTP is the text protocol that
drives KataGo. No server runs. No WASM ships. No mocks exist — KataGo is
required, and the app says so when files are missing.

- **Start:** the app runs `/system/bin/linker64` on the bundled
  `libkatago.so` with library paths wired. The first use spawns the process
  and loads the models. One throwaway move warms the neural-net cache. Then
  one persistent board serves all queries for the game.
- **Models:** split nets (two brain files, one job each). The small search
  net ships inside the app. The human steering net downloads once on the
  setup screen, with resume and retry. File names and sizes live in
  `ModelManager`.
- **Queries:** bot moves use `kata-genmove_analyze`. Candidates use
  `kata-analyze` with the human mode left on — candidates need the true
  human policy (how often a human at your rank plays each move). Winrates
  report BLACK-perspective (`reportAnalysisWinratesAs = BLACK` in
  `gtp.cfg`. The ViewModel flips for White humans.) Scoring uses
  `final_score` under Chinese rules with komi 7.5. Komi means White
  moves second and receives 7.5 extra points.
- **Profiles:** one `rank_<id>` profile per game rank. KataGo profiles
  cover only part of our ladder, so weaker ranks clamp to the lowest
  profile (the clamp lives in `profileFor`). `preaz_` (pre-AI-era openings)
  is a possible follow-up selector, not the default.
- **Budgets:** replies use a small visit budget, candidate queries a much
  larger one (both live in `KataGoGtpEngine`). A visit is one simulated
  continuation. Replies land under a second on warm hardware. First load
  takes a few seconds.

Binary provenance, staging layout, and the install loop live in the
phone-session notes, not here
(see [ON_DEVICE.md](../android/docs/ON_DEVICE.md)).

## Server engine (development only)

`server/` spawns `katago analysis` (JSON lines over stdin/stdout) behind the
same query shapes. It is the reference implementation. It is also the
fallback when no device is at hand. Models stay out of git and arrive by
script.

## TODO

- [x] On-device GTP path working (no remote server)
- [x] Rank imitation through `humanSLProfile` with real move history
- [ ] `preaz_` selector experiment (intentional pre-AI-era imitation)
- [ ] 1-ply lookahead for "tempting bad" accuracy (optional polish)

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md) — where the engine sits in the app
- [ON_DEVICE.md](../android/docs/ON_DEVICE.md) — provenance, staging, install loop
- [TECH_STACK.md](TECH_STACK.md) — shipped stack vs dev stack
