package com.melapplyworks.g002shogi.analysis

import com.melapplyworks.g002shogi.analysis.poc.UsiAnalysisSnapshot
import com.melapplyworks.g002shogi.analysis.poc.UsiInfo
import com.melapplyworks.g002shogi.analysis.poc.UsiScore
import com.melapplyworks.g002shogi.model.AnalysisResult
import com.melapplyworks.g002shogi.model.CandidateMove
import com.melapplyworks.g002shogi.model.Explanation
import com.melapplyworks.g002shogi.model.Move
import com.melapplyworks.g002shogi.model.ShogiMoveText
import com.melapplyworks.g002shogi.model.ShogiPosition
import com.melapplyworks.g002shogi.model.VariationLine
import com.melapplyworks.g002shogi.rules.SfenCodec
import com.melapplyworks.g002shogi.rules.ShogiRules

/** License-neutral request boundary between G002 and a separately selected USI runtime. */
data class UsiEngineQuery(
    val sfen: String,
    val multiPv: Int,
    val nodeLimit: Int,
    val timeLimitMillis: Long,
    val isCancelled: () -> Boolean
)

/**
 * Implementations own process lifecycle, USI handshakes and stop handling.
 * No engine binary, model or license choice is implied by this interface.
 */
fun interface UsiEngineBackend : AutoCloseable {
    fun analyze(query: UsiEngineQuery): UsiAnalysisSnapshot

    override fun close() = Unit
}

/** Converts a real USI MultiPV result into the same models used by both G002 modes. */
class UsiShogiAnalysisEngine(private val backend: UsiEngineBackend) : ShogiAnalysisEngine {
    override fun analyze(position: ShogiPosition, request: AnalysisRequest): AnalysisResult {
        if (request.isCancelled()) return emptyResult(position, request)
        val snapshot = runCatching {
            backend.analyze(
                UsiEngineQuery(
                    sfen = SfenCodec.format(position),
                    multiPv = MAX_CANDIDATES,
                    nodeLimit = request.nodeLimit,
                    timeLimitMillis = request.timeLimitMillis,
                    isCancelled = request.isCancelled
                )
            )
        }.getOrNull() ?: return emptyResult(position, request)
        if (request.isCancelled()) return emptyResult(position, request)

        val candidates = snapshot.infos
            .sortedBy { it.multiPv }
            .mapNotNull { toCandidate(position, request.positionId, it) }
            .distinctBy { it.move.actionKey() }
            .take(MAX_CANDIDATES)
        return AnalysisResult(
            position = position,
            candidates = candidates,
            isSample = false,
            positionId = request.positionId,
            requestId = request.requestId
        )
    }

    override fun assessMove(position: ShogiPosition, move: Move, request: AnalysisRequest): MoveAssessment? {
        if (!ShogiRules.isLegal(position, move) || request.isCancelled()) return null
        val after = ShogiRules.applyKnownLegal(position, move)
        val snapshot = runCatching {
            backend.analyze(
                UsiEngineQuery(
                    sfen = SfenCodec.format(after),
                    multiPv = 1,
                    nodeLimit = request.nodeLimit,
                    timeLimitMillis = request.timeLimitMillis,
                    isCancelled = request.isCancelled
                )
            )
        }.getOrNull() ?: return null
        if (request.isCancelled()) return null
        val info = snapshot.infos.minByOrNull { it.multiPv } ?: return null
        val reply = SfenCodec.parseUsiVariation(after, info.pv).map(::withNotation)
        val scoreForOpponent = scoreToInt(info.score) ?: return null
        return MoveAssessment(
            scoreForMover = -scoreForOpponent,
            principalVariation = listOf(withNotation(move)) + reply
        )
    }

    override fun close() = backend.close()

