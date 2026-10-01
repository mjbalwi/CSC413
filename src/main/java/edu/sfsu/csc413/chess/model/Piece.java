package edu.sfsu.csc413.chess.model;

public class Piece {
    private final Color color;
    private final PieceType type;

    public Piece(Color color, PieceType type) {
        this.color = color;
        this.type = type;
    }
    public Color color() {
        return this.color;
    }
    public PieceType type() {
        return this.type;
    }

    public char symbol() {
        return color == Color.WHITE
            ? type.symbol()
            : Character.toLowerCase(type.symbol());
    }

    @Override
    public String toString() {
        return String.valueOf(
            color == Color.WHITE
            ? type.symbol()
            : Character.toLowerCase(type.symbol())
        );
    }
}
