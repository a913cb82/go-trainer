# Next steps

## Immediate

- Run `./gradlew :app:testDebugUnitTest`. The WHR migration and the resync
  thinking fix were verified here by mirroring every assertion in Python
  (solver cross-checked against direct matrix inversion to 1e-13), but gradle
  has not run: no JVM toolchain in that container.

## WHR follow-ups (in order)

1. **Bot-vs-bot anchor refit.** Adjacent-rung self-play gives direct gap
   measurements independent of human data. Edit only the frozen table in
   `WhrAnchors`; stored histories resolve anchors at recompute time, so old
   games follow with no migration.
2. **Refit w² from our own history.** Ship value is 60 elo²/day (Coulom's
   Go-data fit). Select by predictive performance once streak data
   accumulates; GoShrine's 300 is the "humans improve faster than bots"
   direction if validation points that way.
3. **Color-advantage residual check.** Currently fixed at zero. `playerBlack`
   is stored, so fit the term only if residuals show a color pattern.

## Watch in production

- Automatch should terminate streaks sooner through stronger bots now that
  the rating moves faster. If streaks persist across rungs, that is evidence
  for follow-up 2, not an automatch bug.