    private fun toCandidate(position: ShogiPosition, positionId: String, info: UsiInfo): CandidateMove? {
        val pv = SfenCodec.parseUsiVariation(position, info.pv).map(::withNotation)
        val move = pv.firstOrNull() ?: return null
        val after = ShogiRules.applyKnownLegal(position, move)
        val captured = position.board[move.to]
        val givesCheck = ShogiRules.isInCheck(after, after.activePlayer)
        val purpose = when {
            givesCheck -> "王に迫り、相手の応手を限定する"
            captured != null -> "${captured.type.unpromoted.label}を取り、持ち駒を増やす"
            move.promote -> "成って駒の働きを強める"
            move.isDrop -> "持ち駒を使って要所を押さえる"
            else -> ShogiStrategy.teachingPurpose(position, move)
                ?: "${move.piece.label}の働きを高め、次の狙いを作る"
        }
        val score = scoreToInt(info.score)
        val assessment = when {
            score == null -> "評価値は未確定ですが、読み筋に含まれる合法な候補です。"
            score >= 180 -> "局面を少し有利にしやすい候補です。"
            score >= -80 -> "大きな無理のない、自然な候補です。"
            else -> "相手の反撃もあるため、読み筋をよく確認したい候補です。"
        }
        val opponent = pv.getOrNull(1)?.let { "有力な応手の一例は ${it.notation} です。" }
            ?: "相手の応手候補はまだ読み筋に現れていません。"
        val continuation = pv.getOrNull(2)?.let { "続きの目安は ${it.notation} です。" }
            ?: "次は相手の応手を確認して方針を調整しましょう。"
        val strategicProfile = ShogiStrategy.profile(after, move.player)
        return CandidateMove(
            id = "$positionId-usi-${info.multiPv}-${move.from}-${move.to}-${move.promote}",
            move = move,
            purpose = purpose,
            explanation = Explanation(
                title = "候補${info.multiPv}：${move.notation}",
                body = "$assessment\n\n【現在の方針】${strategicProfile.summary}\n$purpose",
                nextStep = continuation,
                meaning = "$assessment ${move.notation}は、$purpose。",
                intent = ShogiStrategy.teachingIntent(position, move)
                    ?: "この手から、相手より先に駒を働かせる形を目指します。",
                opponentResponse = opponent,
                continuation = continuation,
                caution = if (givesCheck) {
                    "王手後は、相手の逃げ道や反撃の利きを必ず確認しましょう。"
                } else {
                    ShogiStrategy.teachingCaution(position, move)
                        ?: "自分の玉まわりが薄くならないか、相手の次の一手も確認しましょう。"
                }
            ),
            evaluationForFuture = score?.div(100.0),
            variation = VariationLine(
                moves = pv,
                explanations = pv.mapIndexed { index, pvMove ->
                    if (index == 0) "まず ${pvMove.notation} を考えます。" else "読み筋の${index + 1}手目：${pvMove.notation}"
                }
            ),
            rankForFuture = info.multiPv,
            positionId = positionId,
            comparisonSummary = "候補${info.multiPv}は、${strategicProfile.summary}を踏まえて、$purpose 方針です。"
        )
    }

    private fun emptyResult(position: ShogiPosition, request: AnalysisRequest) = AnalysisResult(
        position = position,
        candidates = emptyList(),
        isSample = false,
        positionId = request.positionId,
        requestId = request.requestId
    )

    private fun scoreToInt(score: UsiScore?): Int? = when (score) {
        is UsiScore.Centipawn -> score.value
        is UsiScore.Mate -> if (score.moves > 0) MATE_SCORE - score.moves else -MATE_SCORE + -score.moves
        null -> null
    }

    private fun withNotation(move: Move): Move = move.copy(
        notation = move.notation.ifBlank { ShogiMoveText.display(move) }
    )

    private fun Move.actionKey() = listOf(from, to, piece, player, promote)

    private companion object {
        const val MAX_CANDIDATES = 3
        const val MATE_SCORE = 100_000
    }
}

/** Keeps the existing local engine available when a selected USI runtime cannot return analysis. */
class FallbackShogiAnalysisEngine(
    private val primary: ShogiAnalysisEngine,
    private val fallback: ShogiAnalysisEngine
) : ShogiAnalysisEngine {
    override fun analyze(position: ShogiPosition, request: AnalysisRequest): AnalysisResult {
        if (request.isCancelled()) {
            return AnalysisResult(position, emptyList(), false, request.positionId, request.requestId)
        }
        val primaryResult = runCatching { primary.analyze(position, request) }.getOrNull()
        if (primaryResult != null && (primaryResult.candidates.isNotEmpty() || ShogiRules.legalMoves(position).isEmpty())) {
            return primaryResult
        }
        return fallback.analyze(position, request)
    }

    override fun assessMove(position: ShogiPosition, move: Move, request: AnalysisRequest): MoveAssessment? {
        if (request.isCancelled()) return null
        return runCatching { primary.assessMove(position, move, request) }.getOrNull()
            ?: fallback.assessMove(position, move, request)
    }

    override fun close() {
        runCatching { primary.close() }
        runCatching { fallback.close() }
    }
}
