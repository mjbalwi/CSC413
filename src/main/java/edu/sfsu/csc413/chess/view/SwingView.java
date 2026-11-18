package edu.sfsu.csc413.chess.view;

import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.engine.GameStatus;
import edu.sfsu.csc413.chess.model.Board;
import edu.sfsu.csc413.chess.model.Color;
import edu.sfsu.csc413.chess.model.Move;
import edu.sfsu.csc413.chess.model.Piece;
import edu.sfsu.csc413.chess.model.Position;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.List;

/**
 * A window: an 8×8 board you play by clicking a piece and then its target.
 *
 * <p>This view is event-driven. {@link #start} builds the window and returns;
 * it never asks the controller to loop. Every click that means
 * something becomes a {@link PlayerAction} handed to
 * {@link GameController#handle}, which updates the game and calls back into
 * {@link #render} and {@link #showMessage}.
 *
 * <p>The only state this class keeps is display state: which square is
 * selected (the half-finished move) and the last message. The position, the
 * turn, the legal moves and the status are all read from {@link Game}.
 */
public class SwingView implements BoardView {

    private static final int SQUARE = 72;

    private static final java.awt.Color LIGHT = new java.awt.Color(240, 217, 181);
    private static final java.awt.Color DARK = new java.awt.Color(181, 136, 99);
    private static final java.awt.Color SELECTED = new java.awt.Color(0, 29, 255, 108);
    private static final java.awt.Color LAST_MOVE = new java.awt.Color(205, 210, 106, 150);
    private static final java.awt.Color TARGET = new java.awt.Color(20, 85, 30, 140);
    private static final java.awt.Color IN_CHECK = new java.awt.Color(220, 40, 40, 170);

    /** The game the window draws. Set by {@link #render}, read on the event thread. */
    private Game game;
    private GameController controller;

    /** The half-finished move: the square clicked first, or null. */
    private Position selected;

    private JFrame frame;
    private BoardPanel boardPanel;
    private JLabel turnLabel;
    private JLabel messageLabel;
    private String pendingMessage = " ";

    /** How long the capture animation runs, and how often it repaints. */
    private static final int CAPTURE_MILLIS = 450;
    private static final int FRAME_MILLIS = 15;

    /** History length at the last render, to spot a move that just happened. */
    private int lastHistorySize;

    /** The capture being animated, or null, and when it started. */
    private Move animating;
    private long animationStart;
    private final Timer animationTimer = new Timer(FRAME_MILLIS, e -> onAnimationTick());

    @Override
    public void start(GameController controller) {
        this.controller = controller;
        this.game = controller.game();
        this.lastHistorySize = game.history().size();
        SwingUtilities.invokeLater(this::buildWindow);
        // Return straight away: from here on the window is driven by clicks.
    }

    @Override
    public void render(Game game) {
        this.game = game;
        onEventThread(() -> {
            noticeNewMove(game);
            refresh();
        });
    }

    @Override
    public void showMessage(String message) {
        pendingMessage = message;
        onEventThread(() -> {
            if (messageLabel != null) {
                messageLabel.setText(message);
            }
        });
    }

    /**
     * Called by the controller when the game ends. The window stays open so the
     * result can be read; the board just stops accepting moves (see
     * {@link #onSquareClicked}). Undo still works.
     */
    @Override
    public void close() {
        onEventThread(() -> {
            selected = null;
            refresh();
        });
    }

    // ------------------------------------------------------------------ window

