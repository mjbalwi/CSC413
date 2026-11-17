package edu.sfsu.csc413.chess.view;

import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.engine.GameStatus;
import edu.sfsu.csc413.chess.model.Color;
import edu.sfsu.csc413.chess.model.Move;
import edu.sfsu.csc413.chess.model.Piece;
import edu.sfsu.csc413.chess.model.Position;

import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/**
 * A terminal board in ANSI colour, framed by a box-drawing border and drawn
 * from the side of the player whose turn it is.
 *
 * <p>Like {@link ConsoleView} it can wait for typed input, so {@link #start}
 * lets the controller drive with {@link GameController#runLoop()}. What it adds
 * is presentation only: coloured squares, the last move highlighted, the king
 * in check marked in red, the board turned to face the side to move, and a
 * running move list. Every fact it shows is read from {@link Game}; it never
 * decides a rule.
 *
 * <p>Start it with {@code java -cp target/classes edu.sfsu.csc413.chess.Main ansi}.
 * Your terminal must understand ANSI escape codes (Windows Terminal, the
 * IntelliJ console, and any macOS/Linux terminal do).
 */
public class AnsiView implements BoardView {

    private static final String ESC = "\u001B[";
    private static final String RESET = ESC + "0m";
    private static final String LIGHT_BG = ESC + "48;5;180m";
    private static final String DARK_BG = ESC + "48;5;137m";
    private static final String LAST_LIGHT_BG = ESC + "48;5;186m";
    private static final String LAST_DARK_BG = ESC + "48;5;143m";
    private static final String CHECK_BG = ESC + "48;5;160m";
    private static final String WHITE_FG = ESC + "1;38;5;231m";
    private static final String BLACK_FG = ESC + "1;38;5;16m";
    private static final String FRAME = ESC + "38;5;245m";
    private static final String BOLD = ESC + "1m";
    private static final String RED = ESC + "1;31m";
    private static final String YELLOW = ESC + "33m";

    private final PrintStream out;
    private final Scanner in;

    /** A view on the real terminal. */
    public AnsiView() {
        this(System.out, new Scanner(System.in));
    }

    /** Injectable streams, so tests can drive the view without a terminal. */
    public AnsiView(PrintStream out, Scanner in) {
        this.out = out;
        this.in = in;
    }

    @Override
    public void render(Game game) {
        out.println();
        out.print(drawBoard(game));
        out.println(statusLine(game));
        out.println(moveList(game.history()));
    }

    @Override
    public void showMessage(String message) {
        out.println(YELLOW + message + RESET);
    }

    @Override
    public void start(GameController controller) {
        controller.runLoop();
    }

    @Override
    public PlayerAction nextAction(Game game) {
        out.print(BOLD + game.sideToMove() + RESET
                + " (e.g. e2e4, 'undo', 'moves', 'quit') > ");
        out.flush();
        return PlayerAction.parse(in.hasNextLine() ? in.nextLine() : "quit");
    }

    /**
     * The framed board. Drawn from {@code game.sideToMove()}'s side: white sees
     * rank 8 at the top and the a-file on the left; black sees both reversed.
     */
    String drawBoard(Game game) {
        boolean flipped = game.sideToMove() == Color.BLACK;
        List<Move> history = game.history();
        Move last = history.isEmpty() ? null : history.getLast();
        GameStatus status = game.status();
        Position checkedKing = (status == GameStatus.CHECK || status == GameStatus.CHECKMATE)
                ? game.board().kingPosition(game.sideToMove())
                : null;

        StringBuilder text = new StringBuilder();
        String files = fileLegend(flipped);
        text.append("    ").append(files).append('\n');
        text.append("  ").append(FRAME).append('╔').append("═".repeat(24)).append('╗').append(RESET).append('\n');

        for (int row = 0; row < Position.BOARD_SIZE; row++) {
            int rank = flipped ? row : Position.BOARD_SIZE - 1 - row;
            text.append(rank + 1).append(' ').append(FRAME).append('║').append(RESET);
            for (int column = 0; column < Position.BOARD_SIZE; column++) {
                int file = flipped ? Position.BOARD_SIZE - 1 - column : column;
                Position square = new Position(file, rank);
                boolean light = (file + rank) % 2 == 1;
                boolean lastMove = last != null
                        && (square.equals(last.from()) || square.equals(last.to()));

                String background;
                if (square.equals(checkedKing)) {
                    background = CHECK_BG;
                } else if (lastMove) {
                    background = light ? LAST_LIGHT_BG : LAST_DARK_BG;
                } else {
                    background = light ? LIGHT_BG : DARK_BG;
                }
                text.append(background).append(' ').append(cell(game.board().pieceAt(square)))
                        .append(background).append(' ').append(RESET);
            }
            text.append(FRAME).append('║').append(RESET).append(' ').append(rank + 1).append('\n');
        }

        text.append("  ").append(FRAME).append('╚').append("═".repeat(24)).append('╝').append(RESET).append('\n');
        text.append("    ").append(files).append('\n');
        return text.toString();
    }

    /** One piece as a bold letter, white or black text; a space if empty. */
    private static String cell(Piece piece) {
        if (piece == null) {
            return " ";
        }
        String colour = piece.color() == Color.WHITE ? WHITE_FG : BLACK_FG;
        return colour + Character.toUpperCase(piece.symbol()) + RESET;
    }

    private static String fileLegend(boolean flipped) {
        StringBuilder legend = new StringBuilder();
        for (int column = 0; column < Position.BOARD_SIZE; column++) {
            int file = flipped ? Position.BOARD_SIZE - 1 - column : column;
            legend.append((char) ('a' + file)).append("  ");
        }
        return legend.toString().stripTrailing();
    }

    private static String statusLine(Game game) {
        String side = game.sideToMove().toString();
        return switch (game.status()) {
            case IN_PROGRESS -> BOLD + side + " to move" + RESET;
            case CHECK -> BOLD + side + " to move" + RESET + "  " + RED + "CHECK" + RESET;
            case CHECKMATE -> RED + "CHECKMATE" + RESET;
            case STALEMATE -> BOLD + "STALEMATE" + RESET;
        };
    }

    /** The game so far in numbered pairs: "1. e2e4 e7e5  2. g1f3". */
    static String moveList(List<Move> history) {
        if (history.isEmpty()) {
            return "Moves: (none yet)";
        }
        StringBuilder text = new StringBuilder("Moves:");
        for (int i = 0; i < history.size(); i++) {
            if (i % 2 == 0) {
                text.append(i == 0 ? " " : "  ").append(i / 2 + 1).append('.');
            }
            text.append(' ').append(history.get(i));
        }
        return text.toString();
    }
}
