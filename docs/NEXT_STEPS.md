# Next steps

## WHR follow-ups (in order)

1. **Bot-vs-bot anchor refit.** Adjacent-rung self-play gives direct gap
   measurements independent of human data. A rung is one step on the rank
   ladder. Edit only the frozen table in `WhrAnchors`. Old games pick up
   anchor changes with no migration.
2. **Refit w² from our own history.** The ship value is 60 elo²/day
   (Coulom's Go-data fit). Elo is the rating scale. Select by predictive
   performance once streak data accumulates. GoShrine's 300 is the "humans
   improve faster than bots" direction if validation points that way.
3. **Color-advantage residual check.** Currently fixed at zero. Black moves
   first, so Black may hold an edge beyond komi. The app stores
`playerBlack`, so fit the term only if residuals show a color pattern. Komi means White
   receives 7.5 extra points for moving second.

## Watch in production

- Automatch should terminate streaks sooner through stronger bots now that
  the rating moves faster. A streak is many wins in a row. If streaks persist
  across rungs, that is evidence for follow-up 2, not an automatch bug.
