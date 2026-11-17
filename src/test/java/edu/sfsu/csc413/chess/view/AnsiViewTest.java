package edu.sfsu.csc413.chess.view;

import edu.sfsu.csc413.chess.engine.Game;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnsiViewTest {

    private String play(String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        AnsiView view = new AnsiView(out, new Scanner(input));
        new GameController(new Game(), view).start();
        return bytes.toString(StandardCharsets.UTF_8).replaceAll("\u001B\\[[0-9;]*m", "");
    }

    @Test
    void foolsMateEndsInCheckmate() {
        String text = play("f2f3\ne7e5\ng2g4\nd8h4\n");
        assertTrue(text.contains("Checkmate. BLACK wins."), text);
        assertTrue(text.contains("CHECKMATE — Black wins"), text);
    }

    @Test
    void illegalMoveAndUndoAreReported() {
        String text = play("e2e5\nundo\nquit\n");
        assertTrue(text.contains("Not a legal move: 'e2e5'."), text);
        assertTrue(text.contains("Nothing to undo."), text);
    }

    @Test
    void boardIsDrawnFromTheSideToMove() {
        String text = play("e2e4\nquit\n");
        int whiteView = text.indexOf("8 ║");
        int blackView = text.indexOf("1 ║", whiteView);
        assertTrue(whiteView >= 0 && blackView > whiteView,
                "white's board has rank 8 on top, then black's has rank 1 on top");
        assertTrue(text.contains("1. e2e4"), text);
    }
}
