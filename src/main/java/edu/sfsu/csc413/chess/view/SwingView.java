package edu.sfsu.csc413.chess.view;

import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.engine.GameStatus;
import edu.sfsu.csc413.chess.model.Color;
import edu.sfsu.csc413.chess.model.Move;
import edu.sfsu.csc413.chess.model.Piece;
import edu.sfsu.csc413.chess.model.PieceType;
import edu.sfsu.csc413.chess.model.Position;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Set;

/**
 * A window: click a piece, then click the square to move it to.
 *
 * <p>This view is event-driven. {@link #start} builds the window and returns;
 * it never starts the controller's ask-and-handle loop. Every click is turned into a
 * {@link PlayerAction} and handed to {@link GameController#handle}, which
 * changes the game and calls {@link #render} back.
 *
 * <p>The view never decides whether a move is legal. It highlights the squares
 * that {@link Game#legalMoves()} lists, and whatever the player clicks is sent
 * to the controller, which reports an illegal move through
 * {@link #showMessage}. A promotion is sent as four characters (e.g.
 * {@code e7e8}); the controller asks {@link #promotionChoice}, whose default
 * answer is a queen.
 *
 * <p><strong>Threads.</strong> {@link GameController#start()} calls
 * {@code render} and {@code showMessage} on the main thread before the window
 * exists. So {@code render} copies what it needs out of the {@link Game} into an
 * immutable {@link Snapshot} on whatever thread it was called from, and all
 * Swing work happens on the event dispatch thread.
 */
public class SwingView implements BoardView {

    private static final int SQUARE = 72;
    private static final java.awt.Color LIGHT = new java.awt.Color(240, 217, 181);
    private static final java.awt.Color DARK = new java.awt.Color(181, 136, 99);
    private static final java.awt.Color SELECTED = new java.awt.Color(246, 246, 105, 200);
    private static final java.awt.Color LAST_MOVE = new java.awt.Color(205, 210, 106, 140);
    private static final java.awt.Color TARGET = new java.awt.Color(20, 85, 30, 120);
    private static final java.awt.Color IN_CHECK = new java.awt.Color(220, 40, 40, 170);

    /** What the window draws: a copy of the game, taken in {@link #render}. */
    private volatile Snapshot snapshot;
    /** The last message, kept until the window exists to show it. */
    private volatile String pendingMessage = "";

    // Everything below is touched only on the event dispatch thread.
    private GameController controller;
    private JFrame frame;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private JLabel messageLabel;
    private JButton undoButton;
    /** The first click of a half-finished move, or null. */
    private Position selected;
    private boolean finished;

    @Override
    public void render(Game game) {
        snapshot = Snapshot.of(game);
        onEdt(this::refresh);
    }

    @Override
    public void showMessage(String message) {
        pendingMessage = message;
        onEdt(() -> {
            if (messageLabel != null) {
                messageLabel.setText(message);
            }
        });
    }

    /** Builds the window on the event dispatch thread and returns at once. */
    @Override
    public void start(GameController controller) {
        SwingUtilities.invokeLater(() -> buildWindow(controller));
    }

    /**
     * The controller calls this when the game is over (after drawing the final
     * position and the result) or when the player quits. The window stays open
     * so the result can be read; it just stops taking moves.
     */
    @Override
    public void close() {
        onEdt(() -> {
            finished = true;
            selected = null;
            if (undoButton != null) {
                undoButton.setEnabled(false);
                boardPanel.repaint();
            }
        });
    }

    // ------------------------------------------------------------------ window

