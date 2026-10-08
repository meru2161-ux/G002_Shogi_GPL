package com.melapplyworks.g002shogi.model

enum class Player { SENTE, GOTE;
    fun opponent() = if (this == SENTE) GOTE else SENTE
}

enum class PieceType(val label: String) {
    KING("王"), ROOK("飛"), BISHOP("角"), GOLD("金"), SILVER("銀"), KNIGHT("桂"), LANCE("香"), PAWN("歩"),
    PROMOTED_ROOK("龍"), PROMOTED_BISHOP("馬"), PROMOTED_SILVER("全"), PROMOTED_KNIGHT("圭"), PROMOTED_LANCE("杏"), PROMOTED_PAWN("と");

    val canPromote get() = this in setOf(ROOK, BISHOP, SILVER, KNIGHT, LANCE, PAWN)
    val unpromoted get() = when (this) {
        PROMOTED_ROOK -> ROOK; PROMOTED_BISHOP -> BISHOP; PROMOTED_SILVER -> SILVER
        PROMOTED_KNIGHT -> KNIGHT; PROMOTED_LANCE -> LANCE; PROMOTED_PAWN -> PAWN
        else -> this
    }
    val promoted get() = when (this) {
        ROOK -> PROMOTED_ROOK; BISHOP -> PROMOTED_BISHOP; SILVER -> PROMOTED_SILVER
        KNIGHT -> PROMOTED_KNIGHT; LANCE -> PROMOTED_LANCE; PAWN -> PROMOTED_PAWN
        else -> this
    }
}

data class Square(val file: Int, val rank: Int) {
    init { require(file in 1..9 && rank in 1..9) }
    val japanese: String get() = "${fullWidth(file)}${kanjiRank(rank)}"
    private fun fullWidth(number: Int) = "１２３４５６７８９"[number - 1]
    private fun kanjiRank(number: Int) = "一二三四五六七八九"[number - 1]
}

data class Piece(val type: PieceType, val owner: Player)
data class Move(val from: Square?, val to: Square, val piece: PieceType, val player: Player, val notation: String = "", val promote: Boolean = false) {
    val isDrop get() = from == null
}
data class ShogiPosition(
    val board: Map<Square, Piece>,
    val hands: Map<Player, List<PieceType>> = emptyMap(),
    val activePlayer: Player = Player.SENTE
) {
    fun apply(move: Move): ShogiPosition = copy(board = board.toMutableMap().apply {
        move.from?.let(::remove)
        put(move.to, Piece(move.piece, move.player))
    })
}

data class Explanation(
    val title: String,
    val body: String,
    val nextStep: String,
    val meaning: String = body,
    val intent: String = nextStep,
    val opponentResponse: String = "サンプル解析では、相手の有力応手を読み筋で確認します。",
    val continuation: String = nextStep,
    val caution: String = "この手だけで方針を固定せず、相手の応手に合わせて考えます。"
)
data class VariationLine(val moves: List<Move>, val explanations: List<String>)
data class CandidateMove(
    val id: String,
    val move: Move,
    val purpose: String,
    val explanation: Explanation,
    val evaluationForFuture: Double? = null,
    val variation: VariationLine,
    val rankForFuture: Int? = null,
    val positionId: String = "initial",
    val comparisonSummary: String = "評価値ではなく、狙いと指しやすさを比べるサンプルです。"
)
data class AnalysisResult(
    val position: ShogiPosition,
    val candidates: List<CandidateMove>,
    val isSample: Boolean,
    val positionId: String = "initial",
    val requestId: String = "",
    /** True only when the local search actually reached its requested resource limit. */
    val reachedSearchLimit: Boolean = false
)
