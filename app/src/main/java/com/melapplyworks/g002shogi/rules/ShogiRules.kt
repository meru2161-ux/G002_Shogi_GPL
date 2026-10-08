package com.melapplyworks.g002shogi.rules

import com.melapplyworks.g002shogi.model.*

/** Pure rule layer. UI and analysis engines consume its legal moves rather than duplicate rules. */
object ShogiRules {
    fun legalMoves(position: ShogiPosition, player: Player = position.activePlayer): List<Move> =
        pseudoMoves(position, player).filter { move ->
            val next = applyUnchecked(position, move)
            !isInCheck(next, player) && !(move.isDrop && move.piece == PieceType.PAWN && isPawnDropMate(next, player))
        }

    fun isLegal(position: ShogiPosition, move: Move): Boolean =
        !(move.isDrop && move.promote) &&
            move.player == position.activePlayer &&
            legalMoves(position, move.player).any { it.sameAction(move) }

    fun apply(position: ShogiPosition, move: Move): ShogiPosition {
        require(isLegal(position, move)) { "Illegal shogi move: $move" }
        return applyUnchecked(position, move)
    }

    /**
     * Fast path for callers iterating a list returned by [legalMoves]. Keeping
     * this internal prevents UI/input code from bypassing legality checks while
     * avoiding a complete second move-generation pass at every search node.
     */
    internal fun applyKnownLegal(position: ShogiPosition, move: Move): ShogiPosition = applyUnchecked(position, move)

    fun isInCheck(position: ShogiPosition, player: Player): Boolean =
        position.board.entries.firstOrNull { it.value.owner == player && it.value.type == PieceType.KING }
            ?.let { isSquareAttacked(position, it.key, player.opponent()) } ?: false

    fun isCheckmate(position: ShogiPosition, player: Player = position.activePlayer): Boolean =
        isInCheck(position, player) && legalMoves(position, player).isEmpty()

    private fun Move.sameAction(other: Move) = from == other.from && to == other.to && piece == other.piece && player == other.player && promote == other.promote

    private fun isPawnDropMate(position: ShogiPosition, droppingPlayer: Player): Boolean =
        isInCheck(position, droppingPlayer.opponent()) && pseudoMoves(position, droppingPlayer.opponent()).none {
            !isInCheck(applyUnchecked(position, it), droppingPlayer.opponent())
        }

    private fun pseudoMoves(position: ShogiPosition, player: Player): List<Move> = buildList {
        position.board.filterValues { it.owner == player }.forEach { (from, piece) ->
            destinations(position, from, piece).forEach { to ->
                if (position.board[to]?.owner != player && position.board[to]?.type != PieceType.KING) {
                    val promotionPossible = piece.type.canPromote && (inPromotionZone(from.rank, player) || inPromotionZone(to.rank, player))
                    val mandatory = mustPromote(piece.type, to.rank, player)
                    if (!mandatory) add(Move(from, to, piece.type, player, promote = false))
                    if (promotionPossible) add(Move(from, to, piece.type, player, promote = true))
                }
            }
        }
        position.hands[player].orEmpty().distinct().forEach { type ->
            allSquares.filter { it !in position.board && canDrop(position, type, player, it) }.forEach { to -> add(Move(null, to, type, player)) }
        }
    }

    private fun canDrop(position: ShogiPosition, type: PieceType, player: Player, to: Square): Boolean {
        if (type == PieceType.PAWN && position.board.any { (square, piece) -> square.file == to.file && piece.owner == player && piece.type == PieceType.PAWN }) return false
        return !mustPromote(type, to.rank, player)
    }

    private fun applyUnchecked(position: ShogiPosition, move: Move): ShogiPosition {
        val board = position.board.toMutableMap()
        val hands = position.hands.mapValues { it.value.toMutableList() }.toMutableMap()
        if (move.isDrop) hands.getOrPut(move.player) { mutableListOf() }.remove(move.piece.unpromoted)
        else { move.from?.let(board::remove); board[move.to]?.let { hands.getOrPut(move.player) { mutableListOf() }.add(it.type.unpromoted) } }
        board[move.to] = Piece(if (move.promote) move.piece.promoted else move.piece, move.player)
        return ShogiPosition(board, hands, move.player.opponent())
    }

