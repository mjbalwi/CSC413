# M7 Submission

## 1. Group
alone


## 2. Tools
Claude Code for all 3 runs

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
- **m7-run-1**: request, word for word: `Follow docs/milestones/m7.md and AGENTS.md. Implement the required views.`

- **m7-run-2**: request, word for word: `Follow docs/milestones/m7.md and AGENTS.md. Implement the required views.`
  Changed from the previous run: I added "- Create animations when a player captures a piece" under the "Swing " tab

- **m7-run-3**: request, word for word: `Follow docs/milestones/m7.md and AGENTS.md. Implement the required views.`
  Changed from previous run: I deleted all the "ideas" under the other version and only left this under the other view:
  "Do: a page in a browser, served with the JDK's com.sun.net.httpserver."

## 5. `AGENTS.md`
- A rule I added in Don't was explcity not to change GameController as that was the rules for the agents but it wasn't explcitly
  identified in the AGENTS.md file provided in the slides

## 6. Review
- One place of the generated code that matters is: private Game game; (SwingView.java 61). This is a perfect example of the MVC
  architecture in action because as we know the view depends on the Model which is game here and it creates an instance of it to change the
  change the model as the user plays through this view.

- Another place of generated code that matters is: onSquareClicked method (SwingView.java 203) because it is handles the half-state of a selected square

- The third generated code that matters is: the block of code on (SwingView.java 318) where the view adds the decoration for when the King is in check. This is
  an important piece of code because it checks the status of the King by talking to the Model, thus preserving the MVC design pattern.


## 7. Comparison

### Where does the state of a half-finished move (the selected square) live?

**Swing — Run 1:**  
`SwingView.java`, line 65. The half-finished move lives in a `Position` variable called `selected`. When the window is built, `selected` is set to `null`. Depending on which square the user selects, `selected` is updated accordingly. If the user selects an invalid square (an empty square or one with a piece of the wrong color), `selected` is set to `null`. If the user selects a square with a piece of the correct color, `selected` is set to that piece.

**Swing — Run 2:**  
`SwingView.java`, line 76. Works the same way as Run 1.

**Swing — Run 3:**  
`SwingView.java`, line 83. Works the same way as Run 1.

**Other view — Runs 1 and 2:**  
Not applicable to the ANSI view.

**Other view — Run 3:**  
`WebView.java`, line 299. Similar to the Swing views, the selected square is stored in the JavaScript for the web version.

### How does each view find out which moves are legal?

**Swing — Run 1:**  
`SwingView.java`, line 229. The view passes the move to the controller as a `PlayerAction.Move` using the `handler()` method. This preserves the MVC architecture by allowing the controller to handle the request to the model.

**Swing — Run 2:**  
`SwingView.java`, line 222. The view iterates through `game.legalMoves()`. Moves whose `Move.from()` matches the selected piece are added to a list called `targets`, which contains the legal moves for that piece. Although the view only reads the legal moves from the model, this preserves MVC. However, it leaves the controller out of the loop, unlike Run 1.

**Swing — Run 3:**  
The view creates a `Snapshot` record in `SwingView.java`, line 237, and rewrites the model’s game rules and methods in that record. It creates a copy, or “snapshot,” of the game to find what the window needs, using methods such as `hasMovesFrom()` in `SwingView.java`, line 192.

**Other view — Run 1:**  
`AnsiView.java`, line 88. Works the same way as Run 2.

**Other view — Run 2:**  
`AnsiView.java`, line 82. The view uses `nextAction()` to convert the user’s terminal input into a `PlayerAction`, such as undo, move, or quit. I understand that the resulting move is passed to the controller, but I am still working to understand how the legal moves are determined after that.

**Other view — Run 3:**  
`WebView.java`, line 312. The view iterates through all the legal moves. If a move’s `from` square matches the selected square, that move is included among the legal moves for that selection.

### What does each do on a promotion?

All three runs automatically promote the piece to a queen in both views.

### Which is easier to change, and why? Which did you keep?

**Swing:**  
Run 1 is easier to change because it preserves the MVC architecture more accurately than Runs 2 and 3. In Run 2, the view leaves the controller out of the loop and communicates directly with the model, or game, when deciding which moves are valid. This tightly couples the model and view, making it harder to change one without affecting the other.

Run 1 instead passes the move as a request to the controller, which communicates with the model. This better separates concerns and makes changes easier. Because the model, view, and controller each have more clearly defined responsibilities, changes to the view are less likely to affect the model’s rules.

Run 3 disregards the rules already defined in the model and creates an entire `Snapshot` record in the Swing view. This is unnecessary and complex. The `SwingView` class also contains model logic, making the code harder to scale and work with. Run 1 achieves the same goal with simpler code while preserving the MVC design pattern.

**Other view:**  
Personally, Runs 1 and 2 are easier for me to change because I have a better understanding of Java than of the web version, which combines Java and JavaScript. Regarding MVC and design patterns, I think I could better understand how the ANSI view handles legal moves with more time.


## 8. Applied
I added animation logic from run 2 for the Swingview. noticeNewMove line 237 and drawCapture line 376, both in SwingView.java.

## 9. Debugger evidence
I set a breakpoint in onSquareClicked() and moved the white pawn from e2 to e4. On the first click, square was e2 and selected was null. The board showed White to move. After the click, e2 was selected. On the second click, the pawn moved to e4. This matched what I expected.

## 10. By hand

Changed the color of the selected highlight from yellow to blue. 