    private void buildWindow(GameController controller) {
        this.controller = controller;

        frame = new JFrame("CSC 413 Chess");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!finished) {
                    controller.handle(new PlayerAction.Quit());
                }
                frame.dispose();
            }
        });

        boardPanel = new BoardPanel();

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 18f));
        messageLabel = new JLabel(pendingMessage);
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 14f));

        undoButton = new JButton("Undo");
        undoButton.addActionListener(e -> {
            selected = null;
            messageLabel.setText(" ");
            controller.handle(new PlayerAction.Undo());
        });

        JPanel labels = new JPanel(new GridLayout(2, 1));
        labels.add(statusLabel);
        labels.add(messageLabel);

        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        south.add(labels, BorderLayout.CENTER);
        south.add(undoButton, BorderLayout.EAST);

        frame.getContentPane().add(boardPanel, BorderLayout.CENTER);
        frame.getContentPane().add(south, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);

        refresh();
        frame.setVisible(true);
    }

    /** Redraws the status line and the board from the latest snapshot. */
    private void refresh() {
        if (frame == null || snapshot == null) {
            return;
        }
        statusLabel.setText(snapshot.statusText());
        boardPanel.repaint();
    }

    // ------------------------------------------------------------------ clicks

    private void squareClicked(Position square) {
        Snapshot shot = snapshot;
        if (finished || shot == null) {
            return;
        }

        if (selected == null) {
            if (shot.hasMovesFrom(square)) {
                selected = square;
                messageLabel.setText(" ");
            } else {
                messageLabel.setText("Pick one of " + name(shot.sideToMove())
                        + "'s pieces that can move.");
            }
        } else if (square.equals(selected)) {
            selected = null;
        } else if (shot.hasMovesFrom(square)) {
            // Clicking another of your own movable pieces changes the selection.
            selected = square;
            messageLabel.setText(" ");
        } else {
            String notation = selected.toString() + square;
            selected = null;
            messageLabel.setText(" ");
            // The controller decides. A promotion goes as four characters and
            // the controller asks promotionChoice(), which answers QUEEN.
            controller.handle(new PlayerAction.Move(notation));
        }
        boardPanel.repaint();
    }

    // ------------------------------------------------------------------ helpers

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private static String name(Color color) {
        return color == Color.WHITE ? "White" : "Black";
    }

    /**
     * An immutable copy of what the window needs from the game, so painting
     * never reads the live {@link Game}.
     */
    private record Snapshot(Piece[][] pieces, Color sideToMove, GameStatus status,
                            List<Move> legalMoves, Move lastMove) {

        static Snapshot of(Game game) {
            Piece[][] pieces = new Piece[Position.BOARD_SIZE][Position.BOARD_SIZE];
            for (int file = 0; file < Position.BOARD_SIZE; file++) {
                for (int rank = 0; rank < Position.BOARD_SIZE; rank++) {
                    pieces[file][rank] = game.board().pieceAt(new Position(file, rank));
                }
            }
            List<Move> history = game.history();
            Move last = history.isEmpty() ? null : history.get(history.size() - 1);
            return new Snapshot(pieces, game.sideToMove(), game.status(),
                    List.copyOf(game.legalMoves()), last);
        }

        Piece at(Position p) {
            return pieces[p.file()][p.rank()];
        }

        boolean hasMovesFrom(Position from) {
            return legalMoves.stream().anyMatch(m -> m.from().equals(from));
        }

        Set<Position> targetsFrom(Position from) {
            return legalMoves.stream()
                    .filter(m -> m.from().equals(from))
                    .map(Move::to)
                    .collect(java.util.stream.Collectors.toSet());
        }

        /** The square of the side to move's king, for the check highlight. */
        Position kingOfSideToMove() {
            for (int file = 0; file < Position.BOARD_SIZE; file++) {
                for (int rank = 0; rank < Position.BOARD_SIZE; rank++) {
                    Piece p = pieces[file][rank];
                    if (p != null && p.type() == PieceType.KING && p.color() == sideToMove) {
                        return new Position(file, rank);
                    }
                }
            }
            return null;
        }

        String statusText() {
            String side = name(sideToMove);
            return switch (status) {
                case IN_PROGRESS -> side + " to move";
                case CHECK -> side + " to move  —  Check!";
                case CHECKMATE -> "Checkmate!  " + name(sideToMove.opposite()) + " wins.";
                case STALEMATE -> "Stalemate.  The game is a draw.";
            };
        }
    }

    // ------------------------------------------------------------------ board

    /** Paints the squares and pieces, and turns a mouse click into a square. */
    private final class BoardPanel extends JPanel {

        private final Font pieceFont = choosePieceFont();

        BoardPanel() {
            setPreferredSize(new Dimension(SQUARE * 8, SQUARE * 8));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Position square = squareAt(e.getX(), e.getY());
                    if (square != null) {
                        squareClicked(square);
                    }
                }
            });
        }

        private int squareSize() {
            return Math.min(getWidth(), getHeight()) / Position.BOARD_SIZE;
        }

        private int originX() {
            return (getWidth() - squareSize() * Position.BOARD_SIZE) / 2;
        }

        private int originY() {
            return (getHeight() - squareSize() * Position.BOARD_SIZE) / 2;
        }

        /** Screen rows count down from the top; ranks count up from white's side. */
        private Position squareAt(int x, int y) {
            int size = squareSize();
            if (size == 0) {
                return null;
            }
            int column = Math.floorDiv(x - originX(), size);
            int row = Math.floorDiv(y - originY(), size);
            int file = column;
            int rank = Position.BOARD_SIZE - 1 - row;
            return Position.isOnBoard(file, rank) ? new Position(file, rank) : null;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Snapshot shot = snapshot;
            if (shot == null) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int size = squareSize();
            Set<Position> targets = selected == null ? Set.of() : shot.targetsFrom(selected);
            Position checkedKing = (shot.status() == GameStatus.CHECK
                    || shot.status() == GameStatus.CHECKMATE) ? shot.kingOfSideToMove() : null;

            for (int row = 0; row < Position.BOARD_SIZE; row++) {
                for (int column = 0; column < Position.BOARD_SIZE; column++) {
                    int file = column;
                    int rank = Position.BOARD_SIZE - 1 - row;
                    Position square = new Position(file, rank);
                    int x = originX() + column * size;
                    int y = originY() + row * size;

                    g.setColor((file + rank) % 2 == 0 ? DARK : LIGHT);
                    g.fillRect(x, y, size, size);

                    Move last = shot.lastMove();
                    if (last != null && (square.equals(last.from()) || square.equals(last.to()))) {
                        g.setColor(LAST_MOVE);
                        g.fillRect(x, y, size, size);
                    }
                    if (square.equals(checkedKing)) {
                        g.setColor(IN_CHECK);
                        g.fillRect(x, y, size, size);
                    }
                    if (square.equals(selected)) {
                        g.setColor(SELECTED);
                        g.fillRect(x, y, size, size);
                    }

                    Piece piece = shot.at(square);
                    if (piece != null) {
                        drawPiece(g, piece, x, y, size);
                    }

                    if (targets.contains(square)) {
                        g.setColor(TARGET);
                        if (piece == null) {
                            int d = size / 3;
                            g.fillOval(x + (size - d) / 2, y + (size - d) / 2, d, d);
                        } else {
                            g.setStroke(new BasicStroke(size / 12f));
                            int inset = size / 16;
                            g.drawOval(x + inset, y + inset, size - 2 * inset, size - 2 * inset);
                        }
                    }
                }
            }
            g.dispose();
        }

        /**
         * Draws a piece so it stands out on either square colour.
         *
         * <p>A white piece is a white silhouette with the outlined figure
         * (U+2654..) drawn over it in black. A black piece is the solid figure
         * (U+265A..) in near-black over a light halo. The halo is stroked
         * <em>before</em> the fill: stroking on top closes the thin gaps inside
         * the king, queen and pawn and makes them look the wrong colour.
         */
        private void drawPiece(Graphics2D g, Piece piece, int x, int y, int size) {
            boolean white = piece.color() == Color.WHITE;
            Font font = pieceFont.deriveFont((float) (size * 0.8));
            boolean figures = font.canDisplay('♚');
            char solidChar = figures ? solidFigure(piece.type()) : piece.type().symbol();

            Shape solid = outline(font, solidChar);
            Rectangle2D bounds = solid.getBounds2D();
            AffineTransform centre = AffineTransform.getTranslateInstance(
                    x + (size - bounds.getWidth()) / 2 - bounds.getX(),
                    y + (size - bounds.getHeight()) / 2 - bounds.getY());
            solid = centre.createTransformedShape(solid);

            if (white && figures) {
                Shape hollow = centre.createTransformedShape(
                        outline(font, (char) (solidChar - ('♚' - '♔'))));
                Area silhouette = new Area(solid);
                silhouette.add(new Area(hollow));
                g.setColor(java.awt.Color.WHITE);
                g.fill(silhouette);
                g.setColor(java.awt.Color.BLACK);
                g.fill(hollow);
            } else {
                g.setStroke(new BasicStroke(size / 18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(white ? java.awt.Color.BLACK : new java.awt.Color(235, 235, 235));
                g.draw(solid);
                g.setColor(white ? java.awt.Color.WHITE : new java.awt.Color(20, 20, 20));
                g.fill(solid);
            }
        }

        private Shape outline(Font font, char c) {
            GlyphVector glyphs = font.createGlyphVector(
                    getFontMetrics(font).getFontRenderContext(), String.valueOf(c));
            return glyphs.getOutline();
        }

        /** The solid chess figure; a white piece also uses the outlined one. */
        private static char solidFigure(PieceType type) {
            return switch (type) {
                case KING -> '♚';
                case QUEEN -> '♛';
                case ROOK -> '♜';
                case BISHOP -> '♝';
                case KNIGHT -> '♞';
                case PAWN -> '♟';
            };
        }

        /** A font that has the chess figures, or a bold sans-serif for letters. */
        private static Font choosePieceFont() {
            Set<String> installed = Set.of(GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            for (String family : List.of("Segoe UI Symbol", "DejaVu Sans", "Arial Unicode MS",
                    "Noto Sans Symbols2", "Apple Symbols", "FreeSerif")) {
                if (installed.contains(family)) {
                    Font font = new Font(family, Font.PLAIN, 48);
                    if (font.canDisplay('♚')) {
                        return font;
                    }
                }
            }
            Font fallback = new Font(Font.SERIF, Font.PLAIN, 48);
            return fallback.canDisplay('♚') ? fallback : new Font(Font.SANS_SERIF, Font.BOLD, 48);
        }
    }
}
