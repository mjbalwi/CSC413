# Instructions for coding agents

This is a chess engine built with MVC. The model and the controller are
written by hand and are finished. Your job is views only. Create a swing
view and one other view.

Here are the parameters for each:

## Swing
-  It runs with:
  - ./mvnw -q compile 
  - java -cp target/classes edu.sfsu.csc413.chess.Main swing
- An 8×8 board. Every piece is easy to see on both square colours.
- You play by clicking a piece, then the square to move it to.
- It shows whose turn it is, check, checkmate, stalemate, and illegal-move messages.
- An Undo button sends new PlayerAction.Undo().
- A promotion doesn't crash. Always choosing a queen is acceptable.
- start builds the window and returns. It never calls controller.runLoop().

## The other View
- The other view. Any BoardView that is not Swing and is more than a new 
 glyph set for the existing views: its own class, playable to the end of 
 a game through your controller, chosen by a word on the command line. 
 Name that word in m7-submission.md. It must use only the JDK. Some
- Do this one: a page in a browser, served with the JDK's com.sun.net.httpserver.


## Do

- Add views as new classes in `src/main/java/edu/sfsu/csc413/chess/view/`
  or a new package under it.
- Implement the `BoardView` interface. Read it, `ConsoleView`, and
  `PlayerAction` before writing anything.
- Read the game only through `Game`: `board()`, `sideToMove()`,
  `legalMoves()`, `status()`, `history()`.
- Send every player action to the controller as a `PlayerAction`:
  `controller.handle(new PlayerAction.Move("e2e4"))`.
- In `Main.java`, use a `switch` on the command-line argument to select
  `emoji`, `swing`, the other new view, or the default `console` view.
  Preserve the existing game and controller setup.

## Don't

- Don't change anything in `model/`, `engine/`, or `factory/`.
- Don't change `BoardView`, `PlayerAction`, `GameController`,
  `ConsoleView`, or `EmojiView`.
- Don't call `game.play`, `game.undoLastMove`, or anything that changes
  the game. Only the controller changes the game.
- Don't work out a chess rule in the view. Legal moves come from
  `game.legalMoves()`; check and checkmate come from `game.status()`.
- Don't wait for input inside a click handler. A window's `start` builds
  the window and returns; it never calls `controller.runLoop()`.
- Don't add dependencies to `pom.xml`. Swing is part of the JDK.
- Do not change GameController

## Check your work

- `./mvnw test` passes, with no test changed or deleted.
- `git diff --stat` lists only new files under `view/` and `Main.java`.

## Stop and ask

If you think a file under "Don't" has to change, stop and explain why
instead of changing it.