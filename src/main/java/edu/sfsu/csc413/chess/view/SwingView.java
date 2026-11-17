package edu.sfsu.csc413.chess.view;

import edu.sfsu.csc413.chess.engine.Game;
import edu.sfsu.csc413.chess.engine.GameStatus;
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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A Swing window: click a piece, then click the square to move it to.
 *
 * <p>This view is event-driven. {@link #start} builds the window and returns;
 * it never hands control to the controller's ask-and-wait loop. Every click becomes a
 * {@link PlayerAction} handed to {@link GameController#handle}, and the
 * controller calls {@link #render} and {@link #showMessage} back.
 *
 * <p>The view decides nothing about chess. The squares it highlights come
 * from {@link Game#legalMoves()}, the check/mate/stalemate text comes from
 * {@link Game#status()}, and an illegal click is still sent to the controller
 * so that the controller is the one that says "Not a legal move". A promotion
 * is sent as four characters (e.g. {@code e7e8}); the controller then asks
 * {@link BoardView#promotionChoice}, whose default is a queen.
 */
public class SwingView implements BoardView {

    private static final java.awt.Color LIGHT = new java.awt.Color(240, 217, 181);
    private static final java.awt.Color DARK = new java.awt.Color(181, 136, 99);
    private static final java.awt.Color SELECTED = new java.awt.Color(246, 246, 105, 200);
    private static final java.awt.Color LAST_MOVE = new java.awt.Color(205, 210, 106, 150);
    private static final java.awt.Color TARGET_DOT = new java.awt.Color(20, 85, 30, 110);
    private static final java.awt.Color CHECK_GLOW = new java.awt.Color(220, 30, 30, 170);
    private static final java.awt.Color WHITE_FILL = new java.awt.Color(255, 255, 255);
    private static final java.awt.Color WHITE_EDGE = new java.awt.Color(25, 25, 25);
    private static final java.awt.Color BLACK_FILL = new java.awt.Color(20, 20, 20);
    private static final java.awt.Color BLACK_EDGE = new java.awt.Color(235, 235, 235);

    /** How long the capture animation runs, and how often it repaints. */
    private static final int CAPTURE_MILLIS = 450;
    private static final int FRAME_MILLIS = 15;

    private GameController controller;
    private JFrame frame;
    private BoardPanel boardPanel;
    private JLabel turnLabel;
    private JLabel messageLabel;

    /** The latest game handed to {@link #render}; read only on Swing's thread. */
    private Game game;

    /** First click of a half-finished move, or null when nothing is selected. */
    private Position selected;

    /** History length at the last render, to spot a move that just happened. */
    private int lastHistorySize;

    /** The capture being animated, or null, and when it started. */
    private Move animating;
    private long animationStart;
    private final Timer animationTimer = new Timer(FRAME_MILLIS, e -> onAnimationTick());

    /** Messages that arrived before the window existed (e.g. the welcome). */
    private final List<String> pendingMessages = new ArrayList<>();

    @Override
    public void start(GameController controller) {
        this.controller = controller;
        SwingUtilities.invokeLater(this::buildWindow);
    }

    @Override
    public void render(Game game) {
        onSwingThread(() -> {
            this.game = game;
            noticeNewMove(game);
            if (turnLabel != null) {
                turnLabel.setText(describe(game));
                boardPanel.repaint();
            }
        });
    }

    @Override
    public void showMessage(String message) {
        onSwingThread(() -> {
            if (messageLabel == null) {
                pendingMessages.add(message);
            } else {
                messageLabel.setText(message);
            }
        });
    }

    // ---------------------------------------------------------------- window

    private void buildWindow() {
        frame = new JFrame("CSC 413 Chess");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        boardPanel = new BoardPanel();
        boardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                onClick(event.getX(), event.getY());
            }
        });

        turnLabel = new JLabel(" ");
        turnLabel.setFont(turnLabel.getFont().deriveFont(Font.BOLD, 16f));
        messageLabel = new JLabel(" ");
        messageLabel.setFont(messageLabel.getFont().deriveFont(14f));

        JButton undo = new JButton("Undo");
        undo.addActionListener(e -> {
            selected = null;
            messageLabel.setText(" ");
            controller.handle(new PlayerAction.Undo());
        });

        JPanel labels = new JPanel(new GridLayout(2, 1));
        labels.add(turnLabel);
        labels.add(messageLabel);

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        south.add(labels, BorderLayout.CENTER);
        south.add(undo, BorderLayout.EAST);

        frame.add(boardPanel, BorderLayout.CENTER);
        frame.add(south, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);

        if (game == null) {
            game = controller.game();
        }
        lastHistorySize = game.history().size();
        turnLabel.setText(describe(game));
        for (String message : pendingMessages) {
            messageLabel.setText(message);
        }
        pendingMessages.clear();

        frame.setVisible(true);
    }

    private static String describe(Game game) {
        String side = game.sideToMove() == edu.sfsu.csc413.chess.model.Color.WHITE ? "White" : "Black";
        GameStatus status = game.status();
        return switch (status) {
            case IN_PROGRESS -> side + " to move";
            case CHECK -> side + " to move — CHECK!";
            case CHECKMATE -> "Checkmate — " + side + " has no moves";
            case STALEMATE -> "Stalemate — draw";
        };
    }

    // ----------------------------------------------------------------- input

    /**
     * First click picks a piece of the side to move; second click sends the
     * move. Clicking another of your own pieces switches the selection;
     * clicking the selected square again cancels it. Whether the move is legal
     * is the controller's call, not ours.
     */
    private void onClick(int x, int y) {
        if (game == null || animating != null) {
            return;
        }
        Position clicked = boardPanel.squareAt(x, y);
        if (clicked == null) {
            return;
        }
        Piece piece = game.board().pieceAt(clicked);
        boolean ownPiece = piece != null && piece.color() == game.sideToMove();

        if (selected == null) {
            if (ownPiece) {
                selected = clicked;
                messageLabel.setText(" ");
            } else {
                messageLabel.setText("Pick one of " + game.sideToMove() + "'s pieces first.");
            }
        } else if (clicked.equals(selected)) {
            selected = null;
        } else if (ownPiece) {
            selected = clicked;
        } else {
            String notation = selected.toString() + clicked;
            selected = null;
            messageLabel.setText(" ");
            controller.handle(new PlayerAction.Move(notation));
        }
        boardPanel.repaint();
    }

    /** The squares the selected piece may move to, read from the game. */
    private List<Position> targetsOf(Position from) {
        List<Position> targets = new ArrayList<>();
        for (Move move : game.legalMoves()) {
            if (move.from().equals(from)) {
                targets.add(move.to());
            }
        }
        return targets;
    }

    // ------------------------------------------------------------- animation

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

    // --------------------------------------------------------------- drawing

    private final class BoardPanel extends JPanel {

        private Font pieceFont;

        BoardPanel() {
            setPreferredSize(new Dimension(8 * 72, 8 * 72));
            setFocusable(true);
        }

        /** Side of one square in pixels, and the board's top-left corner. */
        private int square() {
            return Math.max(1, Math.min(getWidth(), getHeight()) / Position.BOARD_SIZE);
        }

        private int originX() {
            return (getWidth() - square() * Position.BOARD_SIZE) / 2;
        }

        private int originY() {
            return (getHeight() - square() * Position.BOARD_SIZE) / 2;
        }

        /** Screen rows count down, ranks count up: row 0 is rank 8. */
        Position squareAt(int x, int y) {
            int column = Math.floorDiv(x - originX(), square());
            int row = Math.floorDiv(y - originY(), square());
            int file = column;
            int rank = Position.BOARD_SIZE - 1 - row;
            return Position.isOnBoard(file, rank) ? new Position(file, rank) : null;
        }

        private int xOf(Position p) {
            return originX() + p.file() * square();
        }

        private int yOf(Position p) {
            return originY() + (Position.BOARD_SIZE - 1 - p.rank()) * square();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (game == null) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int size = square();
            pieceFont = pieceFont(size);
            List<Move> history = game.history();
            Move last = history.isEmpty() ? null : history.getLast();

            // Squares and highlights.
            for (int rank = 0; rank < Position.BOARD_SIZE; rank++) {
                for (int file = 0; file < Position.BOARD_SIZE; file++) {
                    Position p = new Position(file, rank);
                    g.setColor((file + rank) % 2 == 0 ? DARK : LIGHT);
                    g.fillRect(xOf(p), yOf(p), size, size);
                    if (last != null && (p.equals(last.from()) || p.equals(last.to()))) {
                        g.setColor(LAST_MOVE);
                        g.fillRect(xOf(p), yOf(p), size, size);
                    }
                    if (p.equals(selected)) {
                        g.setColor(SELECTED);
                        g.fillRect(xOf(p), yOf(p), size, size);
                    }
                }
            }

            // A red glow under the king that is in check or mated.
            GameStatus status = game.status();
            if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE) {
                Position king = game.board().kingPosition(game.sideToMove());
                if (king != null) {
                    g.setColor(CHECK_GLOW);
                    g.setStroke(new BasicStroke(Math.max(3f, size / 14f)));
                    g.drawOval(xOf(king) + size / 10, yOf(king) + size / 10,
                            size * 8 / 10, size * 8 / 10);
                }
            }

            // Pieces. During a capture animation the arriving piece is drawn
            // separately, sliding in, so skip it on its destination square.
            Move anim = animating;
            for (int rank = 0; rank < Position.BOARD_SIZE; rank++) {
                for (int file = 0; file < Position.BOARD_SIZE; file++) {
                    Position p = new Position(file, rank);
                    if (anim != null && p.equals(anim.to())) {
                        continue;
                    }
                    Piece piece = game.board().pieceAt(p);
                    if (piece != null) {
                        drawPiece(g, piece, xOf(p), yOf(p), size, 1.0, 1.0);
                    }
                }
            }

            // Legal-move markers for the selected piece.
            if (selected != null) {
                for (Position target : targetsOf(selected)) {
                    g.setColor(TARGET_DOT);
                    if (game.board().pieceAt(target) != null) {
                        g.setStroke(new BasicStroke(Math.max(3f, size / 12f)));
                        g.drawOval(xOf(target) + 3, yOf(target) + 3, size - 6, size - 6);
                    } else {
                        int d = size / 3;
                        g.fillOval(xOf(target) + (size - d) / 2, yOf(target) + (size - d) / 2, d, d);
                    }
                }
            }

            if (anim != null) {
                drawCapture(g, anim, size);
            }
            g.dispose();
        }

        /**
         * One frame of a capture: the victim shrinks, spins and fades out with
         * a burst of rays, while the attacker slides from its old square onto
         * the target.
         */
        private void drawCapture(Graphics2D g, Move move, int size) {
            double t = animationProgress();
            double ease = 1 - Math.pow(1 - t, 3);
            int tx = xOf(move.to());
            int ty = yOf(move.to());

            // Victim: fade and shrink over the first two thirds.
            double fade = Math.max(0.0, 1.0 - t * 1.5);
            if (fade > 0) {
                Graphics2D v = (Graphics2D) g.create();
                v.rotate(t * Math.PI, tx + size / 2.0, ty + size / 2.0);
                drawPiece(v, move.captured(), tx, ty, size, 0.4 + 0.6 * fade, fade);
                v.dispose();
            }

            // Burst of rays from the centre of the square.
            Graphics2D b = (Graphics2D) g.create();
            float alpha = (float) Math.max(0.0, 1.0 - t);
            b.setColor(new java.awt.Color(1f, 0.55f, 0.1f, alpha));
            b.setStroke(new BasicStroke(Math.max(2f, size / 20f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double cx = tx + size / 2.0;
            double cy = ty + size / 2.0;
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
                int x = (int) Math.round(xOf(move.from()) + (tx - xOf(move.from())) * ease);
                int y = (int) Math.round(yOf(move.from()) + (ty - yOf(move.from())) * ease);
                drawPiece(g, arriving, x, y, size, 1.0, 1.0);
            }
        }

        /**
         * Draws one piece as a solid figure with a contrasting outline, so
         * both colours stand out on both square colours.
         */
        private void drawPiece(Graphics2D g, Piece piece, int x, int y, int size,
                               double scale, double opacity) {
            if (piece == null || opacity <= 0) {
                return;
            }
            boolean white = piece.color() == edu.sfsu.csc413.chess.model.Color.WHITE;
            FontRenderContext frc = g.getFontRenderContext();
            TextLayout layout = new TextLayout(glyph(piece), pieceFont, frc);
            Rectangle2D bounds = layout.getBounds();

            AffineTransform at = new AffineTransform();
            at.translate(x + size / 2.0, y + size / 2.0);
            at.scale(scale, scale);
            at.translate(-bounds.getCenterX(), -bounds.getCenterY());
            Shape shape = layout.getOutline(at);

            Graphics2D p = (Graphics2D) g.create();
            p.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, (float) opacity));
            p.setStroke(new BasicStroke(Math.max(1.5f, size / 30f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            p.setColor(white ? WHITE_EDGE : BLACK_EDGE);
            p.draw(shape);
            p.setColor(white ? WHITE_FILL : BLACK_FILL);
            p.fill(shape);
            p.dispose();
        }

        /** The solid (black) figure for every piece; colour comes from the fill. */
        private String glyph(Piece piece) {
            return switch (piece.type()) {
                case KING -> "♚";
                case QUEEN -> "♛";
                case ROOK -> "♜";
                case BISHOP -> "♝";
                case KNIGHT -> "♞";
                case PAWN -> "♟";
            };
        }

        private Font pieceFont(int size) {
            float points = size * 0.72f;
            if (pieceFont != null && pieceFont.getSize2D() == points) {
                return pieceFont;
            }
            String[] candidates = {"Segoe UI Symbol", "DejaVu Sans", "Apple Symbols",
                    "Noto Sans Symbols2", "FreeSerif", Font.SERIF, Font.DIALOG};
            for (String name : candidates) {
                Font font = new Font(name, Font.PLAIN, 12);
                if (font.canDisplay('♚') && font.canDisplay('♟')) {
                    return font.deriveFont(points);
                }
            }
            return new Font(Font.DIALOG, Font.PLAIN, 12).deriveFont(points);
        }
    }

    /** Runs on Swing's thread: right away if already there, otherwise later. */
    private static void onSwingThread(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