    private void buildWindow() {
        frame = new JFrame("CSC 413 Chess");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        boardPanel = new BoardPanel();

        turnLabel = new JLabel(" ");
        turnLabel.setFont(turnLabel.getFont().deriveFont(Font.BOLD, 16f));
        messageLabel = new JLabel(pendingMessage);
        messageLabel.setFont(messageLabel.getFont().deriveFont(14f));

        JPanel labels = new JPanel(new GridLayout(2, 1));
        labels.add(turnLabel);
        labels.add(messageLabel);

        JButton undo = new JButton("Undo");
        undo.addActionListener(e -> {
            selected = null;
            messageLabel.setText(" ");
            controller.handle(new PlayerAction.Undo());
        });

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        bottom.add(labels, BorderLayout.CENTER);
        bottom.add(undo, BorderLayout.EAST);

        frame.add(boardPanel, BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        refresh();
        frame.setVisible(true);
    }

    private void refresh() {
        if (frame == null || game == null) {
            return;
        }
        turnLabel.setText(statusText());
        boardPanel.repaint();
    }

    /** Whose turn it is, and the status the game reports. */
    private String statusText() {
        String side = name(game.sideToMove());
        return switch (game.status()) {
            case IN_PROGRESS -> side + " to move";
            case CHECK -> side + " to move — CHECK!";
            case CHECKMATE -> "Checkmate — " + name(game.sideToMove().opposite()) + " wins";
            case STALEMATE -> "Stalemate — draw";
        };
    }

    private static String name(Color color) {
        return color == Color.WHITE ? "White" : "Black";
    }

    private static void onEventThread(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    // ------------------------------------------------------------------ input

    /**
     * First click picks one of your pieces; second click sends the move. The
     * view never decides whether the move is legal: it sends what was clicked,
     * and the controller reports an illegal move. A pawn reaching the last
     * rank is sent as four characters; the controller asks
     * {@link #promotionChoice} (queen, by default) for the piece.
     */
    private void onSquareClicked(Position square) {
        if (animating != null) {
            return;
        }
        if (game.status().isGameOver()) {
            messageLabel.setText("The game is over. Press Undo to take back a move.");
            return;
        }
        Piece piece = game.board().pieceAt(square);
        boolean ownPiece = piece != null && piece.color() == game.sideToMove();

        if (selected == null) {
            if (ownPiece) {
                selected = square;
                messageLabel.setText(" ");
            } else {
                messageLabel.setText("Pick one of " + name(game.sideToMove()) + "'s pieces.");
            }
        } else if (square.equals(selected)) {
            selected = null;
        } else if (ownPiece) {
            selected = square;
        } else {
            String notation = selected.toString() + square;
            selected = null;
            messageLabel.setText(" ");
            controller.handle(new PlayerAction.Move(notation));
        }
        boardPanel.repaint();
    }

    // ---------------------------------------------------------------- animation

    /** Starts the capture animation if the last render added a capture. */
    private void noticeNewMove(Game game) {
        List<Move> history = game.history();
        if (history.size() == lastHistorySize + 1 && history.getLast().isCapture()) {
            animating = history.getLast();
            animationStart = System.currentTimeMillis();
            animationTimer.restart();
        } else {
            stopAnimation();
        }
        lastHistorySize = history.size();
    }

    private double animationProgress() {
        double t = (System.currentTimeMillis() - animationStart) / (double) CAPTURE_MILLIS;
        return Math.min(1.0, Math.max(0.0, t));
    }

    private void onAnimationTick() {
        if (animationProgress() >= 1.0) {
            stopAnimation();
        }
        if (boardPanel != null) {
            boardPanel.repaint();
        }
    }

    private void stopAnimation() {
        animating = null;
        animationTimer.stop();
    }

    // ------------------------------------------------------------------ drawing

    private final class BoardPanel extends JPanel {

        private final Font pieceFont = chooseFont();

        BoardPanel() {
            setPreferredSize(new Dimension(SQUARE * 8, SQUARE * 8));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Position square = squareAt(e.getX(), e.getY());
                    if (square != null && game != null) {
                        onSquareClicked(square);
                    }
                }
            });
        }

        private int squareSize() {
            return Math.min(getWidth(), getHeight()) / 8;
        }

