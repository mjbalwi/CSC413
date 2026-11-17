# M7 Submission

> Sections marked **TODO** are yours to write: they are your own review,
> comparison and by-hand work, and the handout asks for them in your words.

## 1. Group

TODO: groupmates' names and GitHub usernames, or "alone".

## 2. Tools

Claude Code (CLI), model Claude Opus 5.5 (`claude-opus-5-5`).

## 3. Other view

Command-line word: **`ansi`**

```bash
./mvnw -q compile
java -cp target/classes edu.sfsu.csc413.chess.Main ansi
```

`AnsiView` (`src/main/java/edu/sfsu/csc413/chess/view/AnsiView.java`) is a
terminal board in ANSI colour with a box-drawing border, drawn from the side
to move. It tints the last move, puts a king in check on red, and lists the
last few moves. Input goes through `PlayerAction.parse`, and the controller
drives with `runLoop()`. Use a terminal that supports ANSI escapes (Windows
Terminal, the IntelliJ console, or any Linux/macOS terminal).

## 4. Runs

### m7-run-1

- **Request (word for word):** "Follow docs/milestones/m7.md and AGENTS.md. Implement the required views."
- **Changed from previous run:** first run.

TODO: add further runs.

## 5. `AGENTS.md`

TODO

## 6. Review

TODO: at least three places, each with file:line, what it does, how you checked
it, and your decision. Candidates:

- `SwingView.java`, `start`: builds the window with `SwingUtilities.invokeLater` and returns.
- `SwingView.java`, `onSquareClicked`: the selected-square state and the `PlayerAction.Move` it sends.
- `SwingView.java`, `BoardPanel.squareAt`: mouse coordinates to `Position` (screen rows down, ranks up).
- `SwingView.java`, `close`: keeps the window open after checkmate so the result can be read.
- `AnsiView.java`, `drawBoard`: flips both axes when black is to move.

## 7. Comparison

TODO

## 8. Applied

TODO

## 9. Debugger evidence

TODO

## 10. By hand

TODO: the `M7 by hand:` commit, and what you read to make it.
