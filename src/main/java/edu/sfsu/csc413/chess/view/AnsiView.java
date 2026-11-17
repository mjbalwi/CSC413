package edu.sfsu.csc413.chess.view;

import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.engine.GameStatus;
import edu.sfsu.csc413.chess.model.Board;
import edu.sfsu.csc413.chess.model.Color;
import edu.sfsu.csc413.chess.model.Move;
import edu.sfsu.csc413.chess.model.Piece;
import edu.sfsu.csc413.chess.model.Position;

import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/**
 * A terminal board in ANSI colour, framed with a box-drawing border and always
 * drawn from the side to move's seat at the table.
 *
 * <p>Squares are coloured backgrounds rather than characters, the last move is
 * tinted, and a king in check is shown on red. Below the board are whose turn
 * it is, the status from {@link Game#status()}, and the last few moves from
 * {@link Game#history()}.
 *
 * <p>Like {@link ConsoleView} it can wait for input, so {@link #start} lets the
 * controller drive with {@link GameController#runLoop()}, and typed lines are
 * turned into actions by {@link PlayerAction#parse}. It decides no rules.
 *
 * <p>Started with the command-line word {@code ansi}.
 */
public class AnsiView implements BoardView {

    private static final String ESC = "\u001B[";
    private static final String RESET = ESC + "0m";

    private static final String LIGHT_BG = ESC + "48;5;180m";
    private static final String DARK_BG = ESC + "48;5;94m";
    private static final String LAST_LIGHT_BG = ESC + "48;5;186m";
    private static final String LAST_DARK_BG = ESC + "48;5;142m";
    private static final String CHECK_BG = ESC + "48;5;160m";
    private static final String WHITE_FG = ESC + "1;97m";
    private static final String BLACK_FG = ESC + "1;30m";
    private static final String DIM = ESC + "2m";
    private static final String BOLD = ESC + "1m";
    private static final String RED = ESC + "1;31m";
    private static final String YELLOW = ESC + "1;33m";

    private static final int HISTORY_SHOWN = 8;

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
        out.println();
        out.println(statusLine(game));
        out.println(historyLine(game));
    }

    @Override
    public void showMessage(String message) {
        String lower = message.toLowerCase();
        String colour = lower.startsWith("not a legal") || lower.startsWith("nothing") ? RED
                : lower.startsWith("checkmate") || lower.startsWith("stalemate") ? YELLOW
                : BOLD;
        out.println(colour + message + RESET);
    }

    /** A terminal can wait for typing, so the controller drives. */
    @Override
    public void start(GameController controller) {
        controller.runLoop();
    }

    @Override
    public PlayerAction nextAction(Game game) {
        out.print(BOLD + name(game.sideToMove()) + RESET
                + " — your move (e.g. e2e4, 'undo', 'moves', 'quit'): ");
        out.flush();
        return PlayerAction.parse(in.hasNextLine() ? in.nextLine() : "quit");
    }

    // ------------------------------------------------------------------ drawing

    /**
     * The board, seen from the side to move. Flipping turns both axes: black
     * sees rank 1 at the top and the h-file on the left.
     */
    String drawBoard(Game game) {
        Board board = game.board();
        boolean flipped = game.sideToMove() == Color.BLACK;
        List<Move> history = game.history();
        Move last = history.isEmpty() ? null : history.getLast();
        GameStatus status = game.status();
        Position checkedKing = status == GameStatus.CHECK || status == GameStatus.CHECKMATE
                ? board.kingPosition(game.sideToMove()) : null;

        StringBuilder text = new StringBuilder();
        text.append(fileLegend(flipped));
        text.append("   ╔").append("═".repeat(24)).append("╗\n");

        for (int row = 0; row < Position.BOARD_SIZE; row++) {
            int rank = flipped ? row : Position.BOARD_SIZE - 1 - row;
            text.append(' ').append(rank + 1).append(" ║");
            for (int column = 0; column < Position.BOARD_SIZE; column++) {
                int file = flipped ? Position.BOARD_SIZE - 1 - column : column;
                Position square = new Position(file, rank);
                boolean light = (file + rank) % 2 == 1;
                boolean lastMove = last != null
                        && (square.equals(last.from()) || square.equals(last.to()));

                String background = square.equals(checkedKing) ? CHECK_BG
                        : lastMove ? (light ? LAST_LIGHT_BG : LAST_DARK_BG)
                        : light ? LIGHT_BG : DARK_BG;
                text.append(background).append(cell(board.pieceAt(square))).append(RESET);
            }
            text.append("║ ").append(rank + 1).append('\n');
        }

        text.append("   ╚").append("═".repeat(24)).append("╝\n");
        text.append(fileLegend(flipped));
        return text.toString();
    }

    /** Three columns per square: the piece letter, coloured by side. */
    private static String cell(Piece piece) {
        if (piece == null) {
            return "   ";
        }
        String colour = piece.color() == Color.WHITE ? WHITE_FG : BLACK_FG;
        return colour + " " + Character.toUpperCase(piece.symbol()) + " ";
    }

    private static String fileLegend(boolean flipped) {
        StringBuilder text = new StringBuilder("    ");
        for (int column = 0; column < Position.BOARD_SIZE; column++) {
            int file = flipped ? Position.BOARD_SIZE - 1 - column : column;
            text.append(' ').append((char) ('a' + file)).append(' ');
        }
        return DIM + text + RESET + "\n";
    }

    private static String statusLine(Game game) {
        String side = name(game.sideToMove());
        return switch (game.status()) {
            case IN_PROGRESS -> BOLD + side + " to move" + RESET;
            case CHECK -> BOLD + side + " to move  " + RESET + RED + "CHECK" + RESET;
            case CHECKMATE -> YELLOW + "CHECKMATE — " + name(game.sideToMove().opposite())
                    + " wins" + RESET;
            case STALEMATE -> YELLOW + "STALEMATE — draw" + RESET;
        };
    }

    /** The last few moves, numbered the way a score sheet numbers them. */
    private static String historyLine(Game game) {
        List<Move> history = game.history();
        if (history.isEmpty()) {
            return DIM + "No moves yet." + RESET;
        }
        int start = Math.max(0, history.size() - HISTORY_SHOWN);
        StringBuilder text = new StringBuilder(DIM + "Moves: ");
        if (start > 0) {
            text.append("… ");
        }
        for (int i = start; i < history.size(); i++) {
            if (i % 2 == 0) {
                text.append(i / 2 + 1).append(". ");
            } else if (i == start) {
                text.append(i / 2 + 1).append("… ");
            }
            text.append(history.get(i)).append(' ');
        }
        return text.toString().stripTrailing() + RESET;
    }

    private static String name(Color color) {
        return color == Color.WHITE ? "White" : "Black";
    }
}