        /** Screen rows count down from the top; ranks count up from white's side. */
        private Position squareAt(int x, int y) {
            int size = squareSize();
            int column = x / size;
            int row = y / size;
            if (column < 0 || column > 7 || row < 0 || row > 7) {
                return null;
            }
            return new Position(column, 7 - row);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (game == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int size = squareSize();
            Board board = game.board();
            List<Move> history = game.history();
            Move last = history.isEmpty() ? null : history.getLast();
            List<Move> targets = selected == null ? List.of()
                    : game.legalMoves().stream().filter(m -> m.from().equals(selected)).toList();
            Position checkedKing = game.status() == GameStatus.CHECK || game.status() == GameStatus.CHECKMATE
                    ? board.kingPosition(game.sideToMove()) : null;
            Move anim = animating;

            for (int row = 0; row < 8; row++) {
                for (int column = 0; column < 8; column++) {
                    Position square = new Position(column, 7 - row);
                    int x = column * size;
                    int y = row * size;

                    g2.setColor((column + row) % 2 == 0 ? LIGHT : DARK);
                    g2.fillRect(x, y, size, size);

                    if (last != null && (square.equals(last.from()) || square.equals(last.to()))) {
                        fill(g2, LAST_MOVE, x, y, size);
                    }
                    if (square.equals(selected)) {
                        fill(g2, SELECTED, x, y, size);
                    }
                    if (square.equals(checkedKing)) {
                        fill(g2, IN_CHECK, x, y, size);
                    }

                    drawCoordinates(g2, square, row, column, x, y, size);

                    // During a capture animation the arriving piece is drawn
                    // separately, sliding in, so skip it on its destination square.
                    Piece piece = board.pieceAt(square);
                    if (piece != null && !(anim != null && square.equals(anim.to()))) {
                        drawPiece(g2, piece, x, y, size);
                    }
                }
            }

            g2.setColor(TARGET);
            for (Move move : targets) {
                int x = move.to().file() * size;
                int y = (7 - move.to().rank()) * size;
                if (board.pieceAt(move.to()) == null) {
                    int d = size / 4;
                    g2.fillOval(x + (size - d) / 2, y + (size - d) / 2, d, d);
                } else {
                    g2.setStroke(new BasicStroke(Math.max(3f, size / 14f)));
                    int inset = size / 16;
                    g2.drawOval(x + inset, y + inset, size - 2 * inset, size - 2 * inset);
                }
            }

            if (anim != null) {
                drawCapture(g2, anim, size);
            }
        }

        /**
         * One frame of a capture: the victim shrinks, spins and fades out with
         * a burst of rays, while the attacker slides from its old square onto
         * the target.
         */
        private void drawCapture(Graphics2D g2, Move move, int size) {
            double t = animationProgress();
            double ease = 1 - Math.pow(1 - t, 3);
            int tx = move.to().file() * size;
            int ty = (7 - move.to().rank()) * size;
            double cx = tx + size / 2.0;
            double cy = ty + size / 2.0;

            // Victim: spin, shrink and fade over the first two thirds.
            double fade = Math.max(0.0, 1.0 - t * 1.5);
            if (fade > 0 && move.captured() != null) {
                Graphics2D v = (Graphics2D) g2.create();
                v.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, (float) fade));
                double scale = 0.4 + 0.6 * fade;
                v.rotate(t * Math.PI, cx, cy);
                v.translate(cx, cy);
                v.scale(scale, scale);
                v.translate(-cx, -cy);
                drawPiece(v, move.captured(), tx, ty, size);
                v.dispose();
            }

