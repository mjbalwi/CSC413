package edu.sfsu.csc413.chess.model;

import java.util.ArrayList;
import java.util.List;

public class Board {
    // [file][rank]
    private final Piece[][] squares = new Piece[Position.BOARD_SIZE][Position.BOARD_SIZE];

    private static Piece create(PieceType type, Color color) {
        return switch (type) {
            case PAWN -> new Pawn(color);
            case KNIGHT -> new Knight(color);
            case BISHOP -> new Bishop(color);
            case ROOK -> new Rook(color);
            case QUEEN -> new Queen(color);
            case KING -> new King(color);
        };
    }


    public Board() {
    }



    /** PROMOTION WRINKLE NOTE:
     *
     * I chose to create a new private switch method in Board because I want to
     * maintain a consistent dependency direction. Typically, higher level code
     * depends on lower level code so in this case, factory depends on model
     * (PieceFactory --> Board) however by using the helper method in PieceFactory
     * this creates a circular dependency. This creates potential problems later
     * when the project gets bigger such as tight coupling which makes it harder
     * to edit one class without unintentionally affecting the other.
     *
     * This does mean I will have to copy-paste the same exact code found in PieceFactory,
     * but it is only a couple of lines which is a tradeoff I'll take to avoid tight
     * coupling.
     */

    public void apply(Move move) {
        squares[move.from().file()][move.from().rank()] = null;
        if (move.isPromotion()) {
            Piece promotedPiece = create(move.promotesTo(), move.moved().color());
            squares[move.to().file()][move.to().rank()] = promotedPiece;
        } else {
            squares[move.to().file()][move.to().rank()] = move.moved();
        }
    }

    /**
     * misc:
     * Move(Position from, Position to, Piece moved, Piece captured,
     *  PieceType promotesTo)
     *
     *  Position(int file, int rank)
     */


    public void undo(Move move) {
        squares[move.to().file()][move.to().rank()] = move.captured();
        squares[move.from().file()][move.from().rank()] = move.moved();
    }

    public Piece pieceAt(Position position) {
        return squares[position.file()][position.rank()];
    }

    public boolean isEmpty(Position position) {
        return squares[position.file()][position.rank()] == null;
    }

    public void place(Position position, Piece piece) {
        squares[position.file()][position.rank()] = piece;
    }

    public List<Position> positionsOf(Color color) {
        List<Position> positions = new ArrayList<>();

        for (int file = 0; file < Position.BOARD_SIZE; file++) {
            for (int rank = 0; rank < Position.BOARD_SIZE; rank++) {
                if (squares[file][rank] != null && squares[file][rank].color() == color ) {
                    positions.add(new Position(file, rank));
                }
            }
        }

        return positions;
    }

    @Override
    public String toString() {
        String result = "";

        for (int rank = 7; rank >= 0; rank--) {
            int emptyCount = 0;

            for (int file = 0; file < Position.BOARD_SIZE; file++) {
                Position pos = new Position(file, rank);

                if (isEmpty(pos)) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        result += emptyCount;
                        emptyCount = 0;
                    }

                    result += pieceAt(pos).symbol();
                }
            }

            if (emptyCount > 0) {
                result += emptyCount;
            }

            if (rank > 0) {
                result += "/";
            }
        }

        return result;
    }

}
