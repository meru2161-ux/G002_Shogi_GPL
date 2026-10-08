package com.melapplyworks.g002shogi.coaching

import com.melapplyworks.g002shogi.analysis.ShogiStrategy
import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.ShogiRules

private fun sameMoveAction(first: Move, second: Move): Boolean =
    first.from == second.from && first.to == second.to && first.piece == second.piece && first.player == second.player && first.promote == second.promote

/** In-memory study record. Judgement is always tied to the exact analysed position. */
data class UserMoveReview(
    val sourcePositionId: String,
    val sourcePosition: ShogiPosition,
    val userMove: Move,
    val resultingPosition: ShogiPosition,
    val analysis: AnalysisResult,
    val candidatesAtSource: List<CandidateMove>
) {
    val matchingCandidate: CandidateMove? = candidatesAtSource.firstOrNull { sameMoveAction(it.move, userMove) }
    val isCandidateMatch: Boolean get() = matchingCandidate != null
}

data class MoveComparison(
    val sourcePositionId: String,
    val userMove: Move,
    val candidate: CandidateMove?,
    val candidateIndex: Int,
    val sameMove: Boolean,
    val userIntent: String,
    val candidateIntent: String,
    val difference: String,
    val caution: String,
    val opponentResponse: String,
    val continuation: String,
    val teacherMessage: String
)

class StudyMoveHistory {
    private val mutableReviews = mutableListOf<UserMoveReview>()
    val reviews: List<UserMoveReview> get() = mutableReviews.toList()
    fun add(review: UserMoveReview) { mutableReviews += review }
}

object PlayerMoveCoach {
    fun record(
        sourcePositionId: String,
        sourcePosition: ShogiPosition,
        userMove: Move,
        resultingPosition: ShogiPosition,
        analysis: AnalysisResult
    ): UserMoveReview {
        // Candidates are valid only for exactly the position that was analysed.
        val candidates = if (analysis.positionId == sourcePositionId) analysis.candidates else emptyList()
        return UserMoveReview(sourcePositionId, sourcePosition, userMove, resultingPosition, analysis, candidates)
    }

    fun compare(review: UserMoveReview, candidateIndex: Int): MoveComparison {
        val candidate = review.candidatesAtSource.getOrNull(candidateIndex)
        val same = candidate?.let { sameMoveAction(it.move, review.userMove) } == true
        val userText = moveText(review.userMove)
        if (candidate == null) {
            return MoveComparison(
                review.sourcePositionId, review.userMove, null, candidateIndex, false,
                "あなたの手 $userText には、この局面で駒を前進させる狙いがあります。",
                "この局面に対応する候補はまだありません。",
                "別局面の候補を流用せず、この局面をあらためて解析します。",
                "局面が変わったため、古い評価で手の良し悪しを断定しません。",
                "相手の応手は、この局面の解析結果で確認します。",
                "次は盤上の駒の働きと相手の狙いを確認します。",
                "この手を研究対象として保存しました。"
            )
        }
        val rank = candidate.rankForFuture ?: candidateIndex + 1
        val actualIntent = describeUserMove(review)
        val message = if (same) {
            "この手は先生候補$rank と一致しています。理由と読み筋を一緒に確認しましょう。"
        } else {
            "あなたの手にも狙いがあります。先生候補$rank との違いを盤上で比べてみましょう。"
        }
        return MoveComparison(
            review.sourcePositionId, review.userMove, candidate, candidateIndex, same,
            "あなたの手 $userText は、$actualIntent。",
            candidate.explanation.intent,
            if (same) "あなたの手と先生候補$rank は同じ指し手です。" else candidate.comparisonSummary,
            userMoveCaution(review) ?: candidate.explanation.caution,
            candidate.explanation.opponentResponse,
            candidate.explanation.continuation,
            message
        )
    }

    fun moveText(move: Move): String = buildString {
        append(if (move.player == Player.SENTE) "▲" else "△")
        append(move.to.japanese)
        append(move.piece.label)
        if (move.isDrop) append("打")
        if (move.promote) append("成")
    }

    private fun describeUserMove(review: UserMoveReview): String {
        val move = review.userMove
        val captured = review.sourcePosition.board[move.to]
        val givesCheck = ShogiRules.isInCheck(review.resultingPosition, move.player.opponent())
        return when {
            captured != null && givesCheck -> "${captured.type.unpromoted.label}を取りながら王手をかけ、相手の応手を限定する手です"
            givesCheck -> "王手をかけ、相手の応手を限定する手です"
            captured != null -> "${captured.type.unpromoted.label}を取り、持ち駒と駒得を増やす手です"
            move.promote -> "${move.piece.unpromoted.label}を成って働きを強める手です"
            move.isDrop -> "持ち駒の${move.piece.unpromoted.label}を${move.to.japanese}へ打ち、その地点を支配する手です"
            else -> ShogiStrategy.teachingPurpose(review.sourcePosition, move)?.let { "${it}手です" }
                ?: "${move.piece.unpromoted.label}を${move.to.japanese}へ進め、次の働きを作る手です"
        }
    }

    private fun userMoveCaution(review: UserMoveReview): String? {
        val movedPiece = review.resultingPosition.board[review.userMove.to] ?: return null
        if (movedPiece.owner != review.userMove.player) return null
        val recaptures = ShogiRules.legalMoves(review.resultingPosition).filter { reply ->
            reply.player == review.userMove.player.opponent() && reply.to == review.userMove.to
        }
        return if (recaptures.isNotEmpty()) {
            val reply = recaptures.first()
            "この駒は相手の${reply.piece.unpromoted.label}で取り返される可能性があります。駒の交換が得か確認しましょう。"
        } else null
    }
}