            // Burst of rays from the centre of the square.
            Graphics2D b = (Graphics2D) g2.create();
            float alpha = (float) Math.max(0.0, 1.0 - t);
            b.setColor(new java.awt.Color(1f, 0.55f, 0.1f, alpha));
            b.setStroke(new BasicStroke(Math.max(2f, size / 20f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double inner = size * 0.15 + size * 0.25 * ease;
            double outer = inner + size * 0.2 * (1 - t);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                b.drawLine((int) (cx + Math.cos(angle) * inner), (int) (cy + Math.sin(angle) * inner),
                        (int) (cx + Math.cos(angle) * outer), (int) (cy + Math.sin(angle) * outer));
            }
            b.dispose();

            // Attacker: what now stands on the target (a queen after a promotion).
            Piece arriving = game.board().pieceAt(move.to());
            if (arriving != null) {
                int fx = move.from().file() * size;
                int fy = (7 - move.from().rank()) * size;
                int x = (int) Math.round(fx + (tx - fx) * ease);
                int y = (int) Math.round(fy + (ty - fy) * ease);
                drawPiece(g2, arriving, x, y, size);
            }
        }

        private void fill(Graphics2D g2, java.awt.Color color, int x, int y, int size) {
            g2.setColor(color);
            g2.fillRect(x, y, size, size);
        }

        private void drawCoordinates(Graphics2D g2, Position square, int row, int column,
                                     int x, int y, int size) {
            g2.setFont(getFont().deriveFont(Font.BOLD, size / 6f));
            g2.setColor((column + row) % 2 == 0 ? DARK : LIGHT);
            FontMetrics fm = g2.getFontMetrics();
            if (column == 0) {
                g2.drawString(String.valueOf(square.rank() + 1), x + 3, y + fm.getAscent());
            }
            if (row == 7) {
                String file = String.valueOf((char) ('a' + square.file()));
                g2.drawString(file, x + size - fm.stringWidth(file) - 3, y + size - 3);
            }
        }

        /**
         * Every piece is drawn as the solid figure, filled white or black and
         * outlined in the opposite colour, so it stands out on light and dark
         * squares alike.
         */
        private void drawPiece(Graphics2D g2, Piece piece, int x, int y, int size) {
            boolean white = piece.color() == Color.WHITE;
            String text = pieceFont == null ? String.valueOf(piece.symbol()) : solidFigure(piece);
            Font font = (pieceFont == null ? getFont().deriveFont(Font.BOLD) : pieceFont)
                    .deriveFont(size * 0.78f);

            FontRenderContext frc = g2.getFontRenderContext();
            GlyphVector glyphs = font.createGlyphVector(frc, text);
            java.awt.geom.Rectangle2D bounds = glyphs.getVisualBounds();
            double tx = x + (size - bounds.getWidth()) / 2 - bounds.getX();
            double ty = y + (size - bounds.getHeight()) / 2 - bounds.getY();
            Shape shape = AffineTransform.getTranslateInstance(tx, ty)
                    .createTransformedShape(glyphs.getOutline());

            // The figure with its holes filled in, so the body is one solid colour.
            Shape silhouette = silhouette(shape);
            float stroke = Math.max(1.2f, size / 48f);
            if (white) {
                g2.setColor(java.awt.Color.WHITE);
                g2.fill(silhouette);
                g2.setColor(java.awt.Color.BLACK);
                g2.setStroke(new BasicStroke(stroke));
                g2.draw(shape);
            } else {
                g2.setColor(new java.awt.Color(25, 25, 25));
                g2.fill(silhouette);
                g2.setColor(new java.awt.Color(235, 235, 235));
                g2.setStroke(new BasicStroke(stroke * 1.4f));
                g2.draw(silhouette);
                g2.setColor(new java.awt.Color(120, 120, 120));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(shape);
            }
        }

        /** The union of every contour of a glyph: its outline with the holes filled. */
        private static Shape silhouette(Shape shape) {
            java.awt.geom.Area area = new java.awt.geom.Area();
            java.awt.geom.Path2D.Double contour = new java.awt.geom.Path2D.Double();
            double[] c = new double[6];
            for (java.awt.geom.PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
                switch (it.currentSegment(c)) {
                    case java.awt.geom.PathIterator.SEG_MOVETO -> {
                        area.add(new java.awt.geom.Area(contour));
                        contour = new java.awt.geom.Path2D.Double();
                        contour.moveTo(c[0], c[1]);
                    }
                    case java.awt.geom.PathIterator.SEG_LINETO -> contour.lineTo(c[0], c[1]);
                    case java.awt.geom.PathIterator.SEG_QUADTO -> contour.quadTo(c[0], c[1], c[2], c[3]);
                    case java.awt.geom.PathIterator.SEG_CUBICTO ->
                            contour.curveTo(c[0], c[1], c[2], c[3], c[4], c[5]);
                    default -> contour.closePath();
                }
            }
            area.add(new java.awt.geom.Area(contour));
            return area;
        }

        private static String solidFigure(Piece piece) {
            return switch (piece.type()) {
                case KING -> "♚";
                case QUEEN -> "♛";
                case ROOK -> "♜";
                case BISHOP -> "♝";
                case KNIGHT -> "♞";
                case PAWN -> "♟";
            };
        }

        /** A font that has the chess figures, or null to fall back to letters. */
        private static Font chooseFont() {
            String[] names = {"Segoe UI Symbol", "DejaVu Sans", "Apple Symbols",
                    "Noto Sans Symbols2", "Arial Unicode MS", Font.SERIF, Font.SANS_SERIF};
            for (String name : names) {
                Font font = new Font(name, Font.PLAIN, 48);
                if (font.canDisplayUpTo("♚♛♜♝♞♟") == -1) {
                    return font;
                }
            }
            return null;
        }
    }
}
