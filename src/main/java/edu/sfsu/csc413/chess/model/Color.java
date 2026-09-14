package edu.sfsu.csc413.chess.model;

/**
 * The two sides in a game of chess.
 *
 * <p>An enum rather than a boolean or an int: the compiler now rejects
 * meaningless values, and {@code switch} statements over it can be checked for
 * exhaustiveness.
 *
 * <p>The four method contracts below are fixed — later milestones call them.
 * The bodies are yours to write: that is M0b.
 */
public enum Color {
    WHITE,
    BLACK;


    /**
     * The side whose turn it is after this one moves.
     * Returns {@link #BLACK} if this is {@link #WHITE}, and vice versa.
     */
    public Color opposite() {
        return (this == Color.WHITE) ? Color.BLACK : Color.WHITE;
    }

    /**
     * The direction pawns of this color advance, measured in ranks.
     * Returns {@code +1} for {@link #WHITE} (moving up the board) and
     * {@code -1} for {@link #BLACK} (moving down the board).
     */
    public int pawnDirection() {
        return (this == Color.WHITE) ? 1 : -1;
    }

    /**
     * The rank pawns of this color start on (0-based).
     * Returns {@code 1} for {@link #WHITE} and {@code 6} for
     * {@link #BLACK}.
     */
    public int pawnStartRank() {
        return (this == Color.WHITE) ? 1 : 6;
    }

    /**
     * The rank a pawn of this color must reach to promote (0-based).
     * Returns {@code 7} for {@link #WHITE} and {@code 0} for
     * {@link #BLACK}.
     */
    public int promotionRank() {
        return (this == Color.WHITE) ? 7 : 0;
    }
}
