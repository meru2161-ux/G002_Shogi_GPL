package com.melapplyworks.g002shogi.model

/** Canonical standard setup shared by the UI, the local engine and tests. */
object ShogiPositions {
    fun initial(): ShogiPosition {
        val board = mutableMapOf<Square, Piece>()
        fun place(rank: Int, file: Int, type: PieceType, owner: Player) {
            board[Square(file, rank)] = Piece(type, owner)
        }
        val back = listOf(
            PieceType.LANCE, PieceType.KNIGHT, PieceType.SILVER, PieceType.GOLD, PieceType.KING,
            PieceType.GOLD, PieceType.SILVER, PieceType.KNIGHT, PieceType.LANCE
        )
        back.forEachIndexed { index, type ->
            place(1, 9 - index, type, Player.GOTE)
            place(9, 9 - index, type, Player.SENTE)
        }
        place(2, 2, PieceType.BISHOP, Player.GOTE)
        place(2, 8, PieceType.ROOK, Player.GOTE)
        place(8, 8, PieceType.BISHOP, Player.SENTE)
        place(8, 2, PieceType.ROOK, Player.SENTE)
        (1..9).forEach { file ->
            place(3, file, PieceType.PAWN, Player.GOTE)
            place(7, file, PieceType.PAWN, Player.SENTE)
        }
        return ShogiPosition(board)
    }
}

object ShogiMoveText {
    fun display(move: Move): String = buildString {
        append(if (move.player == Player.SENTE) "▲" else "△")
        append(move.to.japanese)
        append(move.piece.label)
        if (move.isDrop) append("打")
        if (move.promote) append("成")
    }
}
