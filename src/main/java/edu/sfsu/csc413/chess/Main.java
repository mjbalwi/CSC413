package edu.sfsu.csc413.chess;

import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.view.BoardView;
import edu.sfsu.csc413.chess.view.ConsoleView;
import edu.sfsu.csc413.chess.view.EmojiView;
import edu.sfsu.csc413.chess.view.GameController;

/**
 * Entry point.
 *
 * <p>Builds a {@link Game}, a {@link BoardView}, and a {@link GameController}
 * that joins them, then starts the game. Pass {@code emoji} on the command
 * line for {@link EmojiView}; anything else gets {@link ConsoleView}.
 */
public final class Main {

    public static void main(String[] args) {
        Game game = new Game();
        BoardView view = (args.length > 0 && args[0].equals("emoji"))
                ? new EmojiView()
                : new ConsoleView();
        GameController controller = new GameController(game, view);
        controller.start();
    }

    private Main() {
    }
}