    private fun destinations(position: ShogiPosition, from: Square, piece: Piece): List<Square> {
        // Direction tuples are written from Sente's viewpoint, where forward is rank -1.
        val forward = if (piece.owner == Player.SENTE) 1 else -1
        fun step(df: Int, dr: Int): Square? {
            val file = from.file + df; val rank = from.rank + dr * forward
            return if (file in 1..9 && rank in 1..9) Square(file, rank) else null
        }
        fun ray(df: Int, dr: Int): List<Square> = buildList {
            var file = from.file + df; var rank = from.rank + dr * forward
            while (file in 1..9 && rank in 1..9) { val square = Square(file, rank); add(square); if (square in position.board) break; file += df; rank += dr * forward }
        }
        return when (piece.type) {
            PieceType.KING -> listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1).mapNotNull { step(it.first, it.second) }
            PieceType.GOLD, PieceType.PROMOTED_SILVER, PieceType.PROMOTED_KNIGHT, PieceType.PROMOTED_LANCE, PieceType.PROMOTED_PAWN -> listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, 0 to 1).mapNotNull { step(it.first, it.second) }
            PieceType.SILVER -> listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 1, 1 to 1).mapNotNull { step(it.first, it.second) }
            PieceType.KNIGHT -> listOf(-1 to -2, 1 to -2).mapNotNull { step(it.first, it.second) }
            PieceType.LANCE -> ray(0, -1)
            PieceType.PAWN -> listOfNotNull(step(0, -1))
            PieceType.ROOK -> ray(0, -1) + ray(0, 1) + ray(-1, 0) + ray(1, 0)
            PieceType.BISHOP -> ray(-1, -1) + ray(1, -1) + ray(-1, 1) + ray(1, 1)
            PieceType.PROMOTED_ROOK -> ray(0, -1) + ray(0, 1) + ray(-1, 0) + ray(1, 0) + listOfNotNull(step(-1, -1), step(1, -1), step(-1, 1), step(1, 1))
            PieceType.PROMOTED_BISHOP -> ray(-1, -1) + ray(1, -1) + ray(-1, 1) + ray(1, 1) + listOfNotNull(step(0, -1), step(0, 1), step(-1, 0), step(1, 0))
        }
    }

    private fun isSquareAttacked(position: ShogiPosition, square: Square, attacker: Player): Boolean = position.board.filterValues { it.owner == attacker }.any { (from, piece) -> destinations(position, from, piece).contains(square) }
    private fun inPromotionZone(rank: Int, player: Player) = if (player == Player.SENTE) rank <= 3 else rank >= 7
    private fun mustPromote(type: PieceType, rank: Int, player: Player) = when (type) {
        PieceType.PAWN, PieceType.LANCE -> if (player == Player.SENTE) rank == 1 else rank == 9
        PieceType.KNIGHT -> if (player == Player.SENTE) rank <= 2 else rank >= 8
        else -> false
    }
    private val allSquares = (1..9).flatMap { file -> (1..9).map { rank -> Square(file, rank) } }
}

enum class RepetitionResult { NONE, DRAW, SENTE_LOSES_BY_PERPETUAL_CHECK, GOTE_LOSES_BY_PERPETUAL_CHECK }

/** Records completed positions; the UI can persist this alongside a study line. */
class RepetitionTracker {
    private data class Snapshot(val position: ShogiPosition, val mover: Player?, val gaveCheck: Boolean)
    private val snapshots = mutableListOf<Snapshot>()

    fun record(position: ShogiPosition, mover: Player?, gaveCheck: Boolean): RepetitionResult {
        snapshots += Snapshot(position, mover, gaveCheck)
        val currentKey = key(position)
        val occurrences = snapshots.indices.filter { key(snapshots[it].position) == currentKey }
        if (occurrences.size < 4) return RepetitionResult.NONE
        val range = snapshots.subList(occurrences[occurrences.size - 4], snapshots.lastIndex + 1)
        return when {
            continuouslyChecked(range, Player.SENTE) -> RepetitionResult.SENTE_LOSES_BY_PERPETUAL_CHECK
            continuouslyChecked(range, Player.GOTE) -> RepetitionResult.GOTE_LOSES_BY_PERPETUAL_CHECK
            else -> RepetitionResult.DRAW
        }
    }

    private fun continuouslyChecked(range: List<Snapshot>, player: Player): Boolean {
        val moves = range.filter { it.mover == player }
        return moves.isNotEmpty() && moves.all { it.gaveCheck }
    }

    private fun key(position: ShogiPosition): String = buildString {
        append(position.activePlayer)
        position.board.toSortedMap(compareBy<Square>({ it.file }, { it.rank })).forEach { (square, piece) -> append("|${square.file}${square.rank}${piece.owner}${piece.type}") }
        Player.entries.forEach { player -> append("/$player:${position.hands[player].orEmpty().sortedBy { it.ordinal }.joinToString()}") }
    }
}
