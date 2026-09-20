# go-trainer

Practice 9x9 Go against KataGo at your rank.

![go-trainer screenshot](media/screenshot.png)

## What it does

Each turn shows candidate moves. You select one move. The app reveals points feedback. The opponent then replies with a human-like move.

You play Black or White. You select rank from 30k to 9d. Rank steers the human model. It does not change the rules.

The winrate graph records each move. The graph also works as a review slider. Drag the graph to review past positions. Two passes end the game. The engine then scores the board.

## Training styles

The setup sheet selects style and move count. Move count is 3, 5, or free choice. Style controls how many good and bad moves appear.

- 2 Good, 3 Bad: mix of best moves and tempting errors
- 3 Good, 2 Bad: three best moves hide two errors
- 1 Good, 4 Bad: best move hides among bad moves
- 4 Good, 1 Bad: find the one blunder
- Human-like: most likely human moves at your rank
- Strongest: strongest moves only

Feedback color shows group membership. Green marks good moves. Red marks bad moves. Yellow marks middle moves in flat styles.

Free choice hides style and instant feedback. It shows no candidate circles.

## Install

1. Connect a device with Android 14 or newer.
2. Run `./gradlew installDebug` from the `android` folder.
3. Open the app on the device.
4. Tap Download on the setup screen.
5. Wait until the status shows Ready.
6. Tap Start game.

## Engine

The app runs KataGo on the device. It needs no server. It needs no network after download.

Two nets share the work. The small b10 net searches moves. The b18 human net steers selection toward your rank. The b10 net ships inside the app. The app downloads the b18 net once. The download is about 99 MB.

Play uses a persistent board. The bot uses 10 visits per reply. Candidates use 150 visits per query. Typical replies take under one second on warm hardware. First load takes a few seconds.

Rules are Japanese area scoring with komi 7. Scoring uses engine `final_score`. The app exports games as SGF.

## Project layout

- `android`: the app and the on-device engine
- `app`: web client, kept for dev only
- `server`: PC engine, kept for dev only
- `docs`: architecture and design notes
- `media`: screenshots

The app does not use `app` or `server` at runtime.
