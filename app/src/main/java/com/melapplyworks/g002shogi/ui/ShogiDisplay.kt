package com.melapplyworks.g002shogi.ui

import com.melapplyworks.g002shogi.model.PieceType
import com.melapplyworks.g002shogi.model.Player
import com.melapplyworks.g002shogi.model.Square

/** Pure display rules shared by Compose and JVM tests. They never alter shogi movement rules. */
object ShogiDisplay {
    val handPieceOrder = listOf(
        PieceType.ROOK,
        PieceType.BISHOP,
        PieceType.GOLD,
        PieceType.SILVER,
        PieceType.KNIGHT,
        PieceType.LANCE,
        PieceType.PAWN
    )

    val fileLabels = (9 downTo 1).map(Int::toString)
    val rankLabels = "一二三四五六七八九".map(Char::toString)

    fun rotationDegrees(owner: Player): Float = if (owner == Player.GOTE) 180f else 0f

    fun orderedHandCounts(pieces: List<PieceType>): List<Pair<PieceType, Int>> {
        val counts = pieces.map(PieceType::unpromoted).groupingBy { it }.eachCount()
        return handPieceOrder.mapNotNull { type -> counts[type]?.let { type to it } }
    }

    fun squareDescription(square: Square): String = "盤 ${square.japanese}"
}
