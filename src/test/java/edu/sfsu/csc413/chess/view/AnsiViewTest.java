package edu.sfsu.csc413.chess.view;

import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.engine.GameStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnsiViewTest {

    private static String play(Game game, String typed) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        AnsiView view = new AnsiView(out, new Scanner(typed));
        new GameController(game, view).start();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Fool's Mate plays to checkmate, with an illegal move and an undo on the way")
    void foolsMate() {
        Game game = new Game();
        String output = play(game, String.join("\n",
                "f2f3", "e7e5", "e2e5", "g2g4", "undo", "g2g4", "d8h4", ""));

        assertEquals(GameStatus.CHECKMATE, game.status());
        assertEquals(4, game.history().size());
        assertTrue(output.contains("Not a legal move: 'e2e5'."));
        assertTrue(output.contains("Checkmate. BLACK wins."));
        assertTrue(output.contains("CHECKMATE"));
    }

    @Test
    @DisplayName("The board turns to face the side to move")
    void drawnFromSideToMove() {
        Game game = new Game();
        AnsiView view = new AnsiView(new PrintStream(new ByteArrayOutputStream()), new Scanner(""));
        String white = view.drawBoard(game).replaceAll("\u001B\\[[0-9;]*m", "");
        assertTrue(white.startsWith("    a  b  c  d  e  f  g  h"), white);

        game.play(game.findLegalMove("e2e4").orElseThrow());
        String black = view.drawBoard(game).replaceAll("\u001B\\[[0-9;]*m", "");
        assertTrue(black.startsWith("    h  g  f  e  d  c  b  a"), black);
        assertTrue(black.lines().skip(2).findFirst().orElseThrow().startsWith("1 "), black);
    }

    @Test
    @DisplayName("The move list is numbered in pairs")
    void moveList() {
        Game game = new Game();
        game.play(game.findLegalMove("e2e4").orElseThrow());
        game.play(game.findLegalMove("e7e5").orElseThrow());
        game.play(game.findLegalMove("g1f3").orElseThrow());
        assertEquals("Moves: 1. e2e4 e7e5  2. g1f3", AnsiView.moveList(game.history()));
    }
}
