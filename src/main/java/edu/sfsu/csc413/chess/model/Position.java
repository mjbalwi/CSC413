package edu.sfsu.csc413.chess.model;


import java.util.HashMap;

/**
 * A single square on the chess board, identified by file and rank.
 *
 * <p>
 * file is the horizontal coordinate (the letters a-h), rank is the
 * vertical coordinate (the numbers 1-8), both stored 0-based.
 */
public record Position(int file, int rank) {

    /** The number of files and ranks on a standard chess board. */
    public static final int BOARD_SIZE = 8;

    /**
     * Whether the given file/rank coordinates fall within the board.
     * Returns {@code true} when both are between 0 (inclusive) and
     * {@link #BOARD_SIZE} (exclusive), {@code false} otherwise.
     */
    public static boolean isOnBoard(int file, int rank) {
        return  file >= 0 &&
            file <BOARD_SIZE &&
            rank >= 0 &&
            rank < BOARD_SIZE;
    }

    /**
     * Validates the coordinates given to the canonical constructor.
     * Throws {@link IllegalArgumentException} if either falls off the
     * board; otherwise the record is constructed normally.
     */
    public Position {
        if (!isOnBoard(file, rank)) {
            throw new IllegalArgumentException(
                "Position off board: file=" + file + ", rank=" + rank);
        }
    }

    /**
     * Parses a square given in algebraic notation, e.g. {@code "e4"}.
     * The first character is the file letter (a-h) and the second is the
     * rank digit (1-8). Returns the corresponding {@code Position}.
     */
    public static Position parse(String algebraic) {
        char c = algebraic.charAt(0);

        int file = (int) c - 'a';
        int rank = algebraic.charAt(1) - '1';

        return new Position(file, rank);
    }

    /**
     * The square reached by shifting this one by the given file/rank
     * deltas. Returns the resulting {@code Position}, or {@code null}
     * if that square would fall off the board.
     */
    public Position offsetOrNull(int fileDelta, int rankDelta) {

        int newFile = file + fileDelta;
        int newRank = rank + rankDelta;

        if (!(isOnBoard(newFile, newRank))) {
            return null;
        } else {
            return new Position(fileDelta, rankDelta);
        }
    }

    /**
     * The algebraic notation for this square, e.g. {@code "e4"}.
     * Returns a two-character string: the file letter (a-h) followed by
     * the rank digit (1-8).
     */
    public String toString() {
        return "" + (char) ('a' + file) + (char)('1' + rank);
    }
}
