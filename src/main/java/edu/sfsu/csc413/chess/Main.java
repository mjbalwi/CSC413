package edu.sfsu.csc413.chess;

import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.view.BoardView;
import edu.sfsu.csc413.chess.view.ConsoleView;
import edu.sfsu.csc413.chess.view.EmojiView;
import edu.sfsu.csc413.chess.view.GameController;
import edu.sfsu.csc413.chess.view.SwingView;
import edu.sfsu.csc413.chess.view.WebView;

/**
 * Entry point.
 *
 * <p>Builds a {@link Game}, a {@link BoardView}, and a {@link GameController}
 * that joins them, then starts the game. The first command-line word picks the
 * view: {@code emoji} for {@link EmojiView}, {@code swing} for
 * {@link SwingView}, {@code web} for {@link WebView} (a page in the browser);
 * anything else gets {@link ConsoleView}.
 */
public final class Main {

    public static void main(String[] args) {
        Game game = new Game();
        String choice = args.length > 0 ? args[0] : "console";
        BoardView view = switch (choice) {
            case "emoji" -> new EmojiView();
            case "swing" -> new SwingView();
            case "web" -> new WebView();
            default -> new ConsoleView();
        };
        GameController controller = new GameController(game, view);
        controller.start();
    }

    private Main() {
    }
}
