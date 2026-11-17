# M7 Submission

## 1. Group
TODO: groupmates' names and GitHub usernames, or "alone".

## 2. Tools
Claude Code, model Claude Opus 5.5 (`claude-opus-5-5`) for run 2. TODO: run 1 / run 3.

## 3. Other view
Command-line word: **`ansi`**

```bash
./mvnw -q compile
java -cp target/classes edu.sfsu.csc413.chess.Main ansi
```

`AnsiView` (`src/main/java/edu/sfsu/csc413/chess/view/AnsiView.java`) is a terminal board in
ANSI colour with a box-drawing border, drawn from the side to move. It highlights the last move,
turns the king's square red in check, and prints a numbered move list. It uses `controller.runLoop()`
through `start`, like `ConsoleView`.

## 4. Runs
- **m7-run-2**: request, word for word: `Follow docs/milestones/m7.md and AGENTS.md. Implement the required views.`
  Changed from the previous run: TODO.

## 5. `AGENTS.md`
TODO

## 6. Review
TODO (at least three places, with file:line, how you checked each, and the decision)

## 7. Comparison
TODO

## 8. Applied
TODO

## 9. Debugger evidence
TODO

## 10. By hand
TODO
