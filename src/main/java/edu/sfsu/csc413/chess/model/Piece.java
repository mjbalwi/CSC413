package edu.sfsu.csc413.chess.model;

import java.util.ArrayList;
import java.util.List;

public abstract class Piece {

	private final Color color;
	private final PieceType type;

	protected Piece(Color color, PieceType type) {
		this.color = color;
		this.type = type;
	}

	public abstract List<Move> pseudoLegalMoves(Board board, Position from);

	public boolean attacks(Board board, Position from, Position target) {
        for (Move move : pseudoLegalMoves(board, from)) {
            if (move.to().equals(target)) {
                return true;
            }
        }

        return false;
    }

	protected List<Move> slidingMoves(Board board, Position from, int[][] directions) {
        Piece piece = board.pieceAt(from);
		List<Move> moves = new ArrayList<>();

		for (int[] direction : directions) {
            Position current = from;

            while (true) {
				Position newPosition = current.offsetOrNull(direction[0], direction[1]);
				if (newPosition == null) {
					break;
				}

				Piece target = board.pieceAt(newPosition);
				Move move;

				if (target == null) {
					move = Move.quiet(from, newPosition, piece);
				} else if (target.color() != piece.color()) {
					move = Move.capture(from, newPosition, piece, board.pieceAt(newPosition));
                    moves.add(move);
                    break;
				} else {
					break;
				}

				moves.add(move);
                current = newPosition;
			}
		}
		return moves;
	}

	protected List<Move> steppingMoves(Board board, Position from, int[][] offsets) {

		Piece piece = board.pieceAt(from);
		List<Move> moves = new ArrayList<>();

		for (int[] offset : offsets) {
			Position newPosition = from.offsetOrNull(offset[0], offset[1]);
			if (newPosition == null) {
				continue;
			}

			Piece target = board.pieceAt(newPosition);
			Move move;

			if (target == null) {
				move = Move.quiet(from, newPosition, piece);
			} else if (target.color() != piece.color()) {
				move = Move.capture(from, newPosition, piece, target);
			} else {
				continue;
			}

			moves.add(move);
		}

		return moves;
	}

	public Color color() {
		return this.color;
	}

	public PieceType type() {
		return this.type;
	}

	public char symbol() {
		return color == Color.WHITE ? type.symbol() : Character.toLowerCase(type.symbol());
	}

	@Override
	public String toString() {
		return String.valueOf(color == Color.WHITE ? type.symbol() : Character.toLowerCase(type.symbol()));
	}
}
