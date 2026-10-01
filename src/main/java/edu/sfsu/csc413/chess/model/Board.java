package edu.sfsu.csc413.chess.model;

import java.util.ArrayList;
import java.util.List;

public class Board {
    // [file][rank]
    private final Piece[][] squares = new Piece[Position.BOARD_SIZE][Position.BOARD_SIZE];

    public Board() {
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
